package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Converts a Prompt to a list of ChatMessage objects for the Ollama API.
 */
internal fun Prompt.toOllamaChatMessages(model: LLModel): List<OllamaChatMessageDTO> {
    val messages = this.messages
    return buildList {
        for (message in messages) {
            when (message) {
                is Message.System -> {
                    message.parts.forEach { part ->
                        add(
                            OllamaChatMessageDTO(
                                role = "system",
                                content = part.text
                            )
                        )
                    }
                }

                is Message.User -> {
                    add(message.toOllamaTextChatMessage(model))
                    message.parts.filterIsInstance<MessagePart.Tool.Result>().forEach { part ->
                        add(
                            OllamaChatMessageDTO(
                                role = "tool",
                                content = part.output,
                            )
                        )
                    }
                }

                is Message.Assistant -> {
                    message.parts.forEach { part ->
                        when (part) {
                            is MessagePart.Text -> {
                                add(
                                    OllamaChatMessageDTO(
                                        role = "assistant",
                                        content = part.text
                                    )
                                )
                            }

                            is MessagePart.Attachment -> {
                                throw NotImplementedError("Attachments in assistant messages are not supported by Ollama")
                            }

                            is MessagePart.Tool.Call -> {
                                add(
                                    OllamaChatMessageDTO(
                                        role = "assistant",
                                        content = "",
                                        toolCalls = listOf(
                                            OllamaToolCallDTO(
                                                function = OllamaToolCallDTO.Call(
                                                    name = part.tool,
                                                    arguments = part.argsJson
                                                )
                                                // Note: Ollama doesn't support tool call IDs in requests,
                                                // so we don't include the message.id here
                                            )
                                        )
                                    )
                                )
                            }

                            is MessagePart.Reasoning -> {
                                throw NotImplementedError("Reasoning is not supported by Ollama")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Message.User.toOllamaTextChatMessage(model: LLModel): OllamaChatMessageDTO {
    val text = StringBuilder()
    val images = buildList {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> {
                    text.append(part.text)
                }

                is MessagePart.Attachment -> {
                    when (val source = part.source) {
                        is AttachmentSource.Image -> {
                            require(model.supports(LLMCapability.Vision.Image)) {
                                "Model ${model.id} doesn't support images"
                            }

                            val image: String = when (val content = source.content) {
                                is AttachmentContent.Binary -> content.asBase64()
                                else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                            }

                            add(image)
                        }

                        is AttachmentSource.File -> {
                            val fileContent = when (val actualContent = source.content) {
                                is AttachmentContent.PlainText -> {
                                    actualContent.text
                                }

                                is AttachmentContent.Binary -> actualContent.asBase64()

                                else -> throw IllegalArgumentException("Unsupported file attachment content: ${source.content::class}")
                            }

                            text.append("\n\n$fileContent")
                        }

                        else -> throw IllegalArgumentException("Unsupported attachment type: $part")
                    }
                }

                else -> {
                    logger.warn { "Skipping unsupported message part: $part" }
                }
            }
        }
    }

    return OllamaChatMessageDTO(
        role = "user",
        content = text.toString(),
        images = images.takeIf { it.isNotEmpty() }
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
 * Generates a deterministic tool call ID based on the tool name and content.
 * Since Ollama doesn't provide tool call IDs in its API response, we generate
 * a consistent ID that can be used for tracking and correlation.
 *
 * @param toolName The name of the tool being called
 * @param content The serialized arguments of the tool call
 * @param index Optional index for multiple tool calls in the same message
 * @return A unique identifier for this specific tool call
 */
internal fun generateToolCallId(toolName: String, content: String, index: Int = 0): String {
    // Create a deterministic ID using tool name, content hash, and index
    val combined = "$toolName:$content:$index"
    val hashCode = combined.hashCode()

    // Format as "ollama_tool_call_" + positive hash to match common ID patterns
    return "ollama_tool_call_${hashCode.toUInt()}"
}
