package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.ServerResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ClientMultiRoundTripTest {

    private val allCapabilities = ClientCapabilities(
        roots = ClientCapabilities.Roots(),
        sampling = ClientCapabilities.Sampling(),
        elicitation = ClientCapabilities.Elicitation(),
    )

    private fun inputRequired(json: String): ServerResult = McpJson.decodeFromString<ServerResult>(json)

    private val elicitationSamplingAndRoots = """
        {
          "resultType": "input_required",
          "inputRequests": {
            "github_login": {
              "method": "elicitation/create",
              "params": {
                "mode": "form",
                "message": "Please provide your GitHub username",
                "requestedSchema": {
                  "type": "object",
                  "properties": { "name": { "type": "string" } },
                  "required": ["name"]
                }
              }
            },
            "capital_of_france": {
              "method": "sampling/createMessage",
              "params": {
                "messages": [
                  { "role": "user", "content": { "type": "text", "text": "What is the capital of France?" } }
                ],
                "maxTokens": 100
              }
            },
            "workspace": { "method": "roots/list" }
          },
          "requestState": "opaque-state"
        }
    """.trimIndent()

    private val finalToolResult = CallToolResult(content = listOf(TextContent("done")))

    /** Answers the first `tools/call` with [firstResult] and every retry with [finalToolResult]. */
    private fun server(firstResult: String, rounds: Int = 1) = FakeMcpServer { request ->
        when (request.method) {
            "server/discover" -> respondDiscover(request)

            "tools/call", "prompts/get" -> {
                val attempt = requests(request.method).size
                if (attempt <= rounds) {
                    respond(request, inputRequired(firstResult))
                } else if (request.method == "tools/call") {
                    respond(request, finalToolResult)
                } else {
                    respond(request, GetPromptResult(messages = emptyList(), description = "prompt"))
                }
            }

            else -> respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND)
        }
    }

    private suspend fun connectedClient(server: FakeMcpServer, capabilities: ClientCapabilities = allCapabilities) =
        Client(Implementation("test-client", "1.0.0"), ClientOptions(capabilities = capabilities)).apply {
            connect(server.clientTransport)
        }

    private fun json(text: String) = McpJson.parseToJsonElement(text)

    @Test
    fun `should fulfil input requests with registered handlers and retry the tool call`() = runTest {
        withContext(Dispatchers.Default) {
            val server = server(elicitationSamplingAndRoots).apply { start() }
            val client = connectedClient(server)
            client.addRoot("file:///workspace", "Workspace")
            client.setElicitationHandler { request ->
                request.message shouldBe "Please provide your GitHub username"
                ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("name", "octocat") })
            }
            client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { _, _ ->
                CreateMessageResult(role = Role.Assistant, content = listOf(TextContent("Paris")), model = "test-model")
            }

            val result = client.callTool("lookup", mapOf("query" to "x"))

            result shouldBe finalToolResult
            val (first, retry) = server.requests("tools/call").also { it shouldHaveSize 2 }
            retry.id shouldNotBe first.id
            retry.paramsObject["name"] shouldBe JsonPrimitive("lookup")
            retry.paramsObject["arguments"] shouldBe first.paramsObject["arguments"]
            retry.paramsObject["requestState"] shouldBe JsonPrimitive("opaque-state")
            first.paramsObject.containsKey("inputResponses") shouldBe false

            val responses = retry.paramsObject.getValue("inputResponses").jsonObject
            responses.getValue("github_login") shouldBe
                json("""{"resultType":"complete","action":"accept","content":{"name":"octocat"}}""")
            responses.getValue("capital_of_france") shouldBe json(
                """
                {"resultType":"complete","role":"assistant","content":{"type":"text","text":"Paris"},"model":"test-model"}
                """.trimIndent(),
            )
            responses.getValue("workspace") shouldBe
                json("""{"resultType":"complete","roots":[{"uri":"file:///workspace","name":"Workspace"}]}""")
            client.close()
        }
    }

    @Test
    fun `should retry immediately when only request state is returned`() = runTest {
        withContext(Dispatchers.Default) {
            val server = server("""{"resultType":"input_required","requestState":"retry-later"}""").apply { start() }
            val client = connectedClient(server)

            val result = client.getPrompt(GetPromptRequest(GetPromptRequestParams(name = "greeting")))

            result.description shouldBe "prompt"
            val retry = server.requests("prompts/get").last()
            retry.paramsObject["requestState"] shouldBe JsonPrimitive("retry-later")
            retry.paramsObject.containsKey("inputResponses") shouldBe false
            client.close()
        }
    }

    @Test
    fun `should fail when the server requests input without a registered handler`() = runTest {
        withContext(Dispatchers.Default) {
            val server = server(elicitationSamplingAndRoots).apply { start() }
            val client = connectedClient(server, ClientCapabilities())

            val error = shouldThrow<IllegalStateException> { client.callTool("lookup", emptyMap()) }

            error.message shouldContain "no handler is registered"
            server.requests("tools/call") shouldHaveSize 1
            client.close()
        }
    }

    @Test
    fun `should give up when the server keeps requiring input`() = runTest {
        withContext(Dispatchers.Default) {
            val server = server("""{"resultType":"input_required","requestState":"again"}""", rounds = Int.MAX_VALUE)
                .apply { start() }
            val client = connectedClient(server)

            val error = shouldThrow<IllegalStateException> { client.callTool("lookup", emptyMap()) }

            error.message shouldContain "tools/call"
            server.requests("tools/call") shouldHaveSize 16
            client.close()
        }
    }

    @Test
    fun `should echo server input request keys as local request ids`() = runTest {
        withContext(Dispatchers.Default) {
            val server = server(
                """{"resultType":"input_required","inputRequests":{"key-1":{"method":"roots/list"}}}""",
            ).apply { start() }
            val client = connectedClient(server)
            val seenIds = mutableListOf<String>()
            client.setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, extra ->
                seenIds += extra.requestId.toString()
                ListRootsResult(listOf(Root("file:///x")))
            }

            client.callTool("lookup", emptyMap())

            seenIds.single() shouldContain "key-1"
            client.close()
        }
    }
}
