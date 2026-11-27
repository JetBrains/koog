package ai.koog.prompt.executor.clients.litert

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * LLM sampling parameters for on-device Android inference via LiteRT.
 *
 * Extends [LLMParams] with LiteRT-specific sampling controls and serializes
 * [topK] and [topP] into [LLMParams.additionalProperties] so they are forwarded
 * to the underlying engine.
 *
 * @property exactTemperature Sampling temperature controlling output randomness.
 * @property topK Number of top tokens to consider during sampling.
 * @property topP Nucleus-sampling probability threshold.
 * @property seed Optional random seed for reproducible generation.
 */
public class AndroidLocalLLMParams private constructor(
    public val exactTemperature: Double,
    public val topK: Int,
    public val topP: Double,
    public val seed: Int? = null
) : LLMParams(
    temperature = exactTemperature,
    additionalProperties = buildJsonObject {
        put("topK", JsonPrimitive(topK))
        put("topP", JsonPrimitive(topP))
        seed?.let { put("seed", JsonPrimitive(seed)) }
    }
) {

    /**
     * Creates [AndroidLocalLLMParams] applying defaults for any `null` argument.
     *
     * @param temperature Sampling temperature; defaults to [DEFAULT_TEMPERATURE] when `null`.
     * @param topK Top-K value; defaults to [DEFAULT_TOP_K] when `null`.
     * @param topP Nucleus-sampling threshold; defaults to [DEFAULT_TOP_P] when `null`.
     * @param seed Optional random seed for reproducible generation.
     */
    public constructor(
        temperature: Double?,
        topK: Int?,
        topP: Double?,
        seed: Int? = null
    ) : this(
        exactTemperature = temperature ?: DEFAULT_TEMPERATURE,
        topK = topK ?: DEFAULT_TOP_K,
        topP = topP ?: DEFAULT_TOP_P,
        seed = seed,
    )

    private companion object {
        private const val DEFAULT_TEMPERATURE: Double = 0.8
        private const val DEFAULT_TOP_K: Int = 10
        private const val DEFAULT_TOP_P: Double = 0.95
    }
}

/**
 * Converts a generic [LLMParams] to [AndroidLocalLLMParams].
 *
 * Returns the receiver unchanged if it is already an [AndroidLocalLLMParams].
 * Otherwise extracts `topK`, `topP`, and `seed` from [LLMParams.additionalProperties].
 */
internal fun LLMParams.toAndroidLocalParams(): AndroidLocalLLMParams {
    if (this is AndroidLocalLLMParams) return this
    return AndroidLocalLLMParams(
        temperature = temperature,
        topK = additionalProperties?.get("topK")?.jsonPrimitive?.int,
        topP = additionalProperties?.get("topP")?.jsonPrimitive?.double,
        seed = additionalProperties?.get("seed")?.jsonPrimitive?.int,
    )
}
