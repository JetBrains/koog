package ai.koog.prompt.streaming

import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo

/**
 * Converts a list of [Message.Response] to a list of [StreamFrame].
 * Final [StreamFrame.End] is also emitted.
 */
public fun List<Message.Response>.toStreamFrames(): List<StreamFrame> =
    flatMapIndexed { index, response -> response.toStreamFrames(index) }.plus(StreamFrame.End(null, ResponseMetaInfo.Empty))

/**
 * Converts a [Message.Response] to a list of [StreamFrame].
 * First it emits the delta frames for each content part for each message, then complete frame with the full message content.
 */
public fun Message.Response.toStreamFrames(index: Int? = null): List<StreamFrame> {
    val response = this
    return buildList {
        when (response) {
            is Message.Assistant -> {
                parts.filterIsInstance<ContentPart.Text>().forEach { add(StreamFrame.TextDelta(it.text, index)) }
                add(StreamFrame.TextComplete(content, index))
            }

            is Message.Reasoning -> {
                parts.forEach { add(StreamFrame.ReasoningDelta(it.text, null, index)) }
                summary?.forEach { add(StreamFrame.ReasoningDelta(null, it.text, index)) }
                add(
                    StreamFrame.ReasoningComplete(
                        parts.map { it.text },
                        summary?.map { it.text },
                        encrypted,
                        index
                    )
                )
            }

            is Message.Tool.Call -> {
                add(StreamFrame.ToolCallDelta(id, tool, content, index))
                add(StreamFrame.ToolCallComplete(id, tool, content, index))
            }
        }
    }
}

/**
 * Converts frames into [Message.Response] objects.
 *
 * Collects all complete frames into one [Message.Response] objects.
 *
 * @return A list of [Message.Response] objects.
 */
public fun Iterable<StreamFrame>.toMessageResponses(): List<Message.Response> {
    val textMessagesCompleteFrames = mutableListOf<StreamFrame.TextComplete>()
    val reasoningCompleteFrames = mutableListOf<StreamFrame.ReasoningComplete>()
    val toolCallCompleteFrames = mutableListOf<StreamFrame.ToolCallComplete>()
    var end: StreamFrame.End? = null

    forEach { frame ->
        when (frame) {
            is StreamFrame.TextComplete -> textMessagesCompleteFrames.add(frame)
            is StreamFrame.ReasoningComplete -> reasoningCompleteFrames.add(frame)
            is StreamFrame.ToolCallComplete -> toolCallCompleteFrames.add(frame)
            is StreamFrame.End -> end = frame
            else -> {}
        }
    }

    return buildList {
        reasoningCompleteFrames.forEach {
            add(
                Message.Reasoning(
                    parts = it.text.map { textPart -> ContentPart.Text(textPart) },
                    summary = it.summary?.map { summaryPart -> ContentPart.Text(summaryPart) },
                    encrypted = it.encrypted,
                    metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty
                )
            )
        }
        textMessagesCompleteFrames.forEach {
            add(
                Message.Assistant(
                    content = it.text,
                    finishReason = end?.finishReason,
                    metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty
                )
            )
        }
        toolCallCompleteFrames.forEach {
            add(
                Message.Tool.Call(
                    id = it.id,
                    tool = it.name,
                    content = it.content,
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

/**
 * Extracts the reasoning response from frames, if any.
 *
 * @return A [Message.Reasoning] object, or `null` if not found.
 */
public fun Iterable<StreamFrame>.toReasoningMessageOrNull(): Message.Reasoning? =
    toMessageResponses().filterIsInstance<Message.Reasoning>().singleOrNull()
