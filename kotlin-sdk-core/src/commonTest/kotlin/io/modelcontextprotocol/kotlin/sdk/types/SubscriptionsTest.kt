package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalMcpApi::class)
class SubscriptionsTest {

    @Test
    fun `should encode a listen request`() {
        val request = SubscriptionsListenRequest(
            SubscriptionsListenRequestParams(
                SubscriptionFilter(toolsListChanged = true, resourceSubscriptions = listOf("file:///a")),
            ),
        )

        McpJson.encodeToString<Request>(request) shouldEqualJson """
            {
              "method": "subscriptions/listen",
              "params": {"notifications": {"toolsListChanged": true, "resourceSubscriptions": ["file:///a"]}}
            }
        """.trimIndent()
    }

    @Test
    fun `should decode a listen request by method`() {
        val request = McpJson.decodeFromString<Request>(
            """{"method":"subscriptions/listen","params":{"notifications":{"promptsListChanged":true}}}""",
        )

        assertEquals(
            SubscriptionFilter(promptsListChanged = true),
            assertIs<SubscriptionsListenRequest>(request).notifications,
        )
    }

    @Test
    fun `should decode an acknowledgement with its subscription id`() {
        val notification = McpJson.decodeFromString<Notification>(
            """
            {
              "method": "notifications/subscriptions/acknowledged",
              "params": {
                "_meta": {"io.modelcontextprotocol/subscriptionId": 7},
                "notifications": {"toolsListChanged": true}
              }
            }
            """.trimIndent(),
        )

        val acknowledged = assertIs<SubscriptionsAcknowledgedNotification>(notification)
        assertEquals(SubscriptionFilter(toolsListChanged = true), acknowledged.params.notifications)
        assertEquals(RequestId(7), acknowledged.subscriptionId)
    }

    @Test
    fun `should read the subscription id of a change notification`() {
        val notification = McpJson.decodeFromString<Notification>(
            """
            {"method":"notifications/tools/list_changed",
             "params":{"_meta":{"io.modelcontextprotocol/subscriptionId":"listen-1"}}}
            """.trimIndent(),
        )

        assertEquals(RequestId("listen-1"), notification.subscriptionId)
        assertNull(ToolListChangedNotification().subscriptionId)
    }
}
