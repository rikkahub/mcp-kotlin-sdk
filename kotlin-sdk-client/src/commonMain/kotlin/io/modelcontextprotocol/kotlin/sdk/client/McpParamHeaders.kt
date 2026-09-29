package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

/** Schema keyword that mirrors a tool parameter into an `Mcp-Param-{name}` HTTP header (SEP-2243). */
private const val X_MCP_HEADER = "x-mcp-header"

private const val MCP_PARAM_HEADER_PREFIX = "Mcp-Param-"
private const val MCP_BASE64_PREFIX = "=?base64?"
private const val MCP_BASE64_SUFFIX = "?="

/** Largest integer representable in a JavaScript number, the bound the spec places on integer header values. */
private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

private val headerPrimitiveTypes = setOf("string", "integer", "boolean")

/** RFC 9110 `tchar` punctuation; letters and digits are also allowed. */
private const val TCHAR_PUNCTUATION = "!#$%&'*+-.^_`|~"

/** JSON Schema keywords whose object value maps names to subschemas rather than being a schema itself. */
private val schemaNameMapKeywords =
    setOf("properties", "patternProperties", "\$defs", "definitions", "dependentSchemas")

/**
 * A tool parameter mirrored into an HTTP header.
 *
 * @property headerName the full header name, `Mcp-Param-{x-mcp-header value}`
 * @property path the chain of `properties` keys leading from the input schema root to the parameter
 */
internal class ToolParamHeader(val headerName: String, val path: List<String>)

/**
 * Extracts the `x-mcp-header` annotations of [tool]'s input schema.
 *
 * @throws IllegalArgumentException if any annotation violates the constraints of the Streamable HTTP
 * transport; the message names the reason
 */
internal fun toolParamHeaders(tool: Tool): List<ToolParamHeader> {
    val headers = mutableListOf<ToolParamHeader>()
    collectParamHeaders(tool.inputSchema.properties, emptyList(), headers)

    val schemaJson = McpJson.encodeToJsonElement(ToolSchema.serializer(), tool.inputSchema)
    val annotationCount = countAnnotations(schemaJson, isNameMap = false)
    require(annotationCount == headers.size) {
        "$X_MCP_HEADER must only annotate properties reachable from the schema root through 'properties'"
    }

    val names = headers.map { it.headerName.lowercase() }
    require(names.size == names.toSet().size) { "$X_MCP_HEADER values must be case-insensitively unique" }
    return headers
}

private fun collectParamHeaders(properties: JsonObject?, parentPath: List<String>, into: MutableList<ToolParamHeader>) {
    properties ?: return
    for ((name, schema) in properties) {
        val schemaObject = schema as? JsonObject ?: continue
        val path = parentPath + name
        schemaObject[X_MCP_HEADER]?.let { annotation ->
            val value = (annotation as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(value != null && value.isNotEmpty() && value.all(::isTchar)) {
                "$X_MCP_HEADER of '${path.joinToString(".")}' must be a non-empty HTTP token, but was $annotation"
            }
            val type = (schemaObject["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(type in headerPrimitiveTypes) {
                "$X_MCP_HEADER of '${path.joinToString(".")}' must annotate a string, integer, or boolean " +
                    "parameter, but its type is ${schemaObject["type"]}"
            }
            into += ToolParamHeader("$MCP_PARAM_HEADER_PREFIX$value", path)
        }
        collectParamHeaders(schemaObject["properties"] as? JsonObject, path, into)
    }
}

/** Counts `x-mcp-header` annotations anywhere in [element], treating keys of name maps as names, not keywords. */
private fun countAnnotations(element: JsonElement, isNameMap: Boolean): Int = when (element) {
    is JsonObject -> element.entries.sumOf { (key, value) ->
        val own = if (!isNameMap && key == X_MCP_HEADER) 1 else 0
        own + countAnnotations(value, isNameMap = !isNameMap && key in schemaNameMapKeywords)
    }

    is JsonArray -> element.sumOf { countAnnotations(it, isNameMap = false) }

    else -> 0
}

private fun isTchar(char: Char): Boolean =
    char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in TCHAR_PUNCTUATION

/**
 * Resolves the `Mcp-Param-*` headers for a `tools/call` with [arguments], omitting parameters that are
 * absent or `null`.
 */
internal fun paramHeaderValues(headers: List<ToolParamHeader>, arguments: JsonObject?): List<Pair<String, String>> =
    headers.mapNotNull { header ->
        val value = header.path.fold<String, JsonElement?>(arguments) { node, key -> (node as? JsonObject)?.get(key) }
        val primitive = value as? JsonPrimitive ?: return@mapNotNull null
        if (primitive is JsonNull) return@mapNotNull null
        header.headerName to primitive.toHeaderValue().encodeMcpHeaderValue()
    }

private fun JsonPrimitive.toHeaderValue(): String = when {
    isString -> content

    booleanOrNull != null -> content

    // Integers travel as plain decimals; tolerate a whole-valued double such as `42.0`.
    else -> content.toDoubleOrNull()
        ?.takeIf { it % 1.0 == 0.0 && abs(it) <= MAX_SAFE_INTEGER }
        ?.toLong()
        ?.toString()
        ?: content
}

/**
 * Encodes a header value per the Streamable HTTP value-encoding rules: values that are not plain,
 * edge-whitespace-free ASCII, or that look like the Base64 sentinel, are sent as `=?base64?…?=`.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun String.encodeMcpHeaderValue(): String {
    val containsUnsafeCharacters = any { it != '\t' && it.code !in 0x20..0x7e }
    val hasEdgeWhitespace = firstOrNull()?.isWhitespace() == true || lastOrNull()?.isWhitespace() == true
    val matchesBase64Sentinel = startsWith(MCP_BASE64_PREFIX) && endsWith(MCP_BASE64_SUFFIX)

    if (!containsUnsafeCharacters && !hasEdgeWhitespace && !matchesBase64Sentinel) return this

    return "$MCP_BASE64_PREFIX${Base64.Default.encode(encodeToByteArray())}$MCP_BASE64_SUFFIX"
}
