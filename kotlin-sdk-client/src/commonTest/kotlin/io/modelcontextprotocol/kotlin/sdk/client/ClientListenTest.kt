package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.SUBSCRIPTION_ID_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionFilter
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionsAcknowledgedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SubscriptionsAcknowledgedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.subscriptionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMcpApi::class)
class ClientListenTest {

    private val filter = SubscriptionFilter(toolsListChanged = true, resourceSubscriptions = listOf("file:///a"))

    private fun subscriptionMeta(request: JSONRPCRequest): JsonObject =
        buildJsonObject { put(SUBSCRIPTION_ID_META_KEY, McpJson.encodeToJsonElement(request.id)) }

    /** A request-scoped server that acknowledges each listen request, sends one change, and keeps it open. */
    private fun server(listenRequests: CompletableDeferred<JSONRPCRequest>) = FakeMcpServer { request ->
        when (request.method) {
            "server/discover" -> respondDiscover(request)

            "subscriptions/listen" -> {
                val meta = subscriptionMeta(request)
                notify(
                    SubscriptionsAcknowledgedNotification(
                        SubscriptionsAcknowledgedNotificationParams(SubscriptionFilter(toolsListChanged = true), meta),
                    ),
                )
                notify(ToolListChangedNotification(BaseNotificationParams(meta)))
                listenRequests.complete(request)
            }

            else -> respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND)
        }
    }

    private suspend fun connectedClient(server: FakeMcpServer) =
        Client(Implementation("test-client", "1.0.0")).apply { connect(server.clientTransport) }

    @Test
    fun `should acknowledge and deliver notifications until the server responds`() = runTest {
        withContext(Dispatchers.Default) {
            val listenRequest = CompletableDeferred<JSONRPCRequest>()
            val server = server(listenRequest).apply { start() }
            val client = connectedClient(server)
            val acknowledged = CompletableDeferred<SubscriptionFilter>()
            val changed = CompletableDeferred<ToolListChangedNotification>()
            client.setNotificationHandler<ToolListChangedNotification>(
                Method.Defined.NotificationsToolsListChanged,
            ) { notification ->
                changed.complete(notification)
                CompletableDeferred(Unit)
            }

            val listening = async { client.listen(filter) { acknowledged.complete(it) } }

            withTimeout(5.seconds) {
                val request = listenRequest.await()
                McpJson.decodeFromJsonElement<SubscriptionFilter>(
                    request.paramsObject.getValue("notifications"),
                ) shouldBe filter
                request.meta["io.modelcontextprotocol/protocolVersion"] shouldBe JsonPrimitive("2026-07-28")
                acknowledged.await() shouldBe SubscriptionFilter(toolsListChanged = true)
                changed.await().subscriptionId shouldBe request.id

                server.respond(request, EmptyResult(meta = subscriptionMeta(request)))
                listening.await()
            }
            client.close()
        }
    }

    @Test
    fun `should cancel the listen request when the caller is cancelled`() = runTest {
        withContext(Dispatchers.Default) {
            val listenRequest = CompletableDeferred<JSONRPCRequest>()
            val server = server(listenRequest).apply { start() }
            val client = connectedClient(server)

            val listening = launch { client.listen(filter) }
            val request = withTimeout(5.seconds) { listenRequest.await() }
            listening.cancel()
            listening.join()

            withTimeout(5.seconds) {
                while (server.notifications("notifications/cancelled").isEmpty()) kotlinx.coroutines.yield()
            }
            val cancelled = server.notifications("notifications/cancelled").single()
            McpJson.decodeFromJsonElement<CancelledNotificationParams>(cancelled.params!!).requestId shouldBe
                request.id
            client.close()
        }
    }

    @Test
    fun `should end when the server cancels the subscription`() = runTest {
        withContext(Dispatchers.Default) {
            val listenRequest = CompletableDeferred<JSONRPCRequest>()
            val server = server(listenRequest).apply { start() }
            val client = connectedClient(server)

            val listening = async { client.listen(filter) }
            val request = withTimeout(5.seconds) { listenRequest.await() }
            server.notify(
                CancelledNotification(CancelledNotificationParams(requestId = request.id, reason = "shutdown")),
            )

            withTimeout(5.seconds) { listening.await() }
            client.close()
        }
    }

    @Test
    fun `should subscribe resources with a handshake-based server and unsubscribe when cancelled`() = runTest {
        withContext(Dispatchers.Default) {
            val server = FakeMcpServer { request ->
                when (request.method) {
                    "initialize" -> respondInitialize(request)
                    "resources/subscribe", "resources/unsubscribe" -> respond(request, EmptyResult())
                    else -> respondError(request, RPCError.ErrorCode.METHOD_NOT_FOUND)
                }
            }.apply { start() }
            val client = connectedClient(server)
            val acknowledged = CompletableDeferred<SubscriptionFilter>()

            val listening = launch { client.listen(filter) { acknowledged.complete(it) } }
            withTimeout(5.seconds) { acknowledged.await() } shouldBe filter
            listening.cancel()
            listening.join()

            server.requests("subscriptions/listen") shouldHaveSize 0
            server.requests("resources/subscribe") shouldHaveSize 1
            server.requests("resources/unsubscribe") shouldHaveSize 1
            client.close()
        }
    }
}
