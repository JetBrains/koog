package ai.koog.agents.core.agent.config

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

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
    public abstract fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt

    public fun convertMessage(message: Message): Message {
        return when (message) {
            is Message.Tool.Call -> format.describeToolCall(message)
            is Message.Tool.Result -> format.describeToolResult(message)
            else -> message
        }
    }

    /**
     * Replace all real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages.
     */
    public class All(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            return prompt.withUpdatedMessages { map { convertMessage(it) } }
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
            return prompt.withUpdatedMessages {
                map { message ->
                    if (message is Message.Tool) {
                        if (message.tool !in toolNames) {
                            return@map convertMessage(message)
                        }
                    }
                    return@map message
                }
            }
        }
    }
}
