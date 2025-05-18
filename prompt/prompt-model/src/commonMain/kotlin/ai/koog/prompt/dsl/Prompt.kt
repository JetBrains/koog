package ai.koog.prompt.dsl

import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.Schema
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.serialization.Serializable


/**
 * Represents a structured prompt for an LLM containing a list of messages, an identifier, and optional
 * language model parameters.
 *
 * @property messages A list of `Message` objects representing the conversation or content
 * encapsulated by this prompt.
 * @property id A unique identifier for the prompt, which can be used for tracking or categorization.
 * @property params Configuration parameters (`LLMParams`) that control the behavior of the language model
 * when processing this prompt. Defaults to an instance of `LLMParams` with default settings.
 */
@Serializable
public data class Prompt(
    val messages: List<Message>,
    val id: String,
    val params: LLMParams = LLMParams()
) {

    /**
     * Companion object for the `Prompt` class, providing utilities and constants for creating instances of `Prompt`.
     */
    public companion object {
        /**
         * Represents an empty state for a [Prompt] object. This variable is initialized
         * with an empty list for the prompt's options and an empty string as the prompt's message.
         *
         * The `Empty` value can be used as a default or placeholder for scenarios
         * where no meaningful data or prompt has been provided.
         */
        public val Empty: Prompt = Prompt(emptyList(), "")

        /**
         * Builds a `Prompt` object using the specified identifier, parameters, and initialization logic.
         *
         * @param id The unique identifier for the `Prompt` being built.
         * @param params The configuration parameters for the `Prompt` with a default value of `LLMParams()`.
         * @param init The initialization logic applied to the `PromptBuilder`.
         * @return The constructed `Prompt` object.
         */
        public fun build(id: String, params: LLMParams = LLMParams(), init: PromptBuilder.() -> Unit): Prompt {
            val builder = PromptBuilder(id, params)
            builder.init()
            return builder.build()
        }

        /**
         * Constructs a new [Prompt] instance by applying the provided initialization logic to a [PromptBuilder].
         *
         * @param prompt The base [Prompt] used for initializing the [PromptBuilder].
         * @param init The initialization block applied to configure the [PromptBuilder].
         * @return A new [Prompt] instance configured with the specified initialization logic.
         */
        public fun build(prompt: Prompt, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt).also(init).build()
        }
    }

    /**
     * Creates a copy of the current Prompt instance with updated messages.
     *
     * @param newMessages A list of Message instances that will replace the current messages in the Prompt.
     * @return A new Prompt instance with the updated list of messages.
     */
    public fun withMessages(newMessages: List<Message>): Prompt = copy(messages = newMessages)

    /**
     * Creates a copy of the `Prompt` with updated messages, allowing modifications to the current messages.
     *
     * @param update A lambda function that operates on a mutable list of `Message` to apply modifications.
     * @return A new `Prompt` instance with the modified list of messages.
     */
    public fun withUpdatedMessages(update: MutableList<Message>.() -> Unit): Prompt =
        this.copy(messages = messages.toMutableList().apply { update() })

    /**
     * Returns a new instance of the `Prompt` class with updated language model parameters.
     *
     * @param newParams the new `LLMParams` to use for the updated prompt.
     * @return a new `Prompt` instance with the specified parameters applied.
     */
    public fun withParams(newParams: LLMParams): Prompt = copy(params = newParams)

    /**
     * Represents a mutable context for updating the parameters of an LLM (Language Learning Model).
     * The class is used internally to facilitate changes to various configurations, such as temperature,
     * speculation, schema, and tool choice, before converting back to an immutable `LLMParams` instance.
     *
     * @property temperature The temperature value that adjusts randomness in the model's output. Higher values
     * produce diverse results, while lower values yield deterministic responses. This property is mutable
     * to allow updates during the context's lifecycle.
     *
     * @property speculation A speculative configuration string that influences model behavior, designed to
     * enhance result speed and accuracy. This property is mutable for modifying the speculation setting.
     *
     * @property schema A schema configuration that describes the structure of the output. This can include JSON-based
     * schema definitions for fine-tuned output generation. This property is mutable for schema updates.
     *
     * @property toolChoice Defines the behavior of the LLM regarding tool usage, allowing choices such as
     * automatic tool invocations or restricted tool interactions. This property is mutable to enable reconfiguration.
     */
    public class LLMParamsUpdateContext internal constructor(
        public var temperature: Double?,
        public var speculation: String?,
        public var schema: Schema?,
        public var toolChoice: ToolChoice?,
    ) {
        /**
         * Secondary constructor for `LLMParamsUpdateContext` that initializes the context using an
         * existing `LLMParams` instance.
         *
         * @param params An instance of `LLMParams` containing the configuration parameters to be
         * initialized in the `LLMParamsUpdateContext`.
         */
        internal constructor(params: LLMParams) : this(
            params.temperature,
            params.speculation,
            params.schema,
            params.toolChoice
        )

        /**
         * Converts the current context of parameters into an instance of [LLMParams].
         *
         * @return A new instance of [LLMParams] populated with the values of the current context,
         * including temperature, speculation, schema, and toolChoice options.
         */
        public fun toParams(): LLMParams = LLMParams(
            temperature = temperature,
            speculation = speculation,
            schema = schema,
            toolChoice = toolChoice
        )
    }

    /**
     * Creates a new instance of `Prompt` with updated parameters based on the modifications provided
     * in the given update lambda. The update is applied to a mutable context representing the current
     * LLM parameters, allowing selective modifications, which are then returned as a new set of parameters.
     *
     * @param update A lambda function that receives an instance of `LLMParamsUpdateContext`, allowing
     *               modification of the current parameters such as temperature, speculation, schema,
     *               and tool choice.
     * @return A new `Prompt` instance with the updated parameters.
     */
    public fun withUpdatedParams(update: LLMParamsUpdateContext.() -> Unit): Prompt =
        copy(params = LLMParamsUpdateContext(params).apply { update() }.toParams())
}
