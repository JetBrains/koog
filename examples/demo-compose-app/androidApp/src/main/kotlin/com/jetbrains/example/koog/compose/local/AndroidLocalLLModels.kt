package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public data object AndroidLocalLLMProvider : LLMProvider("android-litert", "LiteRT")

public val Gemma: LLModel = LLModel(
    provider = AndroidLocalLLMProvider,
    id = "gemma-3n-E2B-it-agent-fixed.litertlm",
    capabilities = listOf(
        LLMCapability.Tools,
        LLMCapability.Completion
    ),
    contextLength = 200_000,
    maxOutputTokens = 4_096,
)

public val Gemma3: LLModel = LLModel(
    provider = AndroidLocalLLMProvider,
    id = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
    capabilities = listOf(
        LLMCapability.Tools,
        LLMCapability.Completion
    ),
    contextLength = 200_000,
    maxOutputTokens = 4_096,
)

