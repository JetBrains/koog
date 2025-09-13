package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo

/**
 * Convert a [Message.Response] to a [StreamFrame].
 */
public fun Message.Response.toStreamFrame(): StreamFrame =
    when (this) {
        is Message.Assistant -> StreamFrame.Append(content)
        is Message.Tool.Call -> StreamFrame.ToolCall(id, tool, content)
    }

/**
 * Converts a list of `StreamFrame` objects into a `Message.Assistant` instance.
 * Filters only the `StreamFrame.Append` elements, concatenates their textual content and finish reasons,
 * and constructs a `Message.Assistant` object if any `Append` frames are present.
 *
 * @return A `Message.Assistant` object containing the concatenated content and finish reason
 * of all `StreamFrame.Append` elements, or `null` if the list contains no `StreamFrame.Append` elements.
 */
public fun Iterable<StreamFrame>.toAssistant(): Message.Assistant? {
    var content: String? = null
    var finishReason: String? = null
    forEach { frame ->
        when (frame) {
            is StreamFrame.Append -> content = content?.plus(frame.text) ?: frame.text
            is StreamFrame.ToolCall -> Unit
            is StreamFrame.End -> finishReason = frame.finishReason
        }
    }
    return Message.Assistant(
        content = content ?: return null,
        finishReason = finishReason,
        metaInfo = ResponseMetaInfo.Empty
    )
}
