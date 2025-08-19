package ai.koog.prompt.executor.clients.mistralai.mapper

import ai.koog.prompt.executor.clients.mistralai.model.FunctionCall
import ai.koog.prompt.executor.clients.mistralai.model.FunctionCallArguments
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIContent
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIMessage
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIMessage.*
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIToolCall
import ai.koog.prompt.message.Message

internal object MistralAIMessageMapper {

    fun mapToMistralAIMessage(message: Message): MistralAIMessage {
        return when (message) {
            is Message.System -> message.toMistralAIMessage()
            is Message.User -> message.toMistralAIMessage()
            is Message.Assistant -> message.toMistralAIMessage()
            is Message.Tool.Result -> message.toMistralAIMessage()
            is Message.Tool.Call -> message.toMistralAIMessage()
        }
    }
}

private fun createTextContent(text: String): List<MistralAIContent.ContentChunk.TextChunk> =
    if (text.isNotEmpty()) listOf(MistralAIContent.ContentChunk.TextChunk(text)) else emptyList()


private fun Message.User.toMistralAIMessage(): MistralAIUserMessage {
    return MistralAIUserMessage(content = createTextContent(content))
}

private fun Message.Assistant.toMistralAIMessage(): MistralAIAssistantMessage {
    return MistralAIAssistantMessage(content = createTextContent(content))
}

private fun Message.System.toMistralAIMessage(): MistralAISystemMessage {
    return MistralAISystemMessage(content = listOf(MistralAIContent.TextChunk(content)))
}

private fun Message.Tool.Result.toMistralAIMessage(): MistralAIMessage {
    return MistralAIToolMessage(
        toolCallId = id,
        name = tool,
        content = createTextContent(content)
    )
}

private fun Message.Tool.Call.toMistralAIMessage(): MistralAIMessage {
    return MistralAIAssistantMessage(
        toolCalls = listOf(
            MistralAIToolCall(
                id = id,
                function = FunctionCall(
                    name = tool,
                    arguments = FunctionCallArguments.StringFunctionCallArguments(content)
                )
            )
        ),
        content = createTextContent(content)
    )
}