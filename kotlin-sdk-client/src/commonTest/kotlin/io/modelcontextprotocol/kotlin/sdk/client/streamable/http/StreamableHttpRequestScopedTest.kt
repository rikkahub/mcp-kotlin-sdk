package io.modelcontextprotocol.kotlin.sdk.client.streamable.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CacheScope
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.http.content.TextContent as HttpTextContent

@OptIn(ExperimentalMcpApi::class)
class StreamableHttpRequestScopedTest {

    private class RecordedRequest(val method: HttpMethod, val headers: Headers, val message: JSONRPCMessage?)

    private val recorded = mutableListOf<RecordedRequest>()
    private val mutex = Mutex()

    private suspend fun recordedPosts(rpcMethod: String): List<RecordedRequest> = mutex.withLock {
        recorded.filter { (it.message as? JSONRPCRequest)?.method == rpcMethod }
    }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData, JSONRPCMessage?) -> HttpResponseData) =
        StreamableHttpClientTransport(
            HttpClient(
                MockEngine { request ->
                    val message = (request.body as? HttpTextContent)?.text?.let {
                        McpJson.decodeFromString<JSONRPCMessage>(it)
                    }
                    mutex.withLock { recorded += RecordedRequest(request.method, request.headers, message) }
                    handler(request, message)
                },
            ) { install(SSE) },
            url = "http://localhost/mcp",
        )

    private fun MockRequestHandleScope.json(message: JSONRPCMessage, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = McpJson.encodeToString(message),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.result(request: JSONRPCMessage?, result: RequestResult) =
        json(JSONRPCResponse((request as JSONRPCRequest).id, result))

    private val discoverResult = DiscoverResult(
        supportedVersions = listOf(LATEST_MODERN_PROTOCOL_VERSION),
        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools()),
        ttlMs = 0,
        cacheScope = CacheScope.Public,
    )

    private fun tool(name: String, properties: JsonObject) =
        Tool(name = name, inputSchema = ToolSchema(properties = properties))

    private val regionTool = tool(
        "execute_sql",
        buildJsonObject {
            putJsonObject("region") {
                put("type", "string")
                put("x-mcp-header", "Region")
            }
            putJsonObject("query") { put("type", "string") }
        },
    )

    private val invalidTool = tool(
        "bad_header",
        buildJsonObject {
            putJsonObject("ratio") {
                put("type", "number")
                put("x-mcp-header", "Ratio")
            }
        },
    )

    @Test
    fun `should send request-scoped headers and mirror annotated tool arguments`() = runTest {
        withContext(Dispatchers.Default) {
            val transport = client { _, message ->
                when ((message as JSONRPCRequest).method) {
                    "server/discover" -> result(message, discoverResult)
                    "tools/list" -> result(message, ListToolsResult(tools = listOf(regionTool, invalidTool)))
                    else -> result(message, CallToolResult(content = listOf(TextContent("ok"))))
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            val tools = client.listTools().tools
            client.callTool("execute_sql", mapOf("region" to "Hello, 世界", "query" to "SELECT 1"))

            client.protocolVersion shouldBe LATEST_MODERN_PROTOCOL_VERSION
            tools.map { it.name } shouldContainExactly listOf("execute_sql")
            val discover = recordedPosts("server/discover").single().headers
            discover["MCP-Protocol-Version"] shouldBe LATEST_MODERN_PROTOCOL_VERSION
            discover["Mcp-Method"] shouldBe "server/discover"
            discover["mcp-session-id"] shouldBe null
            val call = recordedPosts("tools/call").single().headers
            call["MCP-Protocol-Version"] shouldBe LATEST_MODERN_PROTOCOL_VERSION
            call["Mcp-Name"] shouldBe "execute_sql"
            call["Mcp-Param-Region"] shouldBe "=?base64?SGVsbG8sIOS4lueVjA==?="
            mutex.withLock { recorded.filter { it.method == HttpMethod.Get } }.shouldBeEmpty()
            client.close()
        }
    }

    @Test
    fun `should refresh tools and retry once when the server reports a header mismatch`() = runTest {
        withContext(Dispatchers.Default) {
            var calls = 0
            val transport = client { _, message ->
                when ((message as JSONRPCRequest).method) {
                    "server/discover" -> result(message, discoverResult)

                    "tools/list" -> result(message, ListToolsResult(tools = listOf(regionTool)))

                    else -> if (calls++ == 0) {
                        json(
                            JSONRPCError(message.id, RPCError(RPCError.ErrorCode.HEADER_MISMATCH, "Header mismatch")),
                            HttpStatusCode.BadRequest,
                        )
                    } else {
                        result(message, CallToolResult(content = listOf(TextContent("ok"))))
                    }
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            client.callTool("execute_sql", mapOf("region" to "us-west1"))

            val (first, retry) = recordedPosts("tools/call")
            first.headers["Mcp-Param-Region"] shouldBe null
            retry.headers["Mcp-Param-Region"] shouldBe "us-west1"
            recordedPosts("tools/list").size shouldBe 1
            client.close()
        }
    }

    @Test
    fun `should surface a JSON-RPC error body of a rejected request as an McpException`() = runTest {
        withContext(Dispatchers.Default) {
            val transport = client { _, message ->
                when ((message as JSONRPCRequest).method) {
                    "server/discover" -> result(message, discoverResult)

                    else -> json(
                        JSONRPCError(message.id, RPCError(RPCError.ErrorCode.METHOD_NOT_FOUND, "Unknown method")),
                        HttpStatusCode.NotFound,
                    )
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            val error = shouldThrow<McpException> { client.callTool("missing", emptyMap()) }

            error.code shouldBe RPCError.ErrorCode.METHOD_NOT_FOUND
            client.close()
        }
    }

    @Test
    fun `should fail a request whose response stream closes before the response`() = runTest {
        withContext(Dispatchers.Default) {
            val transport = client { _, message ->
                when ((message as JSONRPCRequest).method) {
                    "server/discover" -> result(message, discoverResult)

                    else -> respond(
                        content = ": keep-alive\n\n",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                    )
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            val error = shouldThrow<McpException> { client.callTool("tool", emptyMap()) }

            (error.message ?: "") shouldBe "Error while sending message: " +
                "Streamable HTTP error: Response stream for request ${
                    (recordedPosts("tools/call").single().message as JSONRPCRequest).id
                } closed before the response arrived"
            client.close()
        }
    }

    @Test
    fun `should close the response stream instead of posting a cancellation`() = runTest {
        withContext(Dispatchers.Default) {
            val transport = client { _, message ->
                when ((message as? JSONRPCRequest)?.method) {
                    "server/discover" -> result(message, discoverResult)

                    "tools/call" -> {
                        delay(10.seconds)
                        result(message, CallToolResult(content = emptyList()))
                    }

                    else -> respond("", HttpStatusCode.Accepted)
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            shouldThrow<McpException> {
                client.callTool("slow", emptyMap(), options = RequestOptions(timeout = 200.milliseconds))
            }

            mutex.withLock { recorded.mapNotNull { (it.message as? JSONRPCRequest)?.method } } shouldContainExactly
                listOf("server/discover", "tools/call")
            mutex.withLock { recorded.filter { it.message !is JSONRPCRequest } }.shouldBeEmpty()
            client.close()
        }
    }

    @Test
    fun `should fall back to initialize when the server rejects discover without a JSON-RPC error`() = runTest {
        withContext(Dispatchers.Default) {
            val transport = client { request, message ->
                when {
                    request.method == HttpMethod.Get -> respond("", HttpStatusCode.MethodNotAllowed)

                    (message as? JSONRPCRequest)?.method == "server/discover" ->
                        respond("Bad Request: Server not initialized", HttpStatusCode.BadRequest)

                    (message as? JSONRPCRequest)?.method == "initialize" -> result(
                        message,
                        InitializeResult(
                            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools()),
                            serverInfo = Implementation("legacy", "1.0.0"),
                        ),
                    )

                    message is JSONRPCRequest -> result(message, ListToolsResult(tools = listOf(invalidTool)))

                    else -> respond("", HttpStatusCode.Accepted)
                }
            }
            val client = Client(Implementation("test-client", "1.0.0"))
            client.connect(transport)

            val tools = client.listTools().tools

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            transport.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            // Handshake-based servers are not subject to x-mcp-header validation.
            tools.map { it.name } shouldContainExactly listOf("bad_header")
            recordedPosts("tools/list").single().headers["mcp-protocol-version"] shouldBe LATEST_PROTOCOL_VERSION
            client.close()
        }
    }
}
