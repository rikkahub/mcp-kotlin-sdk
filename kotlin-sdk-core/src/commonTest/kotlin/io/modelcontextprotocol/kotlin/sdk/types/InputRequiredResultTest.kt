package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalMcpApi::class)
class InputRequiredResultTest {

    @Test
    fun `should decode an input required result with typed input requests`() {
        val result = McpJson.decodeFromString<ServerResult>(
            """
            {
              "resultType": "input_required",
              "inputRequests": {
                "github_login": {
                  "method": "elicitation/create",
                  "params": {
                    "mode": "form",
                    "message": "Please provide your GitHub username",
                    "requestedSchema": {"type": "object", "properties": {"name": {"type": "string"}}}
                  }
                },
                "capital": {
                  "method": "sampling/createMessage",
                  "params": {
                    "messages": [{"role": "user", "content": {"type": "text", "text": "Capital of France?"}}],
                    "maxTokens": 100
                  }
                },
                "roots": {"method": "roots/list"}
              },
              "requestState": "opaque"
            }
            """.trimIndent(),
        )

        val inputRequired = assertIs<InputRequiredResult>(result)
        assertEquals("opaque", inputRequired.requestState)
        val requests = inputRequired.inputRequests!!
        assertEquals("Please provide your GitHub username", assertIs<ElicitRequest>(requests["github_login"]).message)
        assertEquals(100, assertIs<CreateMessageRequest>(requests["capital"]).params.maxTokens)
        assertIs<ListRootsRequest>(requests["roots"])
    }

    @Test
    fun `should decode an input required result through the generic result serializer`() {
        val result = McpJson.decodeFromString<RequestResult>("""{"resultType":"input_required","requestState":"s"}""")

        val inputRequired = assertIs<InputRequiredResult>(result)
        assertNull(inputRequired.inputRequests)
    }

    @Test
    fun `should encode the result type discriminator`() {
        McpJson.encodeToString<ServerResult>(InputRequiredResult(requestState = "s")) shouldEqualJson
            """{"resultType":"input_required","requestState":"s"}"""
    }

    @Test
    fun `should match complete results by shape`() {
        val toolResult = McpJson.decodeFromString<ServerResult>(
            """{"resultType":"complete","content":[{"type":"text","text":"ok"}]}""",
        )
        assertIs<CallToolResult>(toolResult)

        val empty = McpJson.decodeFromString<ServerResult>(
            """{"resultType":"complete","_meta":{"io.modelcontextprotocol/subscriptionId":1}}""",
        )
        assertIs<EmptyResult>(empty)
        assertIs<EmptyResult>(McpJson.decodeFromString<RequestResult>("""{"resultType":"complete"}"""))
    }

    @Test
    fun `should reject an unrecognized result type`() {
        assertFailsWith<SerializationException> {
            McpJson.decodeFromString<ServerResult>("""{"resultType":"task","content":[]}""")
        }
    }

    @Test
    fun `should decode a response carrying an input required result`() {
        val message = McpJson.decodeFromString<JSONRPCMessage>(
            """{"jsonrpc":"2.0","id":1,"result":{"resultType":"input_required","requestState":"s"}}""",
        )

        assertIs<InputRequiredResult>(assertIs<JSONRPCResponse>(message).result)
    }
}
