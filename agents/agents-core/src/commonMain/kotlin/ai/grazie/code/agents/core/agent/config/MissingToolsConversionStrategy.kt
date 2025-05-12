package ai.grazie.code.agents.core.agent.config

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

/**
 * Determines how the tool calls which are present in the prompt, but whose definitions are not present in the request,
 * are converted when sending to the Grazie API.
 *
 * Missing tool definitions usually occur when different sets of tools are used between stages/subgraphs,
 * and the same prompt history is used without compression.
 *
 * @property format Formatter used to convert tool calls
 */
abstract class MissingToolsConversionStrategy(val format: ToolCallDescriber) {
    abstract fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt

    fun convertMessage(message: Message): Message {
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
    class All(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            return prompt.copy(messages = prompt.messages.map { convertMessage(it) })
        }
    }

    /**
     * Replace only missing real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages. The tool calls whose definitions are not missing, will be left
     * as real tool calls and responses.
     */
    class Missing(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            val toolNames = tools.map { it.name }
            return prompt.copy(messages = prompt.messages.map { message ->
                if (message is Message.Tool) {
                    if (message.tool !in toolNames) {
                        return@map convertMessage(message)
                    }
                }
                return@map message
            })
        }
    }
}
