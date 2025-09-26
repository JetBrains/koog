package ai.koog.prompt.params

import ai.koog.prompt.llm.LLMCapability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents configuration parameters for controlling the behavior of a language model.
 *
 * @property temperature A parameter to control the randomness in the output. Higher values
 * encourage more diverse results, while lower values produce deterministically focused outputs.
 * The value is optional and defaults to null.
 *
 * @property maxTokens Maximum number of tokens to generate in the response.
 * @property numberOfChoices Specifies the number of alternative completions to generate.
 * @property speculation Reserved for speculative proposition of how result would look like,
 * supported only by a number of models, but may greatly improve speed and accuracy of result.
 * For example, in OpenAI that feature is called PredictedOutput
 * @property schema Defines the structure for the model's structured response format.
 * @property toolChoice Used to switch tool calling behavior of LLM.
 * @property user An optional identifier for the user making the request, which can be used for tracking purposes.
 * @property includeThoughts If `true`, requests the model to add reasoning blocks to the response.
 * Defaults to `null`.
 * When set to `true`, responses may include detailed reasoning steps.
 * When `false` or `null`, responses are typically shorter and faster.
 * @property thinkingBudget Hard cap for reasoning tokens.
 * Ignored by models that don't support budgets.
 * This can be used to limit the amount of tokens used for reasoning when `includeThoughts` is enabled.
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 */
@Serializable
@Suppress("LongParameterList")
public open class LLMParams(
    public val temperature: Double? = null,
    public val maxTokens: Int? = null,
    public val numberOfChoices: Int? = null,
    public val speculation: String? = null,
    public val schema: Schema? = null,
    public val toolChoice: ToolChoice? = null,
    public val user: String? = null,
    public val includeThoughts: Boolean? = null,
    public val thinkingBudget: Int? = null,
    public val additionalProperties: Map<String, JsonElement>? = null,
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
        toolChoice = toolChoice ?: default.toolChoice,
        user = user ?: default.user,
        includeThoughts = includeThoughts ?: default.includeThoughts,
        thinkingBudget = thinkingBudget ?: default.thinkingBudget,
        additionalProperties = additionalProperties ?: default.additionalProperties,
    )

    /**
     * Creates a copy of this instance with the ability to modify any of its properties.
     */
    public open fun copy(
        temperature: Double? = this.temperature,
        maxTokens: Int? = this.maxTokens,
        numberOfChoices: Int? = this.numberOfChoices,
        speculation: String? = this.speculation,
        schema: Schema? = this.schema,
        toolChoice: ToolChoice? = this.toolChoice,
        user: String? = this.user,
        includeThoughts: Boolean? = this.includeThoughts,
        thinkingBudget: Int? = this.thinkingBudget,
        additionalProperties: Map<String, JsonElement>? = this.additionalProperties,
    ): LLMParams = LLMParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        includeThoughts = includeThoughts,
        thinkingBudget = thinkingBudget,
        additionalProperties = additionalProperties,
    )

    /**
     * Component functions for destructuring declarations
     */
    public operator fun component1(): Double? = temperature

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component2(): Int? = maxTokens

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component3(): Int? = numberOfChoices

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component4(): String? = speculation

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component5(): Schema? = schema

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component6(): ToolChoice? = toolChoice

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component7(): String? = user

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component8(): Boolean? = includeThoughts

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component9(): Int? = thinkingBudget

    @Suppress("MissingKDocForPublicAPI")
    public operator fun component10(): Map<String, JsonElement>? = additionalProperties

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is LLMParams -> false
        else ->
            temperature == other.temperature &&
                maxTokens == other.maxTokens &&
                numberOfChoices == other.numberOfChoices &&
                speculation == other.speculation &&
                schema == other.schema &&
                toolChoice == other.toolChoice &&
                user == other.user &&
                includeThoughts == other.includeThoughts &&
                thinkingBudget == other.thinkingBudget &&
                additionalProperties == other.additionalProperties
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice,
        user, includeThoughts, thinkingBudget
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("LLMParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", includeThoughts=$includeThoughts")
        append(", thinkingBudget=$thinkingBudget")
        append(", additionalProperties=$additionalProperties")
        append(")")
    }

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

/**
 * Converts a variable number of pairs into a map where the values are transformed into JsonElement instances.
 *
 * @param pairs A variable number of key-value pairs, where the keys are strings and the values are any type.
 * @return A map with the provided keys associated with their corresponding JsonElement representations as values.
 */
public fun additionalPropertiesOf(vararg pairs: Pair<String, Any>): Map<String, JsonElement> =
    pairs.associate { (k, v) -> k to toJsonElement(v) }

private fun toJsonElement(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Iterable<*> -> JsonArray(v.map { toJsonElement(it) })
    is Map<*, *> -> JsonObject(v.entries.associate { (k, value) -> k.toString() to toJsonElement(value) })
    else -> JsonPrimitive(v.toString())
}
