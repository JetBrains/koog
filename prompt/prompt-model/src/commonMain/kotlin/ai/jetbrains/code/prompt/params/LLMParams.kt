package ai.jetbrains.code.prompt.params

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
 * This class also includes a nested `Builder` class to facilitate constructing instances in a more
 * customizable and incremental way.
 */
@Serializable
public data class LLMParams(
    val temperature: Double? = null,
    val speculation: String? = null,
    val schema: Schema? = null,
    val toolChoice: ToolChoice? = null,
) {
    public fun default(default: LLMParams): LLMParams = copy(
        temperature = temperature ?: default.temperature,
        speculation = speculation ?: default.speculation,
        schema = schema ?: default.schema
    )

    @Serializable
    public sealed interface Schema {
        public val name: String

        @Serializable
        public sealed interface JSON: Schema {
            public val schema: JsonObject

            @Serializable
            public data class Simple(override val name: String, override val schema: JsonObject) : JSON
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
