package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMcpApi::class)
class ClientProtocolNegotiationTest {

    private val clientInfo = Implementation(name = "test-client", version = "1.0.0")

    private fun client(configure: ClientOptions.() -> Unit = {}) = Client(
        clientInfo = clientInfo,
        options = ClientOptions(capabilities = ClientCapabilities(roots = ClientCapabilities.Roots())).apply(configure),
    )

    private fun modernServer() = FakeMcpServer { request ->
        when (request.method) {
            "server/discover" -> respondDiscover(request)
            "tools/list" -> respond(request, ListToolsResult(tools = emptyList()))
            else -> respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND, "Method not found")
        }
    }

    /** A handshake-based server that rejects unknown requests the way most legacy servers do. */
    private fun legacyServer(answerDiscover: Boolean = true) = FakeMcpServer { request ->
        when (request.method) {
            "initialize" -> respondInitialize(request)

            "tools/list" -> respond(request, ListToolsResult(tools = emptyList()))

            "logging/setLevel", "ping" -> respond(request, EmptyResult())

            "server/discover" -> if (answerDiscover) {
                respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND, "Method not found")
            }

            else -> respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND, "Method not found")
        }
    }

    @Test
    fun `should adopt request-scoped protocol when server answers discover`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()

            client.connect(server.clientTransport)

            client.protocolVersion shouldBe LATEST_MODERN_PROTOCOL_VERSION
            client.serverVersion shouldBe FakeMcpServer.serverInfo
            client.serverCapabilities shouldBe FakeMcpServer.defaultCapabilities
            client.serverInstructions shouldBe "Use the tools wisely."
            server.requests("initialize").shouldBeEmpty()
            server.notifications("notifications/initialized").shouldBeEmpty()
            client.close()
        }
    }

    @Test
    fun `should attach protocol version and client info and capabilities to every request`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()
            client.connect(server.clientTransport)

            client.listTools()

            val meta = server.requests("tools/list").single().meta
            meta["io.modelcontextprotocol/protocolVersion"] shouldBe JsonPrimitive(LATEST_MODERN_PROTOCOL_VERSION)
            meta["io.modelcontextprotocol/clientInfo"] shouldBe McpJson.encodeToJsonElement(clientInfo)
            meta["io.modelcontextprotocol/clientCapabilities"] shouldBe
                McpJson.encodeToJsonElement(ClientCapabilities(roots = ClientCapabilities.Roots()))
            meta["io.modelcontextprotocol/logLevel"] shouldBe null
            client.close()
        }
    }

    @Test
    fun `should send log level with each request instead of logging setLevel`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()
            client.connect(server.clientTransport)

            client.setLoggingLevel(LoggingLevel.Warning)
            client.listTools()

            server.requests("logging/setLevel").shouldBeEmpty()
            server.requests(
                "tools/list",
            ).single().meta["io.modelcontextprotocol/logLevel"]?.jsonPrimitive?.content shouldBe
                "warning"
            client.close()
        }
    }

    @Test
    fun `should ping a request-scoped server with discover`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()
            client.connect(server.clientTransport)

            client.ping()

            server.requests("ping").shouldBeEmpty()
            server.requests("server/discover") shouldHaveSize 2
            client.close()
        }
    }

    @Test
    fun `should reject resources subscribe with a request-scoped server`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()
            client.connect(server.clientTransport)

            val error = shouldThrow<IllegalStateException> {
                client.subscribeResource(SubscribeRequest(SubscribeRequestParams(uri = "file:///a")))
            }

            error.message shouldContain "listen"
            server.requests("resources/subscribe").shouldBeEmpty()
            client.close()
        }
    }

    @Test
    fun `should fall back to initialize when server rejects discover`() = runTest {
        withContext(Dispatchers.Default) {
            val server = legacyServer().apply { start() }
            val client = client()

            client.connect(server.clientTransport)
            client.setLoggingLevel(LoggingLevel.Info)
            client.ping()

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            client.serverVersion shouldBe FakeMcpServer.serverInfo
            server.requests("initialize") shouldHaveSize 1
            server.notifications("notifications/initialized") shouldHaveSize 1
            server.requests("logging/setLevel") shouldHaveSize 1
            server.requests("ping") shouldHaveSize 1
            // Handshake-based requests carry no request-scoped metadata.
            server.requests("initialize").single().meta.containsKey("io.modelcontextprotocol/protocolVersion") shouldBe
                false
            client.close()
        }
    }

    @Test
    fun `should fall back to initialize when discover is not answered in time`() = runTest {
        withContext(Dispatchers.Default) {
            val server = legacyServer(answerDiscover = false).apply { start() }
            val client = client { discoveryTimeout = 200.milliseconds }

            client.connect(server.clientTransport)

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            client.close()
        }
    }

    @Test
    fun `should not probe with the legacy era`() = runTest {
        withContext(Dispatchers.Default) {
            val server = legacyServer().apply { start() }
            val client = client { protocolEra = ProtocolEra.Legacy }

            client.connect(server.clientTransport)

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            server.requests("server/discover").shouldBeEmpty()
            client.close()
        }
    }

    @Test
    fun `should fail with the modern era against a handshake-based server`() = runTest {
        withContext(Dispatchers.Default) {
            val server = legacyServer().apply { start() }
            val client = client { protocolEra = ProtocolEra.Modern }

            shouldThrow<Exception> { client.connect(server.clientTransport) }

            server.requests("initialize").shouldBeEmpty()
        }
    }

    @Test
    fun `should fall back when unsupported version error only lists handshake-based versions`() = runTest {
        withContext(Dispatchers.Default) {
            val server = FakeMcpServer { request ->
                when (request.method) {
                    "server/discover" -> respondError(
                        request,
                        RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
                        "Unsupported protocol version",
                        McpJson.encodeToJsonElement(
                            UnsupportedProtocolVersionData(
                                supported = listOf(LATEST_PROTOCOL_VERSION),
                                requested = LATEST_MODERN_PROTOCOL_VERSION,
                            ),
                        ),
                    )

                    "initialize" -> respondInitialize(request)
                }
            }.apply { start() }
            val client = client()

            client.connect(server.clientTransport)

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            client.close()
        }
    }

    @Test
    fun `should fail when server supports no version of the client`() = runTest {
        withContext(Dispatchers.Default) {
            val server = FakeMcpServer { request ->
                when (request.method) {
                    "server/discover" -> respondError(
                        request,
                        RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
                        "Unsupported protocol version",
                        McpJson.encodeToJsonElement(
                            UnsupportedProtocolVersionData(
                                supported = listOf("2099-01-01"),
                                requested = LATEST_MODERN_PROTOCOL_VERSION,
                            ),
                        ),
                    )

                    "initialize" -> respondInitialize(request)
                }
            }.apply { start() }
            val client = client()

            val error = shouldThrow<IllegalStateException> { client.connect(server.clientTransport) }

            error.message shouldContain "2099-01-01"
            server.requests("initialize").shouldBeEmpty()
        }
    }

    @Test
    fun `should use a handshake-based version a dual-era server lists in discover`() = runTest {
        withContext(Dispatchers.Default) {
            val server = FakeMcpServer { request ->
                when (request.method) {
                    "server/discover" -> respondDiscover(request, supportedVersions = listOf(LATEST_PROTOCOL_VERSION))
                    "initialize" -> respondInitialize(request)
                }
            }.apply { start() }
            val client = client()

            client.connect(server.clientTransport)

            client.protocolVersion shouldBe LATEST_PROTOCOL_VERSION
            server.requests("initialize") shouldHaveSize 1
            client.close()
        }
    }

    @Test
    fun `should refresh server information with discover`() = runTest {
        withContext(Dispatchers.Default) {
            val server = modernServer().apply { start() }
            val client = client()
            client.connect(server.clientTransport)

            val result = client.discover()

            result.supportedVersions shouldBe listOf(LATEST_MODERN_PROTOCOL_VERSION)
            result.meta shouldNotBe null
            (result.meta as JsonObject).containsKey("io.modelcontextprotocol/serverInfo") shouldBe true
            client.close()
        }
    }
}
