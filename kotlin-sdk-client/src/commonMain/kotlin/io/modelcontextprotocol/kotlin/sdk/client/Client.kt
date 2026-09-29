@file:OptIn(ExperimentalMcpApi::class)

package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.ProtocolOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverRequest
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.InputRequiredResult
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.MODERN_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionFilter
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionsAcknowledgedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionsListenRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionsListenRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.subscriptionId
import io.modelcontextprotocol.kotlin.sdk.types.supportsUrl
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** Default time [Client.connect] waits for the `server/discover` probe. */
private val DEFAULT_DISCOVERY_TIMEOUT: Duration = 5.seconds

/** Upper bound on `input_required` rounds for one request, guarding against a server that never completes it. */
private const val MAX_INPUT_REQUIRED_ROUNDS = 16

/** Methods a server may embed as input requests in an [InputRequiredResult]. */
private val inputRequestMethods = setOf(
    Method.Defined.ElicitationCreate,
    Method.Defined.SamplingCreateMessage,
    Method.Defined.RootsList,
)

/**
 * Options for configuring the MCP client.
 *
 * @property capabilities The capabilities this client supports.
 * @param enforceStrictCapabilities Whether to strictly enforce capabilities when interacting with the server.
 * @param handlerCoroutineContext Coroutine context for inbound handlers. See [ProtocolOptions.handlerCoroutineContext].
 */
public class ClientOptions(
    public val capabilities: ClientCapabilities = ClientCapabilities(),
    enforceStrictCapabilities: Boolean = true,
    handlerCoroutineContext: CoroutineContext = Dispatchers.Default,
) : ProtocolOptions(
    enforceStrictCapabilities = enforceStrictCapabilities,
    handlerCoroutineContext = handlerCoroutineContext,
) {
    /**
     * Which protocol eras the client may speak. Defaults to [ProtocolEra.Auto], which uses the
     * request-scoped protocol (`2026-07-28`) when the server supports it and the `initialize`
     * handshake otherwise.
     */
    @ExperimentalMcpApi
    public var protocolEra: ProtocolEra = ProtocolEra.Auto

    /**
     * How long [Client.connect] waits for the `server/discover` probe before treating the server as
     * handshake-based. Only handshake-based servers that ignore unknown requests hit this timeout.
     */
    @ExperimentalMcpApi
    public var discoveryTimeout: Duration = DEFAULT_DISCOVERY_TIMEOUT
}

/**
 * Initializes and connects an MCP client using the provided clientInfo [Implementation], client options,
 * and transport mechanism.
 *
 * @param clientInfo The implementation details of the MCP client, including its name, version, and other metadata.
 * @param clientOptions Optional client configuration settings, such as supported capabilities
 *      and strict enforcement options. Defaults to a new instance of [ClientOptions].
 * @param transport The transport mechanism used for communication.
 * @return An instance of [Client] that is connected and ready for use with the specified transport.
 */
@ExperimentalMcpApi
public suspend fun mcpClient(
    clientInfo: Implementation,
    clientOptions: ClientOptions = ClientOptions(),
    transport: Transport,
): Client {
    val client = Client(
        clientInfo = clientInfo,
        options = clientOptions,
    )
    client.connect(transport)
    return client
}

/**
 * An MCP client on top of a pluggable transport.
 *
 * [connect] negotiates the protocol with the server, as selected by [ClientOptions.protocolEra]: it
 * probes the server with `server/discover` and uses a request-scoped protocol version (`2026-07-28`)
 * when available, and otherwise performs the `initialize` handshake of earlier versions. Afterwards,
 * [protocolVersion], [serverCapabilities], and [serverVersion] describe the connected server.
 *
 * With a request-scoped protocol version, every request carries the client's protocol version,
 * information, and capabilities in `_meta`, and server requests for input (elicitation, sampling,
 * roots) embedded in `tools/call`, `prompts/get`, and `resources/read` results are fulfilled with the
 * registered handlers and the request is retried automatically.
 *
 * You can extend this class with custom request/notification/result types if needed.
 *
 * @param clientInfo Information about the client implementation (name, version).
 * @param options Configuration options for this client.
 */
public open class Client(private val clientInfo: Implementation, options: ClientOptions = ClientOptions()) :
    Protocol(options) {

    /**
     * Retrieves the server's reported capabilities after the initialization process completes.
     *
     * @return The server's capabilities, or `null` if initialization is not yet complete.
     */
    public var serverCapabilities: ServerCapabilities? = null
        private set

    /**
     * Optional human-readable instructions or description from the server.
     *
     * @return Instructions provided by the server, or `null` if none were given or initialization is not yet complete.
     */
    public var serverInstructions: String? = null
        private set

    /**
     * Retrieves the server's reported version information after initialization.
     *
     * @return Information about the server's implementation, or `null` if initialization is not yet complete.
     */
    public var serverVersion: Implementation? = null
        private set

    /**
     * The protocol version negotiated with the server, or `null` before [connect] completes.
     *
     * This is a request-scoped version from [MODERN_PROTOCOL_VERSIONS] when the server supports one, and
     * a handshake-based version from [SUPPORTED_PROTOCOL_VERSIONS] otherwise.
     */
    public val protocolVersion: String?
        get() = negotiatedProtocolVersion.value

    private val clientOptions: ClientOptions = options

    private val capabilities: ClientCapabilities = options.capabilities

    private val roots = atomic(persistentMapOf<String, Root>())

    private val negotiatedProtocolVersion = atomic<String?>(null)

    /** Log level sent with each request of a request-scoped protocol version, set by [setLoggingLevel]. */
    private val requestLogLevel = atomic<LoggingLevel?>(null)

    /** Open `subscriptions/listen` requests, keyed by request ID (which is also the subscription ID). */
    private val subscriptions = atomic(persistentMapOf<RequestId, ActiveSubscription>())

    private val clientInfoJson: JsonElement by lazy { McpJson.encodeToJsonElement(clientInfo) }

    private val capabilitiesJson: JsonElement by lazy { McpJson.encodeToJsonElement(capabilities) }

    private val usesRequestScopedProtocol: Boolean
        get() = protocolVersion in MODERN_PROTOCOL_VERSIONS

    init {
        logger.debug { "Initializing MCP client with capabilities: $capabilities" }

        // Internal handlers for roots
        if (capabilities.roots != null) {
            setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, _ ->
                handleListRoots()
            }
        }

        setNotificationHandler<SubscriptionsAcknowledgedNotification>(
            Method.Defined.NotificationsSubscriptionsAcknowledged,
        ) { notification ->
            val subscription = notification.subscriptionId?.let { subscriptions.value[it] }
            if (subscription == null) {
                logger.debug { "Ignoring acknowledgement of unknown subscription ${notification.subscriptionId}" }
            } else {
                subscription.acknowledge(notification.params.notifications)
            }
            CompletableDeferred(Unit)
        }
    }

    protected fun assertCapability(capability: String, method: String) {
        val caps = serverCapabilities
        val hasCapability = when (capability) {
            "logging" -> caps?.logging != null
            "prompts" -> caps?.prompts != null
            "resources" -> caps?.resources != null
            "tools" -> caps?.tools != null
            "tasks" -> caps?.tasks != null
            else -> true
        }

        check(hasCapability) {
            "Server does not support $capability (required for $method)"
        }
    }

    /**
     * Connects the client to the given [transport] and negotiates the protocol version with the server.
     *
     * Depending on [ClientOptions.protocolEra], the client probes the server with `server/discover` and
     * adopts a request-scoped protocol version, or performs the `initialize` handshake.
     *
     * @param transport The transport to use for communication with the server.
     * @throws IllegalStateException If the server supports none of the client's protocol versions.
     */
    override suspend fun connect(transport: Transport) {
        negotiatedProtocolVersion.value = null
        requestLogLevel.value = null
        super.connect(transport)

        try {
            val requestScoped = when (clientOptions.protocolEra) {
                ProtocolEra.Legacy -> false

                ProtocolEra.Auto ->
                    transport !is SseClientTransport && negotiateRequestScopedVersion(allowHandshakeFallback = true)

                ProtocolEra.Modern -> negotiateRequestScopedVersion(allowHandshakeFallback = false)
            }
            if (!requestScoped) initializeSession(transport)
            enableConcurrentDispatch()
        } catch (error: Throwable) {
            if (error !is CancellationException) {
                logger.error(error) { "Failed to initialize client: ${error.message}" }
            }
            close()

            when (error) {
                is CancellationException,
                is McpException,
                is StreamableHttpError,
                is SerializationException,
                -> throw error

                else -> throw IllegalStateException("Error connecting to transport: ${error.message}", error)
            }
        }
    }

    /** Performs the `initialize` handshake of handshake-based protocol versions. */
    private suspend fun initializeSession(transport: Transport) {
        val message = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = capabilities,
                clientInfo = clientInfo,
            ),
        )
        val result = request<InitializeResult>(message)

        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(result.protocolVersion)) {
            error(
                "Server's protocol version is not supported: ${result.protocolVersion}",
            )
        }

        serverCapabilities = result.capabilities
        serverVersion = result.serverInfo
        serverInstructions = result.instructions
        adoptProtocolVersion(transport, result.protocolVersion)

        notification(InitializedNotification())
    }

    /**
     * Selects a request-scoped protocol version with `server/discover`, following
     * `UnsupportedProtocolVersionError` hints until a mutually supported version is found.
     *
     * @return `true` if a request-scoped version was adopted, `false` if the client should fall back to
     * the `initialize` handshake
     */
    private suspend fun negotiateRequestScopedVersion(allowHandshakeFallback: Boolean): Boolean {
        val attempted = mutableSetOf<String>()
        var version = MODERN_PROTOCOL_VERSIONS.first()
        while (true) {
            attempted += version
            val serverVersions = when (val outcome = probe(version, allowHandshakeFallback)) {
                is DiscoveryOutcome.Discovered -> {
                    val supported = outcome.result.supportedVersions
                    if (supported.isEmpty() || version in supported) {
                        applyDiscoverResult(version, outcome.result)
                        return true
                    }
                    supported
                }

                is DiscoveryOutcome.Unsupported -> outcome.supportedVersions

                DiscoveryOutcome.HandshakeBased -> return false
            }

            version = MODERN_PROTOCOL_VERSIONS.firstOrNull { it in serverVersions && it !in attempted }
                ?: if (allowHandshakeFallback && serverVersions.any { it in SUPPORTED_PROTOCOL_VERSIONS }) {
                    // A dual-era server that only shares a handshake-based version with this client.
                    return false
                } else {
                    error("Server supports none of this client's protocol versions: $serverVersions")
                }
        }
    }

    private suspend fun probe(version: String, allowHandshakeFallback: Boolean): DiscoveryOutcome = try {
        DiscoveryOutcome.Discovered(discover(version, RequestOptions(timeout = clientOptions.discoveryTimeout)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: McpException) {
        when (e.code) {
            RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION -> DiscoveryOutcome.Unsupported(e.supportedVersions())

            RPCError.ErrorCode.HEADER_MISMATCH,
            RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
            -> throw e

            else -> handshakeBasedOrThrow(e, allowHandshakeFallback)
        }
    } catch (e: Exception) {
        handshakeBasedOrThrow(e, allowHandshakeFallback)
    }

    /**
     * Treats a failed probe as the sign of a handshake-based server, which answers `server/discover`
     * with an implementation-defined error, an HTTP 4xx, or not at all.
     */
    private fun handshakeBasedOrThrow(cause: Exception, allowHandshakeFallback: Boolean): DiscoveryOutcome {
        // A lost connection says nothing about the server's protocol era.
        if (!allowHandshakeFallback || transport == null) throw cause
        logger.debug(cause) { "server/discover failed, falling back to the initialize handshake" }
        return DiscoveryOutcome.HandshakeBased
    }

    private fun McpException.supportedVersions(): List<String> = data
        ?.let { runCatching { McpJson.decodeFromJsonElement<UnsupportedProtocolVersionData>(it) }.getOrNull() }
        ?.supported
        .orEmpty()

    private fun applyDiscoverResult(version: String, result: DiscoverResult) {
        serverCapabilities = result.capabilities
        serverInstructions = result.instructions
        result.meta?.get(SERVER_INFO_META_KEY)
            ?.let { runCatching { McpJson.decodeFromJsonElement<Implementation>(it) }.getOrNull() }
            ?.let { serverVersion = it }
        transport?.let { adoptProtocolVersion(it, version) }
    }

    private fun adoptProtocolVersion(transport: Transport, version: String) {
        negotiatedProtocolVersion.value = version
        // Streamable HTTP mirrors the negotiated version into the MCP-Protocol-Version header.
        (transport as? StreamableHttpClientTransport)?.protocolVersion = version
    }

    /**
     * Queries the server's supported protocol versions, capabilities, and identity with
     * `server/discover`, and refreshes [serverCapabilities], [serverInstructions], and [serverVersion].
     *
     * @param options Optional request options.
     * @throws IllegalStateException If the negotiated protocol version is not request-scoped.
     */
    @ExperimentalMcpApi
    public suspend fun discover(options: RequestOptions? = null): DiscoverResult {
        val version = protocolVersion?.takeIf { it in MODERN_PROTOCOL_VERSIONS }
            ?: error("server/discover requires a request-scoped protocol version, but $protocolVersion is in use")
        return discover(version, options).also { applyDiscoverResult(version, it) }
    }

    private suspend fun discover(version: String, options: RequestOptions?): DiscoverResult =
        request(DiscoverRequest(DiscoverRequestParams(RequestMeta(requestScopedMeta(version)))), options)

    /** The `_meta` entries every request of a request-scoped protocol [version] carries. */
    private fun requestScopedMeta(version: String): JsonObject = buildJsonObject {
        put(PROTOCOL_VERSION_META_KEY, version)
        put(CLIENT_INFO_META_KEY, clientInfoJson)
        put(CLIENT_CAPABILITIES_META_KEY, capabilitiesJson)
        requestLogLevel.value?.let { put(LOG_LEVEL_META_KEY, McpJson.encodeToJsonElement(it)) }
    }

    override fun prepareOutgoingRequest(request: JSONRPCRequest): JSONRPCRequest {
        val version = protocolVersion?.takeIf { it in MODERN_PROTOCOL_VERSIONS } ?: return request
        val params = request.params as? JsonObject ?: JsonObject(emptyMap())
        val meta = params["_meta"] as? JsonObject ?: JsonObject(emptyMap())
        // Metadata already on the request wins, e.g. the explicit version of a discovery probe.
        val merged = JsonObject(requestScopedMeta(version) + meta)
        return request.copy(params = JsonObject(params + ("_meta" to merged)))
    }

    override fun onPeerCancelledRequest(requestId: RequestId): Boolean {
        // Over stdio, a server ends a subscription by cancelling its subscriptions/listen request.
        val subscription = subscriptions.value[requestId] ?: return false
        subscription.closedByServer.complete(Unit)
        return true
    }

    override fun assertCapabilityForMethod(method: Method) {
        when (method) {
            Method.Defined.LoggingSetLevel -> {
                checkNotNull(serverCapabilities?.logging) {
                    "Server does not support logging (required for $method)"
                }
            }

            Method.Defined.PromptsGet,
            Method.Defined.PromptsList,
            -> {
                checkNotNull(serverCapabilities?.prompts) {
                    "Server does not support prompts (required for $method)"
                }
            }

            Method.Defined.CompletionComplete -> {
                checkNotNull(serverCapabilities?.completions) {
                    "Server does not support completions (required for $method)"
                }
            }

            Method.Defined.ResourcesList,
            Method.Defined.ResourcesTemplatesList,
            Method.Defined.ResourcesRead,
            Method.Defined.ResourcesSubscribe,
            Method.Defined.ResourcesUnsubscribe,
            -> {
                val resCaps = serverCapabilities?.resources
                    ?: error("Server does not support resources (required for $method)")

                if (method == Method.Defined.ResourcesSubscribe) {
                    check(resCaps.subscribe == true) {
                        "Server does not support resource subscriptions (required for $method)"
                    }
                }
            }

            Method.Defined.ToolsCall, Method.Defined.ToolsList -> {
                checkNotNull(serverCapabilities?.tools) {
                    "Server does not support tools (required for $method)"
                }
            }

            Method.Defined.TasksGet,
            Method.Defined.TasksResult,
            Method.Defined.TasksList,
            Method.Defined.TasksCancel,
            -> assertTasksCapabilityForMethod(method)

            Method.Defined.Initialize, Method.Defined.Ping -> {
                // No specific capability required
            }

            else -> {
                // For unknown or future methods, no assertion by default
            }
        }
    }

    private fun assertTasksCapabilityForMethod(method: Method) {
        val tasks = serverCapabilities?.tasks
            ?: error("Server does not support tasks (required for $method)")
        when (method) {
            Method.Defined.TasksList -> checkNotNull(tasks.list) {
                "Server does not support listing tasks (required for $method)"
            }

            Method.Defined.TasksCancel -> checkNotNull(tasks.cancel) {
                "Server does not support cancelling tasks (required for $method)"
            }

            else -> {
                // TasksGet, TasksResult: base tasks capability suffices.
            }
        }
    }

    override fun assertNotificationCapability(method: Method) {
        when (method) {
            Method.Defined.NotificationsRootsListChanged -> {
                check(capabilities.roots?.listChanged == true) {
                    "Client does not support roots list changed notifications (required for $method)"
                }
            }

            Method.Defined.NotificationsTasksStatus -> {
                checkNotNull(capabilities.tasks) {
                    "Client does not support tasks (required for $method)"
                }
            }

            Method.Defined.NotificationsInitialized,
            Method.Defined.NotificationsCancelled,
            Method.Defined.NotificationsProgress,
            -> {
                // Always allowed
            }

            else -> {
                // For notifications not specifically listed, no assertion by default
            }
        }
    }

    override fun assertRequestHandlerCapability(method: Method) {
        when (method) {
            Method.Defined.SamplingCreateMessage -> {
                checkNotNull(capabilities.sampling) {
                    "Client does not support sampling capability (required for $method)"
                }
            }

            Method.Defined.RootsList -> {
                checkNotNull(capabilities.roots) {
                    "Client does not support roots capability (required for $method)"
                }
            }

            Method.Defined.ElicitationCreate -> {
                checkNotNull(capabilities.elicitation) {
                    "Client does not support elicitation capability (required for $method)"
                }
            }

            Method.Defined.Ping -> {
                // No capability required
            }

            else -> {}
        }
    }

    /**
     * Wraps incoming-request handlers with SEP-1577 client-side enforcement.
     *
     * For `sampling/createMessage`: if the incoming request carries `tools` or
     * `toolChoice` but this client did not advertise [ClientCapabilities.Sampling.tools],
     * the wrapper throws an [McpException] with JSON-RPC error code `InvalidParams`
     * before the user-supplied handler runs. Matches the TypeScript SDK wrapper in
     * `Client.setRequestHandler`.
     */
    override fun <T : Request> wrapRequestHandler(
        method: Method,
        block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ): suspend (T, RequestHandlerExtra) -> RequestResult? {
        if (method != Method.Defined.SamplingCreateMessage) return block
        return { request, extra ->
            (request as? CreateMessageRequest)?.let { validateSamplingToolsCapability(it, capabilities) }
            block(request, extra)
        }
    }

    /**
     * Sends a ping request to the server to check connectivity.
     *
     * Request-scoped protocol versions removed `ping`; there the client sends `server/discover`
     * instead, which every server must answer.
     *
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support the ping method (unlikely).
     */
    public suspend fun ping(options: RequestOptions? = null): EmptyResult {
        if (!usesRequestScopedProtocol) return request(PingRequest(), options)
        discover(options)
        return EmptyResult()
    }

    /**
     * Sends a completion request to the server, typically to generate or complete some content.
     *
     * @param params The completion request parameters.
     * @param options Optional request options.
     * @return The completion result returned by the server, or `null` if none.
     * @throws IllegalStateException If the server does not support completions.
     */
    public suspend fun complete(params: CompleteRequest, options: RequestOptions? = null): CompleteResult =
        request(params, options)

    /**
     * Sets the logging level on the server.
     *
     * Request-scoped protocol versions removed `logging/setLevel`; there the level is stored locally and
     * sent with every subsequent request, and the server emits no log messages until a level is set.
     *
     * @param level The desired logging level.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support logging.
     */
    public suspend fun setLoggingLevel(level: LoggingLevel, options: RequestOptions? = null): EmptyResult {
        if (!usesRequestScopedProtocol) return request(SetLevelRequest(SetLevelRequestParams(level)), options)
        if (clientOptions.enforceStrictCapabilities) assertCapabilityForMethod(Method.Defined.LoggingSetLevel)
        requestLogLevel.value = level
        return EmptyResult()
    }

    /**
     * Retrieves a prompt by name from the server.
     *
     * @param request The prompt request containing the prompt name.
     * @param options Optional request options.
     * @return The requested prompt details, or `null` if not found.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public suspend fun getPrompt(request: GetPromptRequest, options: RequestOptions? = null): GetPromptResult =
        requestWithInput(request, options)

    /**
     * Lists all available prompts from the server.
     *
     * @param request A request object for listing prompts (usually empty).
     * @param options Optional request options.
     * @return The list of available prompts, or `null` if none.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public suspend fun listPrompts(
        request: ListPromptsRequest = ListPromptsRequest(),
        options: RequestOptions? = null,
    ): ListPromptsResult = request(request, options)

    /**
     * Lists all available resources from the server.
     *
     * @param request A request object for listing resources (usually empty).
     * @param options Optional request options.
     * @return The list of resources, or `null` if none.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun listResources(
        request: ListResourcesRequest = ListResourcesRequest(),
        options: RequestOptions? = null,
    ): ListResourcesResult = request(request, options)

    /**
     * Lists resource templates available on the server.
     *
     * @param request The request object for listing resource templates.
     * @param options Optional request options.
     * @return The list of resource templates, or `null` if none.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun listResourceTemplates(
        request: ListResourceTemplatesRequest,
        options: RequestOptions? = null,
    ): ListResourceTemplatesResult = request(request, options)

    /**
     * Reads a resource from the server by its URI.
     *
     * @param request The request object containing the resource URI.
     * @param options Optional request options.
     * @return The resource content, or `null` if the resource is not found.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun readResource(
        request: ReadResourceRequest,
        options: RequestOptions? = null,
    ): ReadResourceResult = requestWithInput(request, options)

    /**
     * Subscribes to resource changes on the server.
     *
     * Request-scoped protocol versions replaced `resources/subscribe` with `subscriptions/listen`; use
     * [listen] with [SubscriptionFilter.resourceSubscriptions], which works with every protocol version.
     *
     * @param request The subscription request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions, or the
     * negotiated protocol version is request-scoped.
     */
    public suspend fun subscribeResource(request: SubscribeRequest, options: RequestOptions? = null): EmptyResult {
        check(!usesRequestScopedProtocol) { RESOURCE_SUBSCRIPTION_REMOVED }
        return request(request, options)
    }

    /**
     * Unsubscribes from resource changes on the server.
     *
     * @param request The unsubscribe request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions, or the
     * negotiated protocol version is request-scoped.
     */
    public suspend fun unsubscribeResource(request: UnsubscribeRequest, options: RequestOptions? = null): EmptyResult {
        check(!usesRequestScopedProtocol) { RESOURCE_SUBSCRIPTION_REMOVED }
        return request(request, options)
    }

    /**
     * Receives the server's change notifications selected by [filter] until the subscription ends.
     *
     * Notifications are delivered to the handlers registered with [setNotificationHandler], for example
     * for [Method.Defined.NotificationsToolsListChanged] or [Method.Defined.NotificationsResourcesUpdated].
     * [onAcknowledged] receives the subset of [filter] the server agreed to honor.
     *
     * With a request-scoped protocol version this sends `subscriptions/listen` and returns when the
     * server ends the subscription; cancel the calling coroutine to end it from the client. With a
     * handshake-based version, list-change notifications need no subscription and resources are
     * subscribed with `resources/subscribe`; the call then suspends until cancelled, and unsubscribes
     * the resources when it is.
     *
     * @param filter The notification types to receive.
     * @param onAcknowledged Invoked once the server has acknowledged the subscription.
     * @throws McpException If the connection closes or the server rejects the subscription.
     */
    @ExperimentalMcpApi
    public suspend fun listen(filter: SubscriptionFilter, onAcknowledged: (SubscriptionFilter) -> Unit = {}) {
        if (!usesRequestScopedProtocol) return listenInSession(filter, onAcknowledged)

        val message = SubscriptionsListenRequest(SubscriptionsListenRequestParams(filter)).toJSON()
        val subscription = ActiveSubscription(onAcknowledged)
        subscriptions.update { it.put(message.id, subscription) }
        try {
            coroutineScope {
                val response = async {
                    sendRequestMessage<RequestResult>(message, RequestOptions(timeout = Duration.INFINITE))
                }
                select {
                    response.onAwait { }
                    subscription.closedByServer.onAwait { response.cancel() }
                }
            }
        } finally {
            subscriptions.update { it.remove(message.id) }
        }
    }

    /** [listen] for handshake-based servers, which push change notifications without a subscription. */
    private suspend fun listenInSession(filter: SubscriptionFilter, onAcknowledged: (SubscriptionFilter) -> Unit) {
        val caps = serverCapabilities
        val requestedUris = filter.resourceSubscriptions.orEmpty()
        val uris = requestedUris.takeIf { caps?.resources?.subscribe == true }.orEmpty()
        val subscribed = mutableListOf<String>()
        try {
            for (uri in uris) {
                request<EmptyResult>(SubscribeRequest(SubscribeRequestParams(uri)))
                subscribed += uri
            }
            onAcknowledged(
                SubscriptionFilter(
                    toolsListChanged = filter.toolsListChanged.honoredIf(caps?.tools?.listChanged == true),
                    promptsListChanged = filter.promptsListChanged.honoredIf(caps?.prompts?.listChanged == true),
                    resourcesListChanged = filter.resourcesListChanged.honoredIf(
                        caps?.resources?.listChanged == true,
                    ),
                    resourceSubscriptions = subscribed.toList().takeIf { it.isNotEmpty() },
                ),
            )
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { unsubscribeAll(subscribed) }
        }
    }

    private suspend fun unsubscribeAll(uris: List<String>) {
        for (uri in uris) {
            try {
                request<EmptyResult>(UnsubscribeRequest(UnsubscribeRequestParams(uri)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to unsubscribe from resource $uri" }
            }
        }
    }

    private fun Boolean?.honoredIf(supported: Boolean): Boolean? = if (this == true && supported) true else null

    /**
     * Calls a tool on the server by name, passing the specified arguments and metadata.
     *
     * @param name The name of the tool to call.
     * @param arguments A map of argument names to values for the tool.
     * @param meta A map of metadata key-value pairs. Keys must follow MCP specification format.
     *             - Optional prefix: dot-separated labels followed by slash (e.g., "api.example.com/")
     *             - Name: alphanumeric start/end, may contain hyphens, underscores, dots, alphanumerics
     *             - Reserved prefixes starting with "mcp" or "modelcontextprotocol" are forbidden
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>,
        meta: Map<String, Any?> = emptyMap(),
        options: RequestOptions? = null,
    ): CallToolResult {
        validateMetaKeys(meta.keys)

        val jsonArguments = arguments.toJson()
        val jsonMeta = meta.toJson()

        val request = CallToolRequest(
            CallToolRequestParams(
                name = name,
                arguments = JsonObject(jsonArguments),
                meta = RequestMeta(JsonObject(jsonMeta)),
            ),
        )
        return callTool(request, options)
    }

    /**
     * Calls a tool on the server using a [CallToolRequest] object.
     *
     * @param request The request object containing the tool name and arguments.
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(request: CallToolRequest, options: RequestOptions? = null): CallToolResult {
        if (!usesRequestScopedProtocol || transport !is StreamableHttpClientTransport) {
            return requestWithInput(request, options)
        }
        return try {
            requestWithInput(request, options)
        } catch (e: McpException) {
            if (e.code != RPCError.ErrorCode.HEADER_MISMATCH) throw e
            // The tool's x-mcp-header annotations may have changed since it was last listed.
            logger.info { "Server rejected the headers of tool call '${request.name}', refreshing tools and retrying" }
            refreshTools()
            requestWithInput(request, options)
        }
    }

    /** Lists all tools, page by page, which refreshes their `x-mcp-header` annotations on the transport. */
    private suspend fun refreshTools() {
        var cursor: String? = null
        do {
            cursor = listTools(ListToolsRequest(cursor?.let { PaginatedRequestParams(cursor = it) })).nextCursor
        } while (cursor != null)
    }

    /**
     * Lists all available tools on the server.
     *
     * @param request A request object for listing tools (usually empty).
     * @param options Optional request options.
     * @return The list of available tools, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun listTools(
        request: ListToolsRequest = ListToolsRequest(),
        options: RequestOptions? = null,
    ): ListToolsResult {
        val result = request<ListToolsResult>(request, options)
        val httpTransport = transport as? StreamableHttpClientTransport
        if (!usesRequestScopedProtocol || httpTransport == null) return result
        // Streamable HTTP clients must drop tools whose x-mcp-header annotations are invalid.
        val tools = result.tools.filter(httpTransport::registerToolParamHeaders)
        return if (tools.size == result.tools.size) result else result.copy(tools = tools)
    }

    /**
     * Sends [request], answering any `input_required` results of a request-scoped server by fulfilling
     * their input requests with the registered handlers and retrying, until the request completes.
     */
    private suspend fun <T : RequestResult> requestWithInput(request: Request, options: RequestOptions?): T {
        if (!usesRequestScopedProtocol) return request(request, options)

        var retryParams: Map<String, JsonElement> = emptyMap()
        repeat(MAX_INPUT_REQUIRED_ROUNDS) {
            // Every attempt is an independent request with a fresh ID.
            val message = request.toJSON().let { base ->
                if (retryParams.isEmpty()) {
                    base
                } else {
                    val params = base.params as? JsonObject ?: JsonObject(emptyMap())
                    base.copy(params = JsonObject(params + retryParams))
                }
            }
            val result = sendRequestMessage<RequestResult>(message, options)
            if (result !is InputRequiredResult) {
                @Suppress("UNCHECKED_CAST")
                return result as T
            }
            retryParams = buildMap {
                result.inputRequests?.let { put(INPUT_RESPONSES_PARAM, fulfilInputRequests(it)) }
                result.requestState?.let { put(REQUEST_STATE_PARAM, JsonPrimitive(it)) }
            }
        }
        error("Server still required input for ${request.method.value} after $MAX_INPUT_REQUIRED_ROUNDS attempts")
    }

    /** Runs the local handler of each input request and collects the results, keyed like the requests. */
    private suspend fun fulfilInputRequests(inputRequests: Map<String, Request>): JsonObject {
        val responses = LinkedHashMap<String, JsonElement>()
        for ((key, inputRequest) in inputRequests) {
            val method = inputRequest.method
            check(method in inputRequestMethods) { "Server requested unsupported input '${method.value}' ($key)" }
            check(method.value in requestHandlers || fallbackRequestHandler != null) {
                "Server requested '${method.value}' input ($key), but no handler is registered for it"
            }
            val result = handleRequestLocally(
                JSONRPCRequest(id = RequestId(key), method = method.value, params = inputRequest.toJSON().params),
            )
            val resultJson = McpJson.encodeToJsonElement<RequestResult>(result).jsonObject
            responses[key] = JsonObject(mapOf(RESULT_TYPE_FIELD to JsonPrimitive(COMPLETE_RESULT_TYPE)) + resultJson)
        }
        return JsonObject(responses)
    }

    /**
     * Registers a single root.
     *
     * @param uri The URI of the root.
     * @param name A human-readable name for the root.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoot(uri: String, name: String) {
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to add root '$name': Client does not support roots capability" }
            "Client does not support roots capability."
        }
        logger.info { "Adding root: $name ($uri)" }
        roots.update { current -> current.put(uri, Root(uri, name)) }
    }

    /**
     * Registers multiple roots at once.
     *
     * @param rootsToAdd A list of [Root] objects to register.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoots(rootsToAdd: List<Root>) {
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to add roots: Client does not support roots capability" }
            "Client does not support roots capability."
        }
        logger.info { "Adding ${rootsToAdd.size} roots" }
        roots.update { current -> current.putAll(rootsToAdd.associateBy { it.uri }) }
    }

    /**
     * Removes a single root by URI.
     *
     * @param uri The URI of the root to remove.
     * @return True if the root was removed, false if it wasn't found.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun removeRoot(uri: String): Boolean {
        checkNotNull(capabilities.roots) {
            "Client does not support roots capability."
        }
        logger.info { "Removing root: $uri" }
        val oldMap = roots.getAndUpdate { current -> current.remove(uri) }
        val removed = uri in oldMap
        logger.debug {
            if (removed) {
                "Root removed: $uri"
            } else {
                "Root not found: $uri"
            }
        }
        return removed
    }

    /**
     * Removes multiple roots at once.
     *
     * @param uris A list of root URIs to remove.
     * @return The number of roots that were successfully removed.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun removeRoots(uris: List<String>): Int {
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to remove roots: Client does not support roots capability" }
            "Client does not support roots capability."
        }
        logger.info { "Removing ${uris.size} roots" }

        val oldMap = roots.getAndUpdate { current -> current - uris.toPersistentSet() }

        val removedCount = uris.count { it in oldMap }

        logger.info {
            if (removedCount > 0) {
                "Removed $removedCount roots"
            } else {
                "No roots were removed"
            }
        }
        return removedCount
    }

    /**
     * Notifies the server that the list of roots has changed.
     * Typically used if the client is managing some form of hierarchical structure.
     *
     * @throws IllegalStateException If the client or server does not support roots.
     */
    public suspend fun sendRootsListChanged() {
        if (usesRequestScopedProtocol) {
            // Request-scoped servers ask for roots with each request, so they never hold a stale list.
            logger.debug { "Not sending notifications/roots/list_changed: removed in $protocolVersion" }
            return
        }
        notification(RootsListChangedNotification())
    }

    /**
     * Sets the elicitation handler.
     *
     * The handler receives both form-mode ([ElicitRequestFormParams]) and URL-mode
     * ([io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestURLParams]) requests;
     * branch on `request.params` to tell them apart. For URL mode,
     * the host application must obtain explicit user consent and display the target domain before
     * navigating — the SDK never opens or validates the URL — and should return
     * [ElicitResult.Action.Decline] or [ElicitResult.Action.Cancel] when it cannot or will not proceed.
     * A URL-mode [ElicitResult.Action.Accept] only signals consent; the outcome arrives out-of-band via
     * [setElicitationCompleteHandler].
     *
     * When a form-mode handler returns [ElicitResult.Action.Accept], any properties missing from
     * [ElicitResult.content] are automatically populated with default values defined in the
     * elicitation schema. URL-mode responses carry no content.
     *
     * @param handler The elicitation handler.
     * @throws IllegalStateException if the client does not support elicitation.
     */
    public fun setElicitationHandler(handler: (ElicitRequest) -> ElicitResult) {
        checkNotNull(capabilities.elicitation) {
            logger.error { "Failed to set elicitation handler: Client does not support elicitation" }
            "Client does not support elicitation."
        }
        logger.info { "Setting the elicitation handler" }

        setRequestHandler<ElicitRequest>(Method.Defined.ElicitationCreate) { request, _ ->
            val result = handler(request)
            applyElicitationDefaults(request, result)
        }
    }

    /**
     * Sets the handler invoked when the server reports that a URL-mode elicitation has completed.
     *
     * The handler is called for every `notifications/elicitation/complete` notification. Because the
     * server only sends this for an out-of-band (URL-mode) interaction, the client must support url-mode
     * elicitation. The client is responsible for correlating the notification's `elicitationId` with a
     * pending elicitation, ignoring unknown or already-completed identifiers, and providing a manual way
     * to continue if a notification never arrives.
     *
     * @param handler Invoked with each completion notification.
     * @throws IllegalStateException if the client does not support url-mode elicitation.
     */
    public fun setElicitationCompleteHandler(handler: (ElicitationCompleteNotification) -> Unit) {
        check(capabilities.elicitation.supportsUrl) {
            logger.error {
                "Failed to set elicitation-complete handler: client does not support url-mode elicitation"
            }
            "Client does not support url-mode elicitation."
        }
        logger.info { "Setting the elicitation-complete handler" }

        setNotificationHandler<ElicitationCompleteNotification>(
            Method.Defined.NotificationsElicitationComplete,
        ) { notification ->
            handler(notification)
            CompletableDeferred(Unit)
        }
    }

    // --- Internal Handlers ---

    private fun applyElicitationDefaults(request: ElicitRequest, result: ElicitResult): ElicitResult {
        if (result.action != ElicitResult.Action.Accept) return result
        val formParams = request.params as? ElicitRequestFormParams ?: return result
        val content = result.content ?: return result

        val merged = buildMap {
            putAll(content)
            for ((key, schemaDef) in formParams.requestedSchema.properties) {
                if (key !in content) {
                    schemaDef.defaultJsonValue()?.let { put(key, it) }
                }
            }
        }

        return if (merged.size == content.size) {
            result
        } else {
            result.copy(content = JsonObject(merged))
        }
    }

    @Suppress("DEPRECATION", "CyclomaticComplexMethod")
    private fun PrimitiveSchemaDefinition.defaultJsonValue(): JsonElement? = when (this) {
        is StringSchema -> default?.let { JsonPrimitive(it) }

        is IntegerSchema -> default?.let { JsonPrimitive(it) }

        is DoubleSchema -> default?.let { JsonPrimitive(it) }

        is BooleanSchema -> default?.let { JsonPrimitive(it) }

        is UntitledSingleSelectEnumSchema -> default?.let { JsonPrimitive(it) }

        is TitledSingleSelectEnumSchema -> default?.let { JsonPrimitive(it) }

        is LegacyTitledEnumSchema -> default?.let { JsonPrimitive(it) }

        is UntitledMultiSelectEnumSchema -> default?.let { list ->
            buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
        }

        is TitledMultiSelectEnumSchema -> default?.let { list ->
            buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun handleListRoots(): ListRootsResult {
        val rootList = roots.value.values.toList()
        return ListRootsResult(rootList)
    }

    /**
     * Validates meta keys according to MCP specification.
     *
     * Key format: [prefix/]name
     * - Prefix (optional): dot-separated labels + slash
     * - Reserved prefixes contain "modelcontextprotocol" or "mcp" as complete labels
     * - Name: alphanumeric start/end, may contain hyphens, underscores, dots (empty allowed)
     */
    private fun validateMetaKeys(keys: Set<String>) {
        val labelPattern = Regex("[a-zA-Z]([a-zA-Z0-9-]*[a-zA-Z0-9])?")
        val namePattern = Regex("[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?")

        keys.forEach { key ->
            require(key.isNotEmpty()) { "Meta key cannot be empty" }

            val (prefix, name) = key.split('/', limit = 2).let { parts ->
                when (parts.size) {
                    1 -> null to parts[0]
                    2 -> parts[0] to parts[1]
                    else -> throw IllegalArgumentException("Unexpected split result for key: $key")
                }
            }

            // Validate prefix if present
            prefix?.let {
                require(it.isNotEmpty()) { "Invalid _meta key '$key': prefix cannot be empty" }

                val labels = it.split('.')
                require(labels.all { label -> label.matches(labelPattern) }) {
                    "Invalid _meta key '$key': prefix labels must start with a letter, end with letter/digit, " +
                        "and contain only letters, digits, or hyphens"
                }

                require(
                    labels.none { label ->
                        label.equals("modelcontextprotocol", ignoreCase = true) ||
                            label.equals("mcp", ignoreCase = true)
                    },
                ) {
                    "Invalid _meta key '$key': prefix cannot contain reserved labels 'modelcontextprotocol' or 'mcp'"
                }
            }

            // Validate name (empty allowed)
            require(name.isEmpty() || name.matches(namePattern)) {
                "Invalid _meta key '$key': name must start and end with alphanumeric characters, " +
                    "and contain only alphanumerics, hyphens, underscores, or dots"
            }
        }
    }

    /** An open `subscriptions/listen` request. */
    private class ActiveSubscription(private val onAcknowledged: (SubscriptionFilter) -> Unit) {
        /** Completed when the server cancels the subscription over stdio. */
        val closedByServer = CompletableDeferred<Unit>()

        private val acknowledged = atomic(false)

        fun acknowledge(filter: SubscriptionFilter) {
            if (acknowledged.compareAndSet(expect = false, update = true)) onAcknowledged(filter)
        }
    }

    /** Result of probing a server with `server/discover`. */
    private sealed interface DiscoveryOutcome {
        class Discovered(val result: DiscoverResult) : DiscoveryOutcome

        class Unsupported(val supportedVersions: List<String>) : DiscoveryOutcome

        data object HandshakeBased : DiscoveryOutcome
    }
}

private const val RESOURCE_SUBSCRIPTION_REMOVED =
    "resources/subscribe and resources/unsubscribe were removed in request-scoped protocol versions; " +
        "use Client.listen(SubscriptionFilter(resourceSubscriptions = ...)) instead"
