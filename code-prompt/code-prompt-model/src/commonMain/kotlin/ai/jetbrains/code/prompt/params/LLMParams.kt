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
 *
 * This class also includes a nested `Builder` class to facilitate constructing instances in a more
 * customizable and incremental way.
 */
@Serializable
data class LLMParams(
    val temperature: Double? = null,
    val speculation: String? = null,
    val schema: Schema? = null,
) {
    fun default(default: LLMParams): LLMParams = copy(
        temperature = temperature ?: default.temperature,
        speculation = speculation ?: default.speculation,
        schema = schema ?: default.schema,
    )

    @Serializable
    sealed interface Schema {
        val name: String

        @Serializable
        sealed interface JSON: Schema {
            val schema: JsonObject

            @Serializable
            data class Simple(override val name: String, override val schema: JsonObject) : JSON
            @Serializable
            data class Full(override val name: String, override val schema: JsonObject) : JSON
        }
    }
}