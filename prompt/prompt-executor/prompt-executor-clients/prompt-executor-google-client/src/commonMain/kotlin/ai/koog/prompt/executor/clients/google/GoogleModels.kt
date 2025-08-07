package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Flash002
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Flash8B
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Flash8BLatest
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5FlashLatest
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Pro
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5Pro002
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini1_5ProLatest
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash001
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0FlashLite
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Flash
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
 * | [Gemini1_5Pro]              | Medium    | $1.25-$2.50 / $5.00-$10.00   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5ProLatest]        | Medium    | $1.25-$2.50 / $5.00-$10.00   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5Pro002]           | Medium    | $1.25-$2.50 / $5.00-$10.00   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5Flash]            | Fast      | $0.075-$0.15 / $0.30-$0.60   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5FlashLatest]      | Fast      | $0.075-$0.15 / $0.30-$0.60   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5Flash002]         | Fast      | $0.075-$0.15 / $0.30-$0.60   | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5Flash8B]          | Very fast | $0.0375-$0.075 / $0.15-$0.30 | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini1_5Flash8BLatest]    | Very fast | $0.0375-$0.075 / $0.15-$0.30 | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_5Pro]              | Slow      | $1.25-$2.50 / $10.00-$15.00² | Audio, Image, Video, Text, Tools | Text, Tools         |
 * | [Gemini2_5Flash]            | Medium    | $0.15-$1.00 / $0.60-$3.50³   | Audio, Image, Video, Text, Tools | Text, Tools         |
 *
 * @see <a href="modelcards.withgoogle.com/model-cards">
 */
public object GoogleModels : LLModelDefinitions {
    /**
     * Basic capabilities shared across all Gemini models
     */
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Completion,
        LLMCapability.MultipleChoices,
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
    private val multimodalCapabilities: List<LLMCapability> =
        standardCapabilities + listOf(LLMCapability.Vision.Image, LLMCapability.Vision.Video, LLMCapability.Audio)

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
     *
     * @see <a href="storage.googleapis.com/model-cards/documents/gemini-2-flash.pdf">
     */
    public val Gemini2_0Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-2.0-flash",
        capabilities = fullCapabilities,
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
        capabilities = fullCapabilities, // Flash Lite has robust tool support
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
     * Gemini 1.5 Pro is a capable multimodal model with strong reasoning capabilities.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: February 2024
     *
     * @see <a href="storage.googleapis.com/deepmind-media/gemini/gemini_v1_5_report.pdf#page=105">
     */
    public val Gemini1_5Pro: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-pro",
        capabilities = fullCapabilities, // 1.5 Pro has robust tool support
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Latest version of Gemini 1.5 Pro
     */
    public val Gemini1_5ProLatest: LLModel = Gemini1_5Pro.copy(
        id = "gemini-1.5-pro-latest",
    )

    /**
     * Specific version of Gemini 1.5 Pro
     */
    public val Gemini1_5Pro002: LLModel = Gemini1_5Pro.copy(
        id = "gemini-1.5-pro-002",
    )

    /**
     * Gemini 1.5 Flash is a fast and efficient multimodal model.
     *
     * Context window: 1 million tokens
     * Knowledge cutoff: February 2024
     *
     * @see <a href="storage.googleapis.com/deepmind-media/gemini/gemini_v1_5_report.pdf#page=105">
     */
    public val Gemini1_5Flash: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash",
        capabilities = fullCapabilities, // 1.5 Flash has tool support
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Latest version of Gemini 1.5 Flash
     */
    public val Gemini1_5FlashLatest: LLModel = Gemini1_5Flash.copy(
        id = "gemini-1.5-flash-latest",
        capabilities = multimodalCapabilities,
    )

    /**
     * Specific version of Gemini 1.5 Flash
     */
    public val Gemini1_5Flash002: LLModel = Gemini1_5Flash.copy(
        id = "gemini-1.5-flash-latest",
        capabilities = multimodalCapabilities,
    )

    /**
     * Gemini 1.5 Flash 8B is a smaller, more efficient variant of Gemini 1.5 Flash.
     */
    public val Gemini1_5Flash8B: LLModel = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-1.5-flash-8b",
        capabilities = multimodalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Specific version of Gemini 1.5 Flash 8B
     */
    public val Gemini1_5Flash8B001: LLModel = Gemini1_5Flash8B.copy(
        id = "gemini-1.5-flash-8b-001",
    )

    /**
     * Latest version of Gemini 1.5 Flash 8B
     */
    public val Gemini1_5Flash8BLatest: LLModel = Gemini1_5Flash8B.copy(
        id = "gemini-1.5-flash-8b-latest",
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
        capabilities = multimodalCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )
}
