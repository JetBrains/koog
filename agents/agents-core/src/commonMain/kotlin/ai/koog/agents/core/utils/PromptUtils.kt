package ai.koog.agents.core.utils

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart

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
                is Message.System -> {
                    val text = message.parts.joinToString("\n") { it.text }.escapeXml()
                    append("<system>\n$text\n</system>\n")
                }
                is Message.User -> {
                    val textParts = message.parts.filterIsInstance<MessagePart.Text>()
                    if (textParts.isNotEmpty()) {
                        val text = textParts.joinToString("\n") { it.text }.escapeXml()
                        append("<user>\n$text\n</user>\n")
                    }
                    message.parts.forEach { part ->
                        if (part is MessagePart.Tool.Result) {
                            append("<tool_result tool=\"${part.tool.escapeXml()}\">\n${part.output.escapeXml()}\n</tool_result>\n")
                        }
                    }
                }
                is Message.Assistant -> {
                    val textParts = message.parts.filterIsInstance<MessagePart.Text>()
                    if (textParts.isNotEmpty()) {
                        val text = textParts.joinToString("\n") { it.text }.escapeXml()
                        append("<assistant>\n$text\n</assistant>\n")
                    }
                    message.parts.forEach { part ->
                        when (part) {
                            is MessagePart.Tool.Call -> append("<tool_call tool=\"${part.tool.escapeXml()}\">\n${part.args.escapeXml()}\n</tool_call>\n")
                            is MessagePart.Reasoning -> append("<thinking>\n${part.content.joinToString("\n").escapeXml()}\n</thinking>\n")
                            else -> {}
                        }
                    }
                }
            }
        }
        append("</$historyWrapperTag>\n")
    }

    system(systemInstruction)
    user(combinedMessage)
}
