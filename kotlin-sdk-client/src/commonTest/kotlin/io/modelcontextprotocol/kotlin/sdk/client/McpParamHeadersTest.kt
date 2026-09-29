package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class McpParamHeadersTest {

    private fun tool(schema: String): Tool {
        val json = McpJson.parseToJsonElement(schema).jsonObject
        return Tool(name = "tool", inputSchema = McpJson.decodeFromJsonElement(ToolSchema.serializer(), json))
    }

    private fun headersOf(schema: String) = toolParamHeaders(tool(schema)).map { it.headerName to it.path }

    private fun arguments(json: String): JsonObject = McpJson.parseToJsonElement(json).jsonObject

    @Test
    fun `should extract top-level and nested annotated parameters`() {
        val headers = headersOf(
            """
            {"type":"object","properties":{
              "region":{"type":"string","x-mcp-header":"Region"},
              "options":{"type":"object","properties":{
                "dryRun":{"type":"boolean","x-mcp-header":"Dry-Run"},
                "limit":{"type":"integer","x-mcp-header":"Limit"}
              }},
              "query":{"type":"string"}
            }}
            """.trimIndent(),
        )

        headers shouldContainExactly listOf(
            "Mcp-Param-Region" to listOf("region"),
            "Mcp-Param-Dry-Run" to listOf("options", "dryRun"),
            "Mcp-Param-Limit" to listOf("options", "limit"),
        )
    }

    @Test
    fun `should accept schemas without annotations`() {
        headersOf("""{"type":"object","properties":{"q":{"type":"string"}}}""").shouldBeEmpty()
    }

    @Test
    fun `should accept a property literally named x-mcp-header`() {
        headersOf("""{"type":"object","properties":{"x-mcp-header":{"type":"string"}}}""").shouldBeEmpty()
    }

    @Test
    fun `should reject number parameters`() {
        shouldThrow<IllegalArgumentException> {
            headersOf("""{"type":"object","properties":{"r":{"type":"number","x-mcp-header":"Ratio"}}}""")
        }.message shouldContain "string, integer, or boolean"
    }

    @Test
    fun `should reject values that are not HTTP tokens`() {
        listOf("\"\"", "\"Has Space\"", "\"Line\\nBreak\"", "\"Ünicode\"", "42").forEach { value ->
            shouldThrow<IllegalArgumentException> {
                headersOf("""{"type":"object","properties":{"p":{"type":"string","x-mcp-header":$value}}}""")
            }
        }
    }

    @Test
    fun `should reject case-insensitive duplicates`() {
        shouldThrow<IllegalArgumentException> {
            headersOf(
                """
                {"type":"object","properties":{
                  "a":{"type":"string","x-mcp-header":"Region"},
                  "b":{"type":"string","x-mcp-header":"region"}
                }}
                """.trimIndent(),
            )
        }.message shouldContain "unique"
    }

    @Test
    fun `should reject annotations that are not statically reachable`() {
        listOf(
            """{"type":"object","properties":{"l":{"type":"array","items":{"type":"string","x-mcp-header":"Item"}}}}""",
            """{"type":"object","properties":{"u":{"anyOf":[{"type":"string","x-mcp-header":"U"}]}}}""",
            """{"type":"object","properties":{"r":{"${'$'}ref":"#/${'$'}defs/r"}},
               "${'$'}defs":{"r":{"type":"string","x-mcp-header":"R"}}}""",
        ).forEach { schema ->
            shouldThrow<IllegalArgumentException> { headersOf(schema) }.message shouldContain "reachable"
        }
    }

    @Test
    fun `should convert and encode argument values`() {
        val headers = toolParamHeaders(
            tool(
                """
                {"type":"object","properties":{
                  "s":{"type":"string","x-mcp-header":"S"},
                  "i":{"type":"integer","x-mcp-header":"I"},
                  "b":{"type":"boolean","x-mcp-header":"B"},
                  "n":{"type":"string","x-mcp-header":"N"},
                  "missing":{"type":"string","x-mcp-header":"Missing"},
                  "o":{"type":"object","properties":{"deep":{"type":"string","x-mcp-header":"Deep"}}}
                }}
                """.trimIndent(),
            ),
        )

        val values = paramHeaderValues(
            headers,
            arguments("""{"s":" padded ","i":42.0,"b":false,"n":null,"o":{"deep":"=?base64?literal?="}}"""),
        )

        values shouldContainExactly listOf(
            "Mcp-Param-S" to "=?base64?IHBhZGRlZCA=?=",
            "Mcp-Param-I" to "42",
            "Mcp-Param-B" to "false",
            "Mcp-Param-Deep" to "=?base64?PT9iYXNlNjQ/bGl0ZXJhbD89?=",
        )
    }

    @Test
    fun `should keep plain ASCII header values as they are`() {
        "us-west1".encodeMcpHeaderValue() shouldBe "us-west1"
        "line1\nline2".encodeMcpHeaderValue() shouldBe "=?base64?bGluZTEKbGluZTI=?="
    }
}
