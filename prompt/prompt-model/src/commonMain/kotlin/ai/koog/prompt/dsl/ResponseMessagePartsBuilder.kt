package ai.koog.prompt.dsl

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.text.TextContentBuilderBase
import kotlinx.serialization.json.JsonObject

@JavaAPI
public class ResponseMessagePartsBuilder : TextContentBuilderBase<List<MessagePart.ResponsePart>>() {

    private val responseParts: MutableList<MessagePart.ResponsePart> = mutableListOf()

    private fun flushContentMessageParts() {
        if (textBuilder.isNotEmpty()) {
            responseParts.add(MessagePart.Text(textBuilder.toString()))
            textBuilder.clear()
        }
    }

    private fun part(part: MessagePart.ResponsePart) {
        flushContentMessageParts()
        responseParts.add(part)
    }

    public fun reasoning(thought: MessagePart.Reasoning) {
        part(thought)
    }

    public fun reasoning(
        id: String? = null,
        content: String,
        summary: String? = null,
        encrypted: String? = null,
    ) {
        part(MessagePart.Reasoning(content = content, summary = summary?.let { listOf(it) }, encrypted = encrypted, id = id))
    }

    public fun toolCall(call: MessagePart.Tool.Call) {
        part(call)
    }

    public fun toolCall(
        id: String? = null,
        tool: String,
        args: String,
    ) {
        part(
            MessagePart.Tool.Call(
                id = id,
                tool = tool,
                args = args
            )
        )
    }

    public fun toolCall(
        id: String? = null,
        tool: String,
        args: JsonObject,
    ) {
        part(
            MessagePart.Tool.Call(
                id = id,
                tool = tool,
                args = args
            )
        )
    }

    override fun build(): List<MessagePart.ResponsePart> {
        flushContentMessageParts()
        return responseParts
    }
}
