package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The `_meta` key identifying the `subscriptions/listen` stream a notification was delivered on.
 *
 * Its value is the JSON-RPC ID of the `subscriptions/listen` request that opened the stream.
 */
@ExperimentalMcpApi
public const val SUBSCRIPTION_ID_META_KEY: String = "io.modelcontextprotocol/subscriptionId"

/**
 * The change notifications a client opts in to on a `subscriptions/listen` stream.
 *
 * Omitting a field (leaving it `null`) is equivalent to not subscribing to that notification type.
 *
 * @property toolsListChanged receive `notifications/tools/list_changed`
 * @property promptsListChanged receive `notifications/prompts/list_changed`
 * @property resourcesListChanged receive `notifications/resources/list_changed`
 * @property resourceSubscriptions receive `notifications/resources/updated` for these resource URIs;
 * replaces the former `resources/subscribe` request
 */
@Serializable
@ExperimentalMcpApi
public data class SubscriptionFilter(
    val toolsListChanged: Boolean? = null,
    val promptsListChanged: Boolean? = null,
    val resourcesListChanged: Boolean? = null,
    val resourceSubscriptions: List<String>? = null,
)

/**
 * Parameters for a `subscriptions/listen` request.
 *
 * @property notifications the notification types the client opts in to
 * @property meta optional request metadata
 */
@Serializable
@ExperimentalMcpApi
public data class SubscriptionsListenRequestParams(
    val notifications: SubscriptionFilter,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Opens a long-lived stream of server-to-client change notifications.
 *
 * The server acknowledges the stream with a [SubscriptionsAcknowledgedNotification] and then delivers
 * the opted-in notifications, each tagged with [SUBSCRIPTION_ID_META_KEY]. The request only receives a
 * response when the server ends the subscription gracefully; the client ends it by cancelling the request.
 *
 * @property params the notification filter
 */
@Serializable
@ExperimentalMcpApi
public data class SubscriptionsListenRequest(override val params: SubscriptionsListenRequestParams) :
    ClientRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.SubscriptionsListen

    /** The notification types requested by this subscription. */
    public val notifications: SubscriptionFilter
        get() = params.notifications
}

/**
 * Parameters for a `notifications/subscriptions/acknowledged` notification.
 *
 * @property notifications the subset of the requested notification types the server agreed to honor
 * @property meta notification metadata, carrying [SUBSCRIPTION_ID_META_KEY]
 */
@Serializable
@ExperimentalMcpApi
public data class SubscriptionsAcknowledgedNotificationParams(
    val notifications: SubscriptionFilter,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : NotificationParams

/**
 * Sent by the server as the first message of a `subscriptions/listen` stream, reporting which of the
 * requested notification types it agreed to honor.
 *
 * @property params the acknowledged notification filter
 */
@Serializable
@ExperimentalMcpApi
public data class SubscriptionsAcknowledgedNotification(
    override val params: SubscriptionsAcknowledgedNotificationParams,
) : ServerNotification {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsSubscriptionsAcknowledged
}

/**
 * The ID of the `subscriptions/listen` stream this notification was delivered on, or `null` when the
 * notification was not delivered on a subscription stream.
 */
@ExperimentalMcpApi
public val Notification.subscriptionId: RequestId?
    get() = params?.meta?.get(SUBSCRIPTION_ID_META_KEY)?.let { element ->
        runCatching { McpJson.decodeFromJsonElement(RequestId.serializer(), element) }.getOrNull()
    }
