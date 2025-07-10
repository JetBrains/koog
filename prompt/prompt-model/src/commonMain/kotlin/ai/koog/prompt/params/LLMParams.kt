package ai.koog.prompt.params

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents configuration parameters for controlling the behavior of a language model.
 *
 * @property temperature A parameter to control the randomness in the output. Higher values
 * encourage more diverse results, while lower values produce deterministically focused outputs.
 * The value is optional and defaults to null.
 *
 * @property speculation Reserved for speculative proposition of how result would look like,
 * supported only by a number of models, but may greatly improve speed and accuracy of result.
 * For example, in OpenAI that feature is called PredictedOutput
 *
 * @property toolChoice Used to switch tool calling behavior of LLM.
 *
 * @property user An optional identifier for the user making the request, which can be used for tracking purposes.
 *
 * This class also includes a nested `Builder` class to facilitate constructing instances in a more
 * customizable and incremental way.
 */
@Serializable
public data class LLMParams(
    val temperature: Double? = null,
    val numberOfChoices: Int? = null,
    val speculation: String? = null,
    val schema: Schema? = null,
    val toolChoice: ToolChoice? = null,
    val user: String? = null,
) {
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
        numberOfChoices = numberOfChoices ?: default.numberOfChoices,
        speculation = speculation ?: default.speculation,
        schema = schema ?: default.schema,
        user = user ?: default.user,
    )

    /**
     * Represents a generic schema for structured data, defining a common contract
     * for schemas.
     * This is a sealed interface, enabling a restrictive set of implementations.
     */
    @Serializable
    public sealed interface Schema {
        /**
         * Represents a person's name as a string.
         * This variable is intended to store the full name or a specific format of a name.
         */
        public val name: String

        /**
         * Represents a sealed interface JSON that defines a schema entity.
         * It extends the Schema interface and has a property for schema representation.
         */
        @Serializable
        public sealed interface JSON: Schema {
            /**
             * Represents the JSON schema definition as a JsonObject.
             *
             * This property is used to store and define the structure or format of a JSON-based data schema,
             * enabling serialization, validation, and adherence to a specific format. It is commonly utilized
             * within implementations that require a structured schema for processing or validating JSON data.
             */
            public val schema: JsonObject

            /**
             * Represents a simplified JSON structure with a schema definition.
             *
             * This data class implements the `JSON` interface and provides a basic representation
             * of a JSON structure using a `name` and its corresponding `schema` in the form of a `JsonObject`.
             *
             * Use this class when a lightweight, minimal representation of a JSON schema is sufficient.
             *
             * @property name The identifier or name of the JSON structure.
             * @property schema The JSON schema associated with the structure.
             */
            @Serializable
            public data class Simple(override val name: String, override val schema: JsonObject) : JSON
            /**
             * Represents a complete JSON schema structure.
             *
             * This data class implements the `JSON` interface and provides a representation
             * for a fully described JSON schema object, including its associated name and schema data.
             *
             * @property name The name identifier for the JSON schema structure.
             * @property schema The JSON schema definition as a `JsonObject`.
             */
            @Serializable
            public data class Full(override val name: String, override val schema: JsonObject) : JSON
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
        public data class Named(val name: String): ToolChoice()

        /**
         * LLM will not call tools at all, and only generate text
         */
        @Serializable
        public object None: ToolChoice()

        /**
         * LLM will automatically decide whether to call tools or to generate text
         */
        @Serializable
        public object Auto: ToolChoice()

        /**
         * LLM will only call tools
         */
        @Serializable
        public object Required: ToolChoice()
    }
}
