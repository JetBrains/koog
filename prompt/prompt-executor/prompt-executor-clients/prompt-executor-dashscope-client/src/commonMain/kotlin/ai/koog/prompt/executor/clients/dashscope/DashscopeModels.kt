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
        id = "qwen-flash",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 32_768
    )

    /**
     * Multimodal, low-latency Omni model supporting text, image, video, and audio I/O.
     * Suitable for audio/video chat, visual recognition, and multilingual speech interactions.
     */
    public val QWEN3_OMNI_FLASH: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen3-omni-flash",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.Vision.Image,
            LLMCapability.Vision.Video,
            LLMCapability.Audio,
        ),
        contextLength = 65_536,
        maxOutputTokens = 16_384
    )

    /**
     * Balanced model with enhanced capabilities.
     * Suitable for medium-complexity tasks requiring reasoning and tool usage.
     * Provides good balance between performance, cost, and capabilities.
     * Part of Qwen3 series.
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
        maxOutputTokens = 32_768
    )

    /**
     * Latest version of Qwen Plus model with automatic updates.
     * Part of Qwen3 series.
     * Suitable for medium-complexity tasks requiring reasoning and tool usage.
     * Automatically points to the newest version of qwen-plus.
     */
    public val QWEN_PLUS_LATEST: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen-plus-latest",
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
        maxOutputTokens = 32_768
    )

    /**
     * Code-specialized model (Qwen3-Coder) with strong Coding Agent abilities.
     * Suitable for complex coding tasks, tool use, and environment interaction; retains general abilities.
     */
    public val QWEN3_CODER_PLUS: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen3-coder-plus",
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
        maxOutputTokens = 65_536
    )

    /**
     * High-performance code model optimized for fast responses (Qwen3-Coder Flash).
     * Best for quick coding tasks and tool calling with low latency.
     */
    public val QWEN3_CODER_FLASH: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen3-coder-flash",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 32_768
    )

    /**
     * Most capable model in Qwen series.
     * Best suited for complex, multi-step tasks requiring advanced reasoning.
     * Offers extended context window and maximum output capacity.
     */
    public val QWEN3_MAX: LLModel = LLModel(
        provider = LLMProvider.Alibaba,
        id = "qwen3-max",
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
        contextLength = 262_144,
        maxOutputTokens = 65_536
    )
}
