package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels.DeepSeekV4Flash
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels.DeepSeekV4Pro
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.collections.plus
import kotlin.jvm.JvmField

/**
 * Object containing a collection of predefined DeepSeek model configurations.
 *
 * DeepSeek provides powerful language models with competitive pricing and advanced reasoning capabilities.
 * All models support JSON output, function calling, and chat prefix completion features.
 *
 * | Name               | Speed  | Price                | Input       | Output      |
 * |--------------------|--------|----------------------|-------------|-------------|
 * | [DeepSeekV4Flash]  | Fast   | $0.14 / $0.28 per 1M | Text, Tools | Text, Tools |
 * | [DeepSeekV4Pro]    | Medium | $1.74 / $3.48 per 1M | Text, Tools | Text, Tools |
 *
 * @see <a href="https://platform.deepseek.com/api-docs/pricing">DeepSeek Pricing Documentation</a>
 */
public object DeepSeekModels : LLModelDefinitions {

    /**
     * DeepSeek V4 Flash model optimized for fast, cost-effective generation.
     * Supports both thinking and non-thinking modes in the DeepSeek API.
     *
     * @see <a href="https://api-docs.deepseek.com/api/create-chat-completion/">Chat Completion API</a>
     */
    @JvmField
    public val DeepSeekV4Flash: LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = "deepseek-v4-flash",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
            LLMCapability.Thinking,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 384_000
    )

    /**
     * DeepSeek V4 Pro model optimized for advanced reasoning and agentic tasks.
     * Supports both thinking and non-thinking modes in the DeepSeek API.
     *
     * @see <a href="https://api-docs.deepseek.com/api/create-chat-completion/">Chat Completion API</a>
     */
    @JvmField
    public val DeepSeekV4Pro: LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = "deepseek-v4-pro",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
            LLMCapability.Thinking,
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 384_000
    )

    /**
     * List of the supported models by the DeepSeek provider.
     */
    private val supportedModels: List<LLModel> = listOf(
        DeepSeekV4Flash,
        DeepSeekV4Pro,
    )

    /**
     * List of custom models added to the DeepSeek provider.
     */
    private val customModels: MutableList<LLModel> = mutableListOf()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == LLMProvider.DeepSeek) { "Model provider must be DeepSeek" }
        customModels.add(model)
    }
}
