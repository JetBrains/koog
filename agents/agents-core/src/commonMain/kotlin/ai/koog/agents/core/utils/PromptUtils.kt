package ai.koog.agents.core.utils

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message

/**
 * Escapes characters that have special meaning in XML (`<`, `>`, `&`, `"`, `'`) so that
 * arbitrary content embedded between XML-like wrapper tags cannot break out of those tags
 * or inject new ones. This is critical when wrapping untrusted content (tool results,
 * user input, web pages, etc.) in tags like `<history>` or `<compressed_facts>`: without
 * escaping, a payload containing `</history>` followed by adversarial instructions could
 * truncate the wrapper and inject prompt-level commands to the LLM.
 */
internal fun String.escapeXml(): String = buildString(length) {
    for (ch in this@escapeXml) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }
}

internal fun buildPromptAsXml(
    messages: List<Message>,
    systemInstruction: String,
    promptId: String,
    historyWrapperTag: String
): Prompt = prompt(promptId) {
    // Combine all history into one message with XML tags
    // to prevent LLM from continuing answering in a tool_call -> tool_result pattern.
    //
    // All embedded message content and tool attributes are XML-escaped to prevent
    // prompt/XML injection: an untrusted payload containing a literal closing tag
    // (e.g. `</history>`) must not be able to break out of the wrapper and inject
    // instructions into the surrounding prompt structure.
    val combinedMessage = buildString {
        append("<$historyWrapperTag>\n")
        messages.forEach { message ->
            when (message) {
                is Message.System -> append("<system>\n${message.content.escapeXml()}\n</system>\n")
                is Message.User -> append("<user>\n${message.content.escapeXml()}\n</user>\n")
                is Message.Assistant -> append("<assistant>\n${message.content.escapeXml()}\n</assistant>\n")
                is Message.Reasoning -> append("<thinking>\n${message.content.escapeXml()}\n</thinking>\n")
                is Message.Tool.Call -> append(
                    "<tool_call tool=\"${message.tool.escapeXml()}\">\n${message.content.escapeXml()}\n</tool_call>\n"
                )

                is Message.Tool.Result -> append(
                    "<tool_result tool=\"${message.tool.escapeXml()}\">\n${message.content.escapeXml()}\n</tool_result>\n"
                )
            }
        }
        append("</$historyWrapperTag>\n")
    }

    system(systemInstruction)
    user(combinedMessage)
}
