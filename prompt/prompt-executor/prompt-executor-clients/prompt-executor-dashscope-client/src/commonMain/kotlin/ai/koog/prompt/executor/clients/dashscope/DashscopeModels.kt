package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Dashscope LLM models enumeration.
 */
public object DashscopeModels : LLModelDefinitions {
    /**
     * High-performance model optimized for fast response times.
     * Best suited for simple tasks requiring quick responses.
     * Offers basic completion and tool usage capabilities.
     */
    public val QWEN_FLASH: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen-turbo",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
        contextLength = 128_000,
        maxOutputTokens = 32_768
    )

    /**
     * Balanced model with enhanced capabilities.
     * Suitable for medium-complexity tasks requiring reasoning and tool usage.
     * Provides good balance between performance, cost, and capabilities.
     */
    public val QWEN_PLUS: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen-plus",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.MultipleChoices,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 16_384
    )
}
