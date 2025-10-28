package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash001
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0FlashLite
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5FlashLite
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Pro
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Google Gemini models and their capabilities.
 * See https://ai.google.dev/gemini-api/docs for more information.
 *
 * | Name                        | Speed     | Price (per 1M tokens)        | Input                            | Output              |
 * |-----------------------------|-----------|------------------------------|----------------------------------|---------------------|
 * | [Gemini2_0Flash]            | Fast      | $0.10-$0.70 / $0.40          | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_0Flash001]         | Fast      | $0.10-$0.70 / $0.40          | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_0FlashLite]        | Very fast | $0.075 / $0.30               | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_5Pro]              | Slow      | $1.25-$2.50 / $10.00-$15.00² | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_5Flash]            | Medium    | $0.15-$1.00 / $0.60-$3.50³   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_5FlashLite]        | Fast      | $0.10-$0.30 / $0.40          | Audio, Image, Video, Text, Tools | Text, Tools         |
 *
 * @see <a href="modelcards.withgoogle.com/model-cards">
 */
public object GoogleModels : LLModelDefinitions {
    /**
     * Basic capabilities shared across all Gemini models
     */
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Completion,
        LLMCapability.MultipleChoices,
    )

    /**
     * Capabilities for models that support tools/function calling
     */
    private val toolCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
    )

    /**
     * Multimodal capabilities including vision (without tools)
     */
    private val multimodalCapabilities: List<LLMCapability> =
        listOf(LLMCapability.Vision.Image, LLMCapability.Vision.Video, LLMCapability.Audio)

    /**
     * Native structured output capabilities
     */
    private val structuredOutputCapabilities: List<LLMCapability.Schema.JSON> = listOf(
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
    )

    /**
     * Full capabilities including standard, multimodal, tools and native structured output
     */
    private val fullCapabilities: List<LLMCapability> =
        standardCapabilities + multimodalCapabilities + toolCapabilities + structuredOutputCapabilities

    /**
     * Gemini 2.0 Flash is a fast, efficient model for a wide range of tasks.
     * It's optimized for speed and efficiency.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: July 2024
     *
     * @see <a href="storage.googleapis.com/model-cards/documents/gemini-2-flash.pdf">
     */
    public val Gemini2_0Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash",
        capabilities = standardCapabilities + toolCapabilities + multimodalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Specific version of Gemini 2.0 Flash
     */
    public val Gemini2_0Flash001: LLModel = Gemini2_0Flash.copy(
        id = "gemini-2.0-flash-001",
    )

    /**
     * Gemini 2.0 Flash-Lite is the smallest and most efficient model in the Gemini 2.0 family.
     * Optimized for low-latency applications.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: July 2024
     *
     * @see <a href="storage.googleapis.com/model-cards/documents/gemini-2-flash-lite.pdf">
     */
    public val Gemini2_0FlashLite: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash-lite",
        capabilities = standardCapabilities + toolCapabilities + multimodalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Specific version of Gemini 2.0 Flash-Lite
     */
    public val Gemini2_0FlashLite001: LLModel = Gemini2_0FlashLite.copy(
        id = "gemini-2.0-flash-lite-001",
    )

    /**
     * Gemini 2.5 Pro offers advanced capabilities for complex tasks.
     *
     * @see <a href="storage.googleapis.com/model-cards/documents/gemini-2.5-pro.pdf">
     */
    public val Gemini2_5Pro: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.5-pro",
        capabilities = fullCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 2.5 Flash offers a balance of speed and capability.
     *
     * @see <a href="storage.googleapis.com/model-cards/documents/gemini-2.5-flash.pdf">
     */
    public val Gemini2_5Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.5-flash",
        capabilities = fullCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * A Gemini 2.5 Flash model optimized for cost-efficiency and high throughput.
     *
     * @see <a href="storage.googleapis.com/deepmind-media/Model-Cards/Gemini-2-5-Flash-Lite-Model-Card.pdf">
     */
    public val Gemini2_5FlashLite: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.5-flash-lite",
        capabilities = fullCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )
}
