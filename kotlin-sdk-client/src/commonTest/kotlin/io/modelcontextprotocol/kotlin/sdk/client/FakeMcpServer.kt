package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CacheScope
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * A scripted MCP server on an in-memory transport: [handler] decides how to answer each request, and
 * every message the client sends is recorded.
 */
@OptIn(ExperimentalMcpApi::class)
internal class FakeMcpServer(private val handler: suspend FakeMcpServer.(JSONRPCRequest) -> Unit) {
    private val linked = ChannelTransport.createLinkedPair()
    private val mutex = Mutex()
    private val messages = mutableListOf<JSONRPCMessage>()

    /** The transport to connect the client under test to. */
    val clientTransport: ChannelTransport = linked.clientTransport

    suspend fun start() {
        linked.serverTransport.onMessage { message ->
            mutex.withLock { messages += message }
            if (message is JSONRPCRequest) handler(message)
        }
        linked.serverTransport.start()
    }

    suspend fun received(): List<JSONRPCMessage> = mutex.withLock { messages.toList() }

    suspend fun requests(method: String): List<JSONRPCRequest> =
        received().filterIsInstance<JSONRPCRequest>().filter { it.method == method }

    suspend fun notifications(method: String): List<JSONRPCNotification> =
        received().filterIsInstance<JSONRPCNotification>().filter { it.method == method }

    suspend fun respond(request: JSONRPCRequest, result: RequestResult) {
        linked.serverTransport.send(JSONRPCResponse(request.id, result))
    }

    suspend fun respondError(
        request: JSONRPCRequest,
        code: Int,
        message: String = "error",
        data: JsonElement? = null,
    ) {
        linked.serverTransport.send(JSONRPCError(request.id, RPCError(code, message, data)))
    }

    suspend fun notify(notification: Notification) {
        linked.serverTransport.send(notification.toJSON())
    }

    suspend fun send(message: JSONRPCMessage) {
        linked.serverTransport.send(message)
    }

    suspend fun close() {
        linked.serverTransport.close()
    }

    /** Answers `server/discover` as a server supporting [supportedVersions]. */
    suspend fun respondDiscover(
        request: JSONRPCRequest,
        supportedVersions: List<String> = listOf(LATEST_MODERN_PROTOCOL_VERSION),
        capabilities: ServerCapabilities = defaultCapabilities,
    ) {
        respond(
            request,
            DiscoverResult(
                supportedVersions = supportedVersions,
                capabilities = capabilities,
                instructions = "Use the tools wisely.",
                ttlMs = 0,
                cacheScope = CacheScope.Public,
                meta = buildJsonObject {
                    put("io.modelcontextprotocol/serverInfo", McpJson.encodeToJsonElement(serverInfo))
                },
            ),
        )
    }

    /** Answers `initialize` as a handshake-based server. */
    suspend fun respondInitialize(request: JSONRPCRequest, capabilities: ServerCapabilities = defaultCapabilities) {
        respond(
            request,
            InitializeResult(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = capabilities,
                serverInfo = serverInfo,
            ),
        )
    }

    companion object {
        val serverInfo = Implementation(name = "fake-server", version = "1.0.0")

        val defaultCapabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            prompts = ServerCapabilities.Prompts(listChanged = true),
            resources = ServerCapabilities.Resources(listChanged = true, subscribe = true),
            logging = JsonObject(emptyMap()),
        )
    }
}

/** The `_meta` object of this request's params, or an empty object. */
internal val JSONRPCRequest.meta: JsonObject
    get() = (params as? JsonObject)?.get("_meta") as? JsonObject ?: JsonObject(emptyMap())

/** This request's params, or an empty object. */
internal val JSONRPCRequest.paramsObject: JsonObject
    get() = params as? JsonObject ?: JsonObject(emptyMap())
