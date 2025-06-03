package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Google Gemini models and their capabilities.
 * See https://ai.google.dev/gemini-api/docs for more information.
 */
public object GoogleModels: LLModelDefinitions {
    /**
     * Basic capabilities shared across all Gemini models
     */
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Completion
    )

    /**
     * Capabilities for models that support tools/function calling
     */
    private val toolCapabilities: List<LLMCapability> = standardCapabilities + listOf(
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
    )

    /**
     * Multimodal capabilities including vision (without tools)
     */
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + listOf(LLMCapability.Vision)

    /**
     * Full capabilities including multimodal and tools
     */
    private val fullCapabilities: List<LLMCapability> = multimodalCapabilities + toolCapabilities

    /**
     * Gemini 2.0 Flash is a fast, efficient model for a wide range of tasks.
     * It's optimized for speed and efficiency.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: July 2024
     */
    public val Gemini2_0Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash",
        capabilities = fullCapabilities
    )

    /**
     * Specific version of Gemini 2.0 Flash
     */
    public val Gemini2_0Flash001: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash-001",
        capabilities = fullCapabilities
    )

    /**
     * Gemini 2.0 Flash-Lite is the smallest and most efficient model in the Gemini 2.0 family.
     * Optimized for low-latency applications.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: July 2024
     */
    public val Gemini2_0FlashLite: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash-lite",
        capabilities = standardCapabilities // Flash Lite does not support tools
    )

    /**
     * Specific version of Gemini 2.0 Flash-Lite
     */
    public val Gemini2_0FlashLite001: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash-lite-001",
        capabilities = standardCapabilities // Flash Lite does not support tools
    )

    /**
     * Gemini 1.5 Pro is a capable multimodal model with strong reasoning capabilities.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: February 2024
     */
    public val Gemini1_5Pro: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-pro",
        capabilities = fullCapabilities // 1.5 Pro has robust tool support
    )

    /**
     * Latest version of Gemini 1.5 Pro
     */
    public val Gemini1_5ProLatest: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-pro-latest",
        capabilities = fullCapabilities // 1.5 Pro has robust tool support
    )

    /**
     * Specific version of Gemini 1.5 Pro
     */
    public val Gemini1_5Pro001: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-pro-001",
        capabilities = fullCapabilities // 1.5 Pro has robust tool support
    )

    /**
     * Specific version of Gemini 1.5 Pro
     */
    public val Gemini1_5Pro002: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-pro-002",
        capabilities = fullCapabilities // 1.5 Pro has robust tool support
    )

    /**
     * Gemini 1.5 Flash is a fast and efficient multimodal model.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: February 2024
     */
    public val Gemini1_5Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash",
        capabilities = fullCapabilities // 1.5 Flash has tool support
    )

    /**
     * Latest version of Gemini 1.5 Flash
     */
    public val Gemini1_5FlashLatest: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-latest",
        capabilities = multimodalCapabilities
    )

    /**
     * Specific version of Gemini 1.5 Flash
     */
    public val Gemini1_5Flash001: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-001",
        capabilities = multimodalCapabilities
    )

    /**
     * Specific version of Gemini 1.5 Flash
     */
    public val Gemini1_5Flash002: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-002",
        capabilities = multimodalCapabilities
    )

    /**
     * Gemini 1.5 Flash 8B is a smaller, more efficient variant of Gemini 1.5 Flash.
     */
    public val Gemini1_5Flash8B: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-8b",
        capabilities = multimodalCapabilities
    )

    /**
     * Specific version of Gemini 1.5 Flash 8B
     */
    public val Gemini1_5Flash8B001: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-8b-001",
        capabilities = multimodalCapabilities
    )

    /**
     * Latest version of Gemini 1.5 Flash 8B
     */
    public val Gemini1_5Flash8BLatest: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-8b-latest",
        capabilities = multimodalCapabilities
    )

    /**
     * Gemini 2.5 Pro Preview 05-06 is one of the Gemini 2.5 Pro preview versions.
     * It offers advanced capabilities for complex tasks.
     */
    public val Gemini2_5ProPreview0506: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.5-pro-preview-05-06",
        capabilities = fullCapabilities // 2.5 Pro Preview has tool support
    )

    /**
     * Gemini 2.5 Flash Preview 04-17 is one of the Gemini 2.5 Flash preview versions.
     * It offers a balance of speed and capability.
     */
    public val Gemini2_5FlashPreview0417: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.5-flash-preview-04-17",
        capabilities = multimodalCapabilities
    )
}
