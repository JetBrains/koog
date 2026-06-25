package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Predefined models for Azure AI Foundry deployments.
 */
public object AzureFoundryModels {

    private val defaultCapabilities = listOf(
        LLMCapability.Completion,
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.OpenAIEndpoint.Completions,
        LLMCapability.Schema.JSON.Basic
    )

    public val GPT5_4_Nano: LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "gpt-5.4-nano",
        capabilities = defaultCapabilities
    )

    public val GPT4_1_Mini: LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "gpt-4.1-mini",
        capabilities = defaultCapabilities
    )

    public val GPT4_1: LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "gpt-4.1",
        capabilities = defaultCapabilities
    )
}
