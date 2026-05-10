package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public data object LiteRTLLMProvider : LLMProvider("android-litert", "LiteRT")

public object LiteRTLLModels : LLModelDefinitions {
    // https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/tree/main
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

    override val models: List<LLModel>
        get() = listOf(FunctionGemma) + customModels

    override fun addCustomModel(model: LLModel) {
        customModels.add(model)
    }
}
