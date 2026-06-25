package ai.koog.prompt.dsl

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.message.CacheControl
import ai.koog.prompt.message.MessagePart

@JavaAPI
public class RequestMessagePartsBuilder : ContentPartsBuilderBase<List<MessagePart.RequestPart>>() {

    private val requestParts: MutableList<MessagePart.RequestPart> = mutableListOf()

    private fun flushContentMessageParts() {
        flushTextBuilder()
        requestParts.addAll(contentParts)
        contentParts.clear()
    }

    private fun part(part: MessagePart.RequestPart) {
        flushContentMessageParts()
        requestParts.add(part)
    }

    public fun toolResult(result: MessagePart.Tool.Result) {
        part(result)
    }

    public fun toolResult(
        id: String? = null,
        tool: String,
        output: String,
        isError: Boolean = false,
        cacheControl: CacheControl? = null,
    ) {
        part(MessagePart.Tool.Result(id, tool, output, isError, cacheControl))
    }

    override fun build(): List<MessagePart.RequestPart> {
        flushContentMessageParts()
        return requestParts
    }
}
