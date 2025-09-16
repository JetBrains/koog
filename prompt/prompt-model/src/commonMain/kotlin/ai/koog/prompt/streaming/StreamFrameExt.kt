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
 * Converts a list of [StreamFrame] objects into a list of [Message.Response].
 * The method combines the tool-related responses produced by `toTools()` with
 * the assistant response derived from `toAssistant()`. If `toAssistant()` returns
 * null, it is excluded from the resulting list.
 *
 * @return A list of [Message.Response] instances containing both tool-generated
 * and assistant-generated responses, derived from the source [StreamFrame] objects.
 */
public fun List<StreamFrame>.toMessageResponses(): List<Message.Response> {
    return toTools() + listOfNotNull(toAssistant())
}

/**
 * Transforms a list of `StreamFrame` objects into a list of `Message.Tool.Call`.
 *
 * This function filters the input list to only include instances of `StreamFrame.ToolCall`.
 * It then groups these tool call frames by their `index` to reconstruct complete tool calls.
 * Each group of tool call frames is concatenated based on their respective fields (`id`, `tool`, and `content`)
 * to produce a list of `Message.Tool.Call` objects.
 *
 * @return A list of `Message.Tool.Call` objects, each representing a reconstructed tool call
 *         with concatenated `id`, `tool`, and `content` fields.
 */
public fun List<StreamFrame>.toTools(): List<Message.Tool.Call> =
    filterIsInstance<StreamFrame.ToolCall>()
        .map {
            Message.Tool.Call(
                id = it.id,
                tool = it.name,
                content = it.content,
                metaInfo = ResponseMetaInfo.Empty
            )
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
    return  Message.Assistant(
        content = content?:return null,
        finishReason = finishReason,
        metaInfo = ResponseMetaInfo.Empty
    )
}
