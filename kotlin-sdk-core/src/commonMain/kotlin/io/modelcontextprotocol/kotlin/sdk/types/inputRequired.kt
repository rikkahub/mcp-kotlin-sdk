package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Name of the discriminator field every request-scoped result carries. */
internal const val RESULT_TYPE_KEY: String = "resultType"

/** [RESULT_TYPE_KEY] value of an [InputRequiredResult]. */
internal const val INPUT_REQUIRED_RESULT_TYPE: String = "input_required"

/** Request parameter carrying the client's answers to an [InputRequiredResult.inputRequests] map. */
internal const val INPUT_RESPONSES_KEY: String = "inputResponses"

/** Request parameter echoing [InputRequiredResult.requestState] back to the server. */
internal const val REQUEST_STATE_KEY: String = "requestState"

/**
 * An interim result telling the client that more input is needed before the request can complete
 * (Multi Round-Trip Requests, SEP-2322).
 *
 * The client fulfils every entry of [inputRequests] and retries the original request as a new
 * JSON-RPC request carrying the answers in `inputResponses` (keyed like [inputRequests]) and the
 * unmodified [requestState]. Servers may only return this result for `tools/call`, `prompts/get`, and
 * `resources/read`.
 *
 * @property inputRequests server-assigned keys mapped to the requests the client must fulfil; each is
 * an `elicitation/create`, `sampling/createMessage`, or `roots/list` request
 * @property requestState opaque server state that the client must echo back verbatim on the retry
 * @property meta optional result metadata
 */
@Serializable
@ExperimentalMcpApi
public data class InputRequiredResult(
    val inputRequests: Map<String, Request>? = null,
    val requestState: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult {
    /** Always `"input_required"`. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val resultType: String = INPUT_REQUIRED_RESULT_TYPE
}
