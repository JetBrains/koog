package ai.koog.agents.features.eventHandler

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
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
            .append(messages.joinToString(", ", prefix = "{", postfix = "}") { message -> "role: ${message.role}, message: ${message.content}" })
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
    get() {
        return "role: ${role}, message: $content"
    }

/**
 * A property that combines the provider ID and the model ID of an `LLModel` instance into a single string.
 *
 * It constructs a formatted identifier in the form of `providerId:modelId`, where:
 * - `providerId` is the unique identifier of the `LLMProvider` associated with the model.
 * - `modelId` is the unique identifier for the specific model instance.
 *
 * This property is typically used to uniquely identify an LLM instance for logging, tracing, or serialization purposes.
 */
internal val LLModel.eventString: String
    get() = "${this.provider.id}:${this.id}"