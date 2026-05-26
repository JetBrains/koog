@file:JvmName("StreamFrameExt")

package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmName

private val logger = KotlinLogging.logger {}

/**
 * Converts a [Message.Assistant] to a list of [StreamFrame].
 * First it emits the delta frames for each content part for each message, then complete frame with the full message content.
 */
public fun Message.Assistant.toStreamFrames(): List<StreamFrame> {
    return buildList {
        parts.forEachIndexed { index, part ->
            when (part) {
                is MessagePart.Reasoning -> {
                    part.content.forEach {
                        add(
                            StreamFrame.ReasoningDelta(
                                id = part.id,
                                text = it,
                                summary = null,
                                index = index
                            )
                        )
                    }

                    part.summary?.forEach {
                        add(
                            StreamFrame.ReasoningDelta(
                                id = part.id,
                                text = null,
                                summary = it,
                                index = index
                            )
                        )
                    }

                    add(
                        StreamFrame.ReasoningComplete(
                            id = part.id,
                            content = part.content,
                            summary = part.summary,
                            encrypted = part.encrypted,
                            index = index
                        )
                    )
                }

                is MessagePart.Text -> {
                    add(StreamFrame.TextDelta(part.text, index))
                    add(StreamFrame.TextComplete(part.text, index))
                }

                is MessagePart.Tool.Call -> {
                    add(StreamFrame.ToolCallDelta(part.id, part.tool, part.args, index))
                    add(StreamFrame.ToolCallComplete(part.id, part.tool, part.args, index))
                }

                is MessagePart.Attachment -> {
                    logger.warn { "Attachment is not supported for streaming yet" }
                }
            }
        }

        add(
            StreamFrame.End(
                finishReason = finishReason,
                metaInfo = metaInfo
            )
        )
    }
}

/**
 * Converts frames into [Message.Assistant] objects.
 *
 * Collects all complete frames into one [Message.Assistant] objects.
 *
 * @return A list of [Message.Assistant] objects.
 */
public fun Iterable<StreamFrame>.toMessageResponse(): Message.Assistant {
    var end: StreamFrame.End? = null

    val parts: List<MessagePart.ResponsePart> = mapNotNull { frame ->
        when (frame) {
            is StreamFrame.ReasoningComplete -> MessagePart.Reasoning(
                id = frame.id,
                content = frame.content,
                summary = frame.summary,
                encrypted = frame.encrypted,
            )

            is StreamFrame.TextComplete ->
                MessagePart.Text(frame.text)

            is StreamFrame.ToolCallComplete ->
                MessagePart.Tool.Call(
                    id = frame.id,
                    tool = frame.name,
                    args = Json.parseToJsonElement(frame.content).jsonObject,
                )

            is StreamFrame.End -> {
                end = frame
                null
            }

            else -> null
        }
    }

    return Message.Assistant(
        parts = parts,
        finishReason = end?.finishReason,
        metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty
    )
}
