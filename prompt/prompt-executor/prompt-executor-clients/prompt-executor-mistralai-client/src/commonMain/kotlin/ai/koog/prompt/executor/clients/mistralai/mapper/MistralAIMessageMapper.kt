package ai.koog.prompt.executor.clients.mistralai.mapper

import ai.koog.prompt.executor.clients.mistralai.model.MistralAIContent
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIMessage
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIMessage.*
import ai.koog.prompt.message.Message
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    private fun Message.User.toMistralAIMessage(): MistralAIMessage.MistralAIUserMessage {
        val listOfContent = buildList {
            if (content.isNotEmpty()) {
                add(MistralAIContent.ContentChunk.TextChunk(content))
            }
        }
        return MistralAIUserMessage(content = listOfContent)
    }

    private fun Message.Assistant.toMistralAIMessage(): MistralAIAssistantMessage {
        return MistralAIAssistantMessage(
            content = listOf(MistralAIContent.ContentChunk.TextChunk(content))
        )
    }

    private fun Message.System.toMistralAIMessage(): MistralAIMessage.MistralAISystemMessage {
        return MistralAISystemMessage(content = listOf(MistralAIContent.TextChunk(content)))
    }

    private fun Message.Tool.Result.toMistralAIMessage(): MistralAIToolMessage {
        return MistralAIToolMessage()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun Message.Tool.Call.toMistralAIMessage(): MistralAIToolMessage {
        return MistralAIToolMessage(
            toolCallId = id ?: Uuid.random().toString(),
            name = tool,
        )
    }
}