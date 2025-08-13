package ai.koog.prompt.params

import ai.koog.prompt.llm.LLMCapability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents configuration parameters for controlling the behavior of a language model.
 *
 * @property temperature A parameter to control the randomness in the output. Higher values
 * encourage more diverse results, while lower values produce deterministically focused outputs.
 * The value is optional and defaults to null.
 *
 * @property numberOfChoices Specifies the number of alternative completions to generate.
 *
 * @property speculation Reserved for speculative proposition of how result would look like,
 * supported only by a number of models, but may greatly improve speed and accuracy of result.
 * For example, in OpenAI that feature is called PredictedOutput
 *
 * @property schema Defines the structure for the model's structured response format.
 *
 * @property toolChoice Used to switch tool calling behavior of LLM.
 *
 * @property user An optional identifier for the user making the request, which can be used for tracking purposes.
 *
 * @property includeThoughts If `true`, requests the model to add reasoning blocks to the response. Defaults to `null`.
 * When set to `true`, responses may include detailed reasoning steps.
 * When `false` or `null`, responses are typically shorter and faster.
 *
 * @property thinkingBudget Hard cap for reasoning tokens. Ignored by models that don't support budgets.
 * This can be used to limit the amount of tokens used for reasoning when `includeThoughts` is enabled.
 *
 * This class also includes a nested `Builder` class to facilitate constructing instances in a more
 * customizable and incremental way.
 */
@Serializable
public data class LLMParams(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val numberOfChoices: Int? = null,
    val speculation: String? = null,
    val schema: Schema? = null,
    val toolChoice: ToolChoice? = null,
    val user: String? = null,
    val includeThoughts: Boolean? = null,
    val thinkingBudget: Int? = null,
) {
    init {
        temperature?.let { temp ->
            require(temp in 0.0..2.0) { "Temperature must be between 0.0 and 2.0, but was $temp" }
        }
        numberOfChoices?.let { choices ->
            require(choices > 0) { "Number of choices must be greater than 0, but was $choices" }
        }
        speculation?.let { spec ->
            require(spec.isNotBlank()) { "Speculation must not be empty or blank" }
        }
        user?.let { userId ->
            require(userId.isNotBlank()) { "User must not be empty or blank" }
        }
        toolChoice?.let { choice ->
            if (choice is ToolChoice.Named) {
                require(choice.name.isNotBlank()) { "Tool choice name must not be empty or blank" }
            }
        }
    }

    /**
     * Combines the parameters of the current `LLMParams` instance with the provided default `LLMParams`
     * to produce a new instance. Fields that are null in the current instance are replaced by the
     * corresponding fields from the default instance.
     *
     * @param default The default `LLMParams` instance used to fill in missing values in the current instance.
     * @return A new `LLMParams` instance with missing fields replaced by corresponding fields from the default instance.
     */
    public fun default(default: LLMParams): LLMParams = copy(
        temperature = temperature ?: default.temperature,
        maxTokens = maxTokens ?: default.maxTokens,
        numberOfChoices = numberOfChoices ?: default.numberOfChoices,
        speculation = speculation ?: default.speculation,
        schema = schema ?: default.schema,
        user = user ?: default.user,
        includeThoughts = includeThoughts ?: default.includeThoughts,
        thinkingBudget = thinkingBudget ?: default.thinkingBudget,
    )

    /**
     * Represents a schema for the structured response.
     */
    @Serializable
    public sealed interface Schema {
        /**
         * Name identifier of the schema.
         */
        public val name: String

        /**
         * Related LLM capability that has to be supported for a particular schema type.
         */
        public val capability: LLMCapability.Schema

        /**
         * Represents a schema in JSON format.
         */
        @Serializable
        public sealed interface JSON : Schema {
            /**
             * JSON schema definition as [JsonObject].
             */
            public val schema: JsonObject

            /**
             * Represents a basic JSON schema.
             * Used to specify lightweight or fundamental JSON processing capabilities.
             * This format primarily focuses on nested data definitions without advanced JSON Schema functionalities.
             *
             * @property name Name identifier for the JSON schema structure.
             * @property schema JSON schema definition as [JsonObject].
             *
             * @see [LLMCapability.Schema.JSON.Basic]
             */
            @Serializable
            public data class Basic(
                override val name: String,
                override val schema: JsonObject
            ) : JSON {
                override val capability: LLMCapability.Schema = LLMCapability.Schema.JSON.Basic

                init {
                    require(name.isNotBlank()) { "Schema name must not be empty or blank" }
                }
            }

            /**
             * Represents a standard JSON schema, according to https://json-schema.org/.
             * This format is a proper subset of the official JSON Schema specification.
             *
             * **Note**: the flavor across different LLM providers might vary, since not all of them support full JSON schemas.
             *
             * @property name Name identifier for the JSON schema structure.
             * @property schema JSON schema definition as [JsonObject].
             *
             * @see [LLMCapability.Schema.JSON.Standard]
             */
            @Serializable
            public data class Standard(
                override val name: String,
                override val schema: JsonObject
            ) : JSON {
                override val capability: LLMCapability.Schema = LLMCapability.Schema.JSON.Standard

                init {
                    require(name.isNotBlank()) { "Schema name must not be empty or blank" }
                }
            }
        }
    }

    /**
     * Used to switch tool calling behavior of LLM
     */
    @Serializable
    public sealed class ToolChoice {
        /**
         *  LLM will call the tool [name] as a response
         */
        @Serializable
        public data class Named(val name: String) : ToolChoice() {
            init {
                require(name.isNotBlank()) { "Tool choice name must not be empty or blank" }
            }
        }

        /**
         * LLM will not call tools at all, and only generate text
         */
        @Serializable
        public object None : ToolChoice()

        /**
         * LLM will automatically decide whether to call tools or to generate text
         */
        @Serializable
        public object Auto : ToolChoice()

        /**
         * LLM will only call tools
         */
        @Serializable
        public object Required : ToolChoice()
    }
}
