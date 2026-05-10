package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/** LLM provider identifier for on-device Android inference via the LiteRT runtime. */
public data object LiteRTLLMProvider : LLMProvider("android-litert", "LiteRT")

/**
 * Catalog of [LLModel] definitions available for on-device Android inference via LiteRT.
 *
 * Implements [LLModelDefinitions] so models registered here are discoverable by the
 * koog executor infrastructure. Custom models can be added at runtime via [addCustomModel].
 */
public object LiteRTLLModels : LLModelDefinitions {
    /**
     * A fine-tuned Gemma variant optimized for function-calling on device.
     *
     * Model file: `tiny_garden.litertlm`
     * Source: https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/tree/main
     */
    public val FunctionGemma: LLModel = LLModel(
        provider = LiteRTLLMProvider,
        id = "tiny_garden.litertlm",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    private val customModels = mutableListOf<LLModel>()

    /** All available models, combining the built-in [FunctionGemma] with any custom models. */
    override val models: List<LLModel>
        get() = listOf(FunctionGemma) + customModels

    /** Registers [model] as an additional on-device model available for inference. */
    override fun addCustomModel(model: LLModel) {
        customModels.add(model)
    }
}
