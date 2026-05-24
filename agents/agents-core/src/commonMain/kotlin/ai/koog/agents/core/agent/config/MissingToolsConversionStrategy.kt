package ai.koog.agents.core.agent.config

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart

/**
 * Determines how the tool calls which are present in the prompt, but whose definitions are not present in the request,
 * are converted when sending to the Model.
 *
 * Missing tool definitions usually occur when different sets of tools are used between stages/subgraphs,
 * and the same prompt history is used without compression.
 *
 * @property format Formatter used to convert tool calls
 */
public abstract class MissingToolsConversionStrategy(private val format: ToolCallDescriber) {
    /**
     * Converts a given [Prompt] by applying modifications based on the provided list of [ToolDescriptor].
     *
     * @param prompt The original [Prompt] to be converted.
     * @param tools The list of [ToolDescriptor] used to modify or adapt the [Prompt].
     * @return A new [Prompt] instance with applied changes based on the provided tools.
     */
    public abstract fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt

    /**
     * Converts the given message by formatting specific types of tool-related messages
     * (e.g., `MessagePart.Tool.Call` and `MessagePart.Tool.Result`) into descriptive messages.
     * If the message is not a tool-related message, it remains unchanged.
     *
     * @param message The input message to be converted.
     * @param toolNames The list of tool names used to determine which tools are present in the call.
     * If not, the message part should be converted.
     * @return The converted message, either modified if it's a tool-related message, or unchanged otherwise.
     */
    public fun convertMessage(message: Message, toolNames: List<String>): Message {
        when (message) {
            is Message.User -> {
                return message.copy(
                    parts = message.parts.map { part ->
                        when (part) {
                            is MessagePart.Tool.Result -> {
                                if (part.tool in toolNames) {
                                    part
                                } else {
                                    MessagePart.Text(format.describeToolResult(part))
                                }
                            }
                            else -> part
                        }
                    }
                )
            }
            is Message.Assistant -> {
                return message.copy(
                    parts = message.parts.map { part ->
                        when (part) {
                            is MessagePart.Tool.Call -> {
                                if (part.tool in toolNames) {
                                    part
                                } else {
                                    MessagePart.Text(format.describeToolCall(part))
                                }
                            }
                            else -> part
                        }
                    }
                )
            }
            is Message.System -> return message
        }
    }

    /**
     * Replace all real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages.
     */
    public class All(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            return prompt.withMessages { messages -> messages.map { convertMessage(it, emptyList()) } }
        }
    }

    /**
     * Replace only missing real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages. The tool calls whose definitions are not missing, will be left
     * as real tool calls and responses.
     */
    public class Missing(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            val toolNames = tools.map { it.name }
            return prompt.withMessages { messages ->
                messages.map { message ->
                    convertMessage(message, toolNames)
                }
            }
        }
    }
}
