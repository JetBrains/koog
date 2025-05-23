package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.tools.json.toJSONSchema
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
                            // Note: Ollama doesn't support tool call IDs in requests,
                            // so we don't include the message.id here
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
 * Extracts a JSON schema format from the prompt, if one is defined.
 */
internal fun Prompt.extractOllamaJsonFormat(): JsonObject? {
    val schema = params.schema
    return if (schema is LLMParams.Schema.JSON) schema.schema else null
}

/**
 * Extracts options from the prompt, if temperature is defined.
 */
internal fun Prompt.extractOllamaOptions(): OllamaChatRequestDTO.Options? {
    val temperature = params.temperature
    return temperature?.let { OllamaChatRequestDTO.Options(temperature = temperature) }
}

/**
 * Extracts tool calls from a ChatMessage.
 * Returns the first tool call for compatibility, but logs if multiple calls exist.
 */
public fun OllamaChatMessageDTO.getFirstToolCall(): Message.Tool.Call? {
    if (this.toolCalls.isNullOrEmpty()) {
        return null
    }

    val toolCall = this.toolCalls.firstOrNull() ?: return null

    val name = toolCall.function.name
    val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }
    val content = json.encodeToString(toolCall.function.arguments)

    return Message.Tool.Call(
        // Generate a deterministic ID based on tool name and arguments
        // Ollama doesn't provide tool call IDs, so we create one based on content
        id = generateToolCallId(name, content),
        tool = name,
        content = content
    )
}

/**
 * Extracts all tool calls from a ChatMessage.
 * Use this method when you need to handle multiple simultaneous tool calls.
 */
internal fun OllamaChatMessageDTO.getToolCalls(): List<Message.Tool.Call> {
    if (this.toolCalls.isNullOrEmpty()) {
        return emptyList()
    }

    return this.toolCalls.mapIndexed { index, toolCall ->
        val name = toolCall.function.name
        val content = Json.encodeToString(toolCall.function.arguments)

        Message.Tool.Call(
            id = generateToolCallId(name, content, index),
            tool = name,
            content = content
        )
    }
}

/**
 * Generates a deterministic tool call ID based on the tool name and content.
 * Since Ollama doesn't provide tool call IDs in its API response, we generate
 * a consistent ID that can be used for tracking and correlation.
 *
 * @param toolName The name of the tool being called
 * @param content The serialized arguments of the tool call
 * @param index Optional index for multiple tool calls in the same message
 * @return A unique identifier for this specific tool call
 */
private fun generateToolCallId(toolName: String, content: String, index: Int = 0): String {
    // Create a deterministic ID using tool name, content hash, and index
    val combined = "$toolName:$content:$index"
    val hashCode = combined.hashCode()

    // Format as "ollama_tool_call_" + positive hash to match common ID patterns
    return "ollama_tool_call_${hashCode.toUInt()}"
}
