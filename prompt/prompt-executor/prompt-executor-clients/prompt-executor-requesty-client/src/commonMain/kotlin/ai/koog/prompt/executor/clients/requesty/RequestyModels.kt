package ai.koog.prompt.executor.clients.requesty

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmField

/**
 * Requesty models
 * Models available through the Requesty API.
 *
 * Requesty routes requests to multiple upstream providers behind one OpenAI compatible endpoint.
 * Model ids are prefixed with the upstream provider, for example `openai/gpt-4o-mini`.
 * The full list of available models can be fetched with [RequestyLLMClient.models].
 *
 * @see <a href="https://docs.requesty.ai">Requesty Documentation</a>
 */
public object RequestyModels : LLModelDefinitions {
    /**
     * Standard capabilities available for most Requesty models.
     * Includes temperature control, tools, tool choice, JSON output, multiple choices, and completion.
     */
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.MultipleChoices,
        LLMCapability.Completion,
    )

    /**
     * Multimodal capabilities including vision support.
     * Extends standard capabilities with image vision processing.
     */
    private val multimodalCapabilities: List<LLMCapability> = standardCapabilities + LLMCapability.Vision.Image

    /**
     * Represents the GPT-4o mini model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT4oMini: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-4o-mini",
        capabilities = multimodalCapabilities + LLMCapability.Speculation,
        contextLength = 128_000,
        maxOutputTokens = 16_384,
    )

    /**
     * Represents the GPT-4o model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT4o: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-4o",
        capabilities = multimodalCapabilities + LLMCapability.Speculation,
        contextLength = 128_000,
        maxOutputTokens = 16_384,
    )

    /**
     * Represents the GPT-4.1 model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT4_1: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-4.1",
        capabilities = multimodalCapabilities + LLMCapability.Speculation,
        contextLength = 1_047_576,
        maxOutputTokens = 32_768,
    )

    /**
     * Represents the GPT-4.1 mini model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT4_1Mini: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-4.1-mini",
        capabilities = multimodalCapabilities + LLMCapability.Speculation,
        contextLength = 1_047_576,
        maxOutputTokens = 32_768,
    )

    /**
     * Represents the GPT-5 reasoning model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT5: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-5",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 400_000,
        maxOutputTokens = 128_000,
    )

    /**
     * Represents the GPT-5 mini reasoning model provided by OpenAI through Requesty.
     */
    @JvmField
    public val GPT5Mini: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "openai/gpt-5-mini",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 400_000,
        maxOutputTokens = 128_000,
    )

    /**
     * Represents the Claude Sonnet 4.5 model provided by Anthropic through Requesty.
     */
    @JvmField
    public val ClaudeSonnet4_5: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "anthropic/claude-sonnet-4-5",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 1_000_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Represents the Claude Opus 4.5 model provided by Anthropic through Requesty.
     */
    @JvmField
    public val ClaudeOpus4_5: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "anthropic/claude-opus-4-5",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Represents the Claude Haiku 4.5 model provided by Anthropic through Requesty.
     */
    @JvmField
    public val ClaudeHaiku4_5: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "anthropic/claude-haiku-4-5",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Represents the Gemini 2.5 Flash model provided by Google through Requesty.
     */
    @JvmField
    public val Gemini2_5Flash: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "google/gemini-2.5-flash",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 1_048_576,
        maxOutputTokens = 65_535,
    )

    /**
     * Represents the Gemini 2.5 Pro model provided by Google through Requesty.
     */
    @JvmField
    public val Gemini2_5Pro: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "google/gemini-2.5-pro",
        capabilities = multimodalCapabilities + LLMCapability.Thinking,
        contextLength = 1_048_576,
        maxOutputTokens = 65_535,
    )

    /**
     * Represents the DeepSeek Chat model provided by DeepSeek through Requesty.
     * Text only, JSON object output, no JSON schema output.
     */
    @JvmField
    public val DeepSeekChat: LLModel = LLModel(
        provider = LLMProvider.Requesty,
        id = "deepseek/deepseek-chat",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.MultipleChoices,
            LLMCapability.Completion,
            LLMCapability.Thinking,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 384_000,
    )

    /**
     * List of the supported models by the Requesty provider.
     */
    private val supportedModels: List<LLModel> = listOf(
        // OpenAI
        GPT4oMini,
        GPT4o,
        GPT4_1,
        GPT4_1Mini,
        GPT5,
        GPT5Mini,
        // Anthropic
        ClaudeSonnet4_5,
        ClaudeOpus4_5,
        ClaudeHaiku4_5,
        // Google
        Gemini2_5Flash,
        Gemini2_5Pro,
        // DeepSeek
        DeepSeekChat,
    )

    /**
     * List of custom models added to the Requesty provider.
     */
    private val customModels: MutableList<LLModel> = mutableListOf()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == LLMProvider.Requesty) { "Model provider must be Requesty" }
        customModels.add(model)
    }
}
