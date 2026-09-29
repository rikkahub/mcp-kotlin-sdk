package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Reserved `_meta` keys of the request-scoped protocol (2026-07-28 and later).
internal const val PROTOCOL_VERSION_META_KEY = "io.modelcontextprotocol/protocolVersion"
internal const val CLIENT_INFO_META_KEY = "io.modelcontextprotocol/clientInfo"
internal const val CLIENT_CAPABILITIES_META_KEY = "io.modelcontextprotocol/clientCapabilities"
internal const val LOG_LEVEL_META_KEY = "io.modelcontextprotocol/logLevel"
internal const val SERVER_INFO_META_KEY = "io.modelcontextprotocol/serverInfo"

// Multi round-trip request (SEP-2322) retry parameters and result discriminator.
internal const val INPUT_RESPONSES_PARAM = "inputResponses"
internal const val REQUEST_STATE_PARAM = "requestState"
internal const val RESULT_TYPE_FIELD = "resultType"
internal const val COMPLETE_RESULT_TYPE = "complete"

/**
 * Which MCP protocol eras a [Client] may speak.
 *
 * Handshake-based ("legacy") protocol versions, up to `2025-11-25`, open a session with an
 * `initialize` handshake. Request-scoped ("modern") protocol versions, from `2026-07-28`, have no
 * handshake: every request carries its protocol version and client capabilities in `_meta`.
 */
@ExperimentalMcpApi
public enum class ProtocolEra {
    /**
     * Probes the server with `server/discover` and uses a request-scoped protocol version when the
     * server supports one, falling back to the `initialize` handshake otherwise.
     *
     * The legacy HTTP+SSE transport ([SseClientTransport]) always uses the handshake.
     */
    Auto,

    /** Always uses the `initialize` handshake, as SDK versions without request-scoped support did. */
    Legacy,

    /** Only uses request-scoped protocol versions; connecting to a handshake-based server fails. */
    Modern,
}

/** The request-scoped protocol version declared in this request's `_meta`, or `null` for legacy requests. */
internal fun JSONRPCRequest.requestScopedProtocolVersion(): String? {
    val meta = (params as? JsonObject)?.get("_meta") as? JsonObject ?: return null
    return (meta[PROTOCOL_VERSION_META_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
