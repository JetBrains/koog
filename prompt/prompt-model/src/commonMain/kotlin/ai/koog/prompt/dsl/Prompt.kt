package ai.koog.prompt.dsl

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.Schema
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration

/**
 * Represents a data structure for a prompt, consisting of a list of messages, a unique identifier,
 * and optional parameters for language model settings.
 *
 * @property messages The list of [Message] objects associated with the prompt.
 * @property id The unique identifier for the prompt.
 * @property params The language model parameters associated with the prompt. Defaults to [LLMParams].
 */
// FIXME move it from dsl package up to the module root package?
@Serializable
public data class Prompt @JvmOverloads constructor(
    val messages: List<Message>,
    val id: String,
    val params: LLMParams = LLMParams()
) {

    /**
     * Companion object for the `Prompt` class, providing utilities and constants for creating instances of `Prompt`.
     */
    public companion object {
        /**
         * Constructs a new `PromptBuilder` instance for creating and configuring a `Prompt`.
         *
         * @param id The unique identifier for the prompt.
         * @param clock The clock used for timestamping or time-related operations. Defaults to `Clock.System` if not provided.
         * @return A new instance of `PromptBuilder` with the specified ID and clock.
         */
        @JvmStatic
        @JvmOverloads
        @JavaAPI
        public fun builder(id: String, clock: Clock = Clock.System): PromptBuilder = PromptBuilder(id, clock = clock)

        /**
         * Represents an empty state for a [Prompt] object. This variable is initialized
         * with an empty list for the prompt's options and an empty string as the prompt's message.
         *
         * The `Empty` value can be used as a default or placeholder for scenarios
         * where no meaningful data or prompt has been provided.
         */
        @JvmField
        public val Empty: Prompt = Prompt(emptyList(), "")

        /**
         * Builds a `Prompt` object using the specified identifier, parameters, and initialization logic.
         *
         * @param id The unique identifier for the `Prompt` being built.
         * @param params The configuration parameters for the `Prompt` with a default value of `LLMParams()`.
         * @param clock The clock to use for generating timestamps, defaults to Clock.System.
         * @param init The initialization logic applied to the `PromptBuilder`.
         * @return The constructed `Prompt` object.
         */
        @JvmOverloads
        public fun build(
            id: String,
            params: LLMParams = LLMParams(),
            clock: Clock = Clock.System,
            init: PromptBuilder.() -> Unit
        ): Prompt {
            val builder = PromptBuilder(id, params, clock)
            builder.init()
            return builder.build()
        }

        /**
         * Constructs a new [Prompt] instance by applying the provided initialization logic to a [PromptBuilder].
         *
         * @param prompt The base [Prompt] used for initializing the [PromptBuilder].
         * @param clock The clock to use for generating timestamps, defaults to Clock.System.
         * @param init The initialization block applied to configure the [PromptBuilder].
         * @return A new [Prompt] instance configured with the specified initialization logic.
         */
        public fun build(prompt: Prompt, clock: Clock = Clock.System, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt, clock).also(init).build()
        }
    }

    /**
     * Represents the total token usage of the most recent response message in the current prompt.
     *
     * This value is determined by iterating through the list of `messages` within the prompt and locating
     * the last message that is of type `Message.Response`. If found, the `tokensCount` from its metadata
     * is returned. If no response message exists, the value defaults to 0.
     *
     * Useful for tracking the token count of the most recently generated LLM response in the LLM chat flow.
     */
    @get:JvmName("latestTokenUsage")
    public val latestTokenUsage: Int
        get() = messages
            .lastOrNull { it is Message.Response }
            ?.let { it as? Message.Response }
            ?.metaInfo?.totalTokensCount ?: 0

    /**
     * Represents the total time spent across all messages within the prompt (measured in milliseconds)
     *
     * This property calculates the difference between the timestamp of the first
     * message and the timestamp of the last message in the list of `messages`.
     *
     * If no messages are present, the total time spent is `0`.
     */
    @get:JvmName("totalTimeSpent")
    public val totalTimeSpent: Duration
        get() = when {
            messages.isEmpty() -> Duration.ZERO
            else -> messages.last().metaInfo.timestamp - messages.first().metaInfo.timestamp
        }

    /**
     * Creates a copy of the `Prompt` with updated messages, allowing to modify the existing list of messages or provide a new one.
     *
     * @param update A lambda function that returns the new list of messages.
     * @return A new `Prompt` instance with the modified list of messages.
     */
    public fun withMessages(update: (List<Message>) -> List<Message>): Prompt =
        this.copy(messages = update(this.messages))

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
     *
     * @property user An optional user identifier that can be used for tracking or personalization purposes. This property
     * is mutable to allow updates to the user context.
     *
     */
    public class LLMParamsUpdateContext internal constructor(
        public var temperature: Double?,
        public var speculation: String?,
        public var schema: Schema?,
        public var toolChoice: ToolChoice?,
        public var user: String? = null,
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
            params.toolChoice,
            params.user,
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
            toolChoice = toolChoice,
            user = user
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
