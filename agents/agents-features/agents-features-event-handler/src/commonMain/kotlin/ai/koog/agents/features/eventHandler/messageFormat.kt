package ai.koog.agents.features.eventHandler

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
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
    get() = StringBuilder()
        .append("id: ").append(id)
        .append(", messages: [")
        .append(
            messages.joinToString { message ->
                "{${message.traceString}}"
            }
        )
        .append("]")
        .append(", ")
        .append("temperature: ").append(params.temperature)
        .toString()

internal val Message.traceString: String
    get() = StringBuilder()
        .append("role: ").append(role)
        .append(", parts: [")
        .append(parts.joinToString { part -> "{${part.traceString}}" })
        .append("]")
        .toString()

internal val MessagePart.traceString: String
    get() = when (this) {
        is MessagePart.Text -> "type: ${this::class.simpleName}, text: $text"
        is MessagePart.Attachment -> "type: ${this::class.simpleName}, source: ${this.source::class.simpleName}"
        is MessagePart.Reasoning -> "type: ${this::class.simpleName}, content: $content"
        is MessagePart.Tool.Call -> "type: ${this::class.simpleName}, tool: $tool, args: $args"
        is MessagePart.Tool.Result -> "type: ${this::class.simpleName}, tool: $tool, output: $output"
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
