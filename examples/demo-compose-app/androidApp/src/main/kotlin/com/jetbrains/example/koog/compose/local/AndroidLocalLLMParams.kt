package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class AndroidLocalLLMParams private constructor(
    val exactTemperature: Double,
    val topK: Int,
    val topP: Double,
    val seed: Int? = null
) : LLMParams(
    temperature = exactTemperature,
    additionalProperties = buildJsonObject {
        put("topK", JsonPrimitive(topK))
        put("topP", JsonPrimitive(topP))
        seed?.let { put("seed", JsonPrimitive(seed)) }
    }
) {

    constructor(
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

    companion object {
        private const val DEFAULT_TEMPERATURE: Double = 0.8
        private const val DEFAULT_TOP_K: Int = 10
        private const val DEFAULT_TOP_P: Double = 0.95
    }
}

internal fun LLMParams.toAndroidLocalParams(): AndroidLocalLLMParams {
    if (this is AndroidLocalLLMParams) return this
    return AndroidLocalLLMParams(
        temperature = temperature,
        topK = additionalProperties?.get("topK")?.jsonPrimitive?.int,
        topP = additionalProperties?.get("topP")?.jsonPrimitive?.double,
        seed = additionalProperties?.get("seed")?.jsonPrimitive?.int,
    )
}
