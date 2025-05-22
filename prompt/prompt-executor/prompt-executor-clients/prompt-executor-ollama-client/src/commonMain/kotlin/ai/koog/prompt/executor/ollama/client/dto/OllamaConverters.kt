package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.tools.json.toJSONSchema
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Extracts a JSON schema format from the prompt, if one is defined.
 */
internal fun Prompt.toOllamaJsonFormat(): JsonObject? {
    val schema = params.schema
    return if (schema is LLMParams.Schema.JSON) schema.schema else null
}

/**
 * Extracts some model options from the prompt, if temperature is defined.
 */
internal fun Prompt.toOllamaOptions(): OllamaChatRequestDTO.Options? {
    val temperature = this.params.temperature
    return if (temperature != null) OllamaChatRequestDTO.Options(temperature) else null
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
