package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public data object AndroidLocalLLMProvider : LLMProvider("android-litert", "LiteRT")

object AndroidLocalModels : LLModelDefinitions {
    // https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/tree/main
    public val FunctionGemma: LLModel = LLModel(
        provider = AndroidLocalLLMProvider,
        id = "tiny_garden.litertlm",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    public fun getIdFromPath(path: String): String {
        return path.split("/").last()
    }
}
