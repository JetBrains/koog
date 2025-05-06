package ai.jetbrains.code.prompt.executor.clients.openai

import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel

object OpenAIModels {
    val GPT4o = LLModel(
        provider = LLMProvider.OpenAI, id = "gpt-4o", capabilities = listOf(
            LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation, LLMCapability.Tools
        )
    )

    val GPT4oMini = LLModel(
        provider = LLMProvider.OpenAI, id = "gpt-4o-mini", capabilities = listOf(
            LLMCapability.Temperature, LLMCapability.Schema.JSON.Full, LLMCapability.Speculation, LLMCapability.Tools
        )
    )

    val O3Mini = LLModel(
        provider = LLMProvider.OpenAI, id = "o3-mini", capabilities = listOf(
            LLMCapability.Tools, LLMCapability.Speculation
        )
    )
}
