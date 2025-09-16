package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo

/**
 * Converts a [Message.Response] to a [StreamFrame].
 */
public fun Message.Response.toStreamFrame(): StreamFrame =
    when (this) {
        is Message.Assistant -> StreamFrame.Append(content)
        is Message.Tool.Call -> StreamFrame.ToolCall(id, tool, content)
    }

/**
 * Converts frames into [Message.Response] objects.
 *
 * - Collects all assistant text (`Append`) into one [Message.Assistant].
 * - Preserves all [StreamFrame.ToolCall] as [Message.Tool.Call].
 * - Uses the last [StreamFrame.End] (if any) for finishReason/metaInfo.
 *
 * @return A list of [Message.Response] objects.
 */
public fun Iterable<StreamFrame>.toMessageResponses(): List<Message.Response> {
    var assistantContent: String? = null
    val toolCalls = mutableListOf<StreamFrame.ToolCall>()
    var end: StreamFrame.End? = null

    forEach { frame ->
        when (frame) {
            is StreamFrame.Append -> assistantContent = (assistantContent ?: "") + frame.text
            is StreamFrame.ToolCall -> toolCalls += frame
            is StreamFrame.End -> end = frame
        }
    }

    return buildList {
        toolCalls.forEach {
            add(
                Message.Tool.Call(
                    id = it.id,
                    tool = it.name,
                    content = it.content,
                    metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty
                )
            )
        }
        assistantContent?.let {
            add(
                Message.Assistant(
                    content = it,
                    finishReason = end?.finishReason,
                    metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty
                )
            )
        }
    }
}

/**
 * Extracts only tool calls from frames.
 *
 * @return A list of [Message.Tool.Call] objects.
 */
public fun Iterable<StreamFrame>.toToolCallMessages(): List<Message.Tool.Call> =
    toMessageResponses().filterIsInstance<Message.Tool.Call>()

/**
 * Extracts the assistant response from frames, if any.
 *
 * @return A [Message.Assistant] object, or `null` if not found.
 */
public fun Iterable<StreamFrame>.toAssistantMessageOrNull(): Message.Assistant? =
    toMessageResponses().filterIsInstance<Message.Assistant>().singleOrNull()
