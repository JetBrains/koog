package ai.koog.agents.features.tracing

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

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
                    "role: ${message.role}, message: ${message.content}"
                }
            )
            .append("]")
            .append(", ")
            .append("temperature: ").append(params.temperature)

        return builder.toString()
    }

/**
 * Provides a formatted string representation of a `Response` message that includes its role and content.
 *
 * The string is structured as: `role: <role>, message: <content>`.
 *
 * This property is useful for logging or debugging purposes where a concise yet descriptive
 * summary of the message content and its associated role is required.
 */
internal val Message.Response.traceString: String
    get() = "role: $role, message: $content"
