package ai.koog.agents.core.utils

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message

internal fun buildPromptAsXml(
    messages: List<Message>,
    systemInstruction: String,
    promptId: String,
    historyWrapperTag: String
): Prompt = prompt(promptId) {
    // Combine all history into one message with XML tags
    // to prevent LLM from continuing answering in a tool_call -> tool_result pattern
    val combinedMessage = buildString {
        append("<$historyWrapperTag>\n")
        messages.forEach { message ->
            when (message) {
                is Message.System -> append("<system>\n${message.content}\n</system>\n")
                is Message.User -> append("<user>\n${message.content}\n</user>\n")
                is Message.Assistant -> append("<assistant>\n${message.content}\n</assistant>\n")
                is Message.Reasoning -> append("<thinking>\n${message.content}\n</thinking>\n")
                is Message.Tool.Call -> append(
                    "<tool_call tool=${message.tool}>\n${message.content}\n</tool_call>\n"
                )

                is Message.Tool.Result -> append(
                    "<tool_result tool=${message.tool}>\n${message.content}\n</tool_result>\n"
                )
            }
        }
        append("</$historyWrapperTag>\n")
    }

    system(systemInstruction)
    user(combinedMessage)
}
