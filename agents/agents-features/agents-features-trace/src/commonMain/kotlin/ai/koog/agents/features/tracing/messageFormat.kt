package ai.koog.agents.features.tracing

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart

/**
 * Constructs a string representation of the `Prompt` object, detailing its unique identifier,
 * messages, and temperature parameter.
 *
 * This property is primarily intended for debugging or logging purposes, offering a concise overview of
 * the `Prompt` object's state.
 */
internal val Prompt.traceString: String
    get() {
        val builder = StringBuilder()
            .append("id: ").append(id)
            .append(", messages: [")
            .append(
                messages.joinToString(", ", prefix = "{", postfix = "}") { message ->
                    message.traceString
                }
            )
            .append("]")
            .append(", ")
            .append("temperature: ").append(params.temperature)

        return builder.toString()
    }

internal val Message.traceString: String
    get() = "role: $role, parts: ${parts.joinToString(", ") { it.traceString }}"

internal val MessagePart.traceString: String
    get() = when (this) {
        is MessagePart.Text -> "type: ${this::class.simpleName}, text: $text"
        is MessagePart.Attachment -> "type: ${this::class.simpleName}, source: ${this.source::class.simpleName}"
        is MessagePart.Reasoning -> "type: ${this::class.simpleName}, content: $content"
        is MessagePart.Tool.Call -> "type: ${this::class.simpleName}, tool: $tool, args: $args"
        is MessagePart.Tool.Result -> "type: ${this::class.simpleName}, tool: $tool, output: $output"
    }
