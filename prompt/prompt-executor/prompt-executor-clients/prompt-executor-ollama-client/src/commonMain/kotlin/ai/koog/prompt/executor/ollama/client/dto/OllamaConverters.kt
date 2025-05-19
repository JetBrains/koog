package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.tools.json.toJSONSchema
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts LLModel to Ollama model ID string.
 */
internal fun LLModel.toOllamaModelId(): String = when (this.id) {
    OllamaModels.Meta.LLAMA_3_2.id -> "llama3.2"
    OllamaModels.Alibaba.QWQ.id -> "qwq"
    OllamaModels.Alibaba.QWEN_CODER_2_5_32B.id -> "qwen2.5-coder:32b"
    else -> error("Unknown profile ID ${this.id}")
}

/**
 * Converts a Prompt to a list of ChatMessage objects for the Ollama API.
 */
internal fun Prompt.toOllamaChatMessages(): List<OllamaChatMessageDTO> {
    val messages = mutableListOf<OllamaChatMessageDTO>()
    for (message in this.messages) {
        val converted = when (message) {
            is Message.System -> {
                OllamaChatMessageDTO(
                    role = "system",
                    content = message.content
                )
            }

            is Message.User -> OllamaChatMessageDTO(
                role = "user",
                content = message.content
            )

            is Message.Assistant -> OllamaChatMessageDTO(
                role = "assistant",
                content = message.content
            )

            is Message.Tool.Call -> {
                OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    toolCalls = listOf(
                        OllamaToolCallDTO(
                            function = OllamaToolCallDTO.Call(
                                name = message.tool,
                                arguments = Json.parseToJsonElement(message.content)
                            )
                        )
                    )
                )
            }

            is Message.Tool.Result -> {
                OllamaChatMessageDTO(
                    role = "tool",
                    content = message.content
                )
            }
        }
        messages.add(converted)
    }
    return messages
}


/**
 * Converts a ToolDescriptor to an Ollama Tool object.
 */
internal fun ToolDescriptor.toOllamaTool(): OllamaToolDTO {
    val jsonSchema = this.toJSONSchema()

    return OllamaToolDTO(
        type = "function",
        function = OllamaToolDTO.Definition(
            name = this.name,
            description = this.description,
            parameters = jsonSchema
        )
    )
}

/**
 * Extracts a tool call from a ChatMessage.
 */
internal fun OllamaChatMessageDTO.getToolCall(): Message.Tool.Call? {
    if (this.toolCalls.isNullOrEmpty()) {
        return null
    }

    val toolCall = this.toolCalls.first()
    val name = toolCall.function.name
    val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }
    val content = json.encodeToString(toolCall.function.arguments)

    return Message.Tool.Call(
        // TODO support tool call ids for Ollama
        id = "id",
        tool = name,
        content = content
    )
}
