package ai.jetbrains.code.prompt.executor.clients.anthropic

import ai.jetbrains.code.prompt.llm.LLMCapability
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel

object AnthropicModels {
    val Sonnet_3_7 = LLModel(
        provider = LLMProvider.Anthropic,
        id = "sonnet-3-7",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
        )
    )
    val Sonnet_3_5 = LLModel(
        provider = LLMProvider.Anthropic,
        id = "sonnet-3-5",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools
        )
    )
}

internal val DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP: Map<LLModel, String> = mapOf(
    AnthropicModels.Sonnet_3_7 to "claude-3-sonnet-20240229",
    AnthropicModels.Sonnet_3_5 to "claude-3-5-sonnet-20240620"
)