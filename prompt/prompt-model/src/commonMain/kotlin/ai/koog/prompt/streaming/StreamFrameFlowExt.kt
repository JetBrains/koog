package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Signals that a streaming response terminated without receiving a [StreamFrame.End] frame.
 *
 * This typically indicates a dropped network connection during SSE streaming,
 * where the underlying transport (e.g., Ktor SSE) completes the flow normally
 * instead of propagating an error.
 */
public class IncompleteStreamException(
    message: String = "Stream completed without receiving an End frame",
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Ensures that the stream contains a [StreamFrame.End] frame before completing.
 *
 * If the flow completes normally without emitting a [StreamFrame.End] frame,
 * an [IncompleteStreamException] is thrown. This detects dropped connections
 * during SSE streaming where the transport silently completes the flow.
 *
 * If the flow already terminates with an exception, that exception is propagated
 * without additionally throwing [IncompleteStreamException].
 */
public fun Flow<StreamFrame>.requireEndFrame(): Flow<StreamFrame> {
    var endFrameReceived = false
    return onEach { frame -> if (frame is StreamFrame.End) endFrameReceived = true }
        .onCompletion { error ->
            if (error == null && !endFrameReceived) {
                throw IncompleteStreamException()
            }
        }
}

/**
 * Returns a transformed flow of [StreamFrame.TextDelta] objects that contains only the textual content.
 */
public fun Flow<StreamFrame>.filterTextOnly(): Flow<String> =
    filterIsInstance<StreamFrame.TextDelta>()
        .map { frame -> frame.text }

/**
 * Collects the textual content of a [Flow] of [StreamFrame] objects and returns it as a single string.
 */
public suspend fun Flow<StreamFrame>.collectText(): String =
    filterTextOnly().fold("") { acc, s -> acc + s }
