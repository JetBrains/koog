package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Object containing a collection of predefined DeepSeek model configurations.
 *
 * DeepSeek provides powerful language models with competitive pricing and advanced reasoning capabilities.
 * All models support JSON output, function calling, and chat prefix completion features.
 *
 * | Name               | Speed  | Price       | Input       | Output      |
 * |--------------------|--------|-------------|-------------|-------------|
 * | [DeepSeekChat]     | Fast   | $0.27-$1.1  | Text, Tools | Text, Tools |
 * | [DeepSeekReasoner] | Medium | $0.55-$2.19 | Text, Tools | Text, Tools |
 *
 * @see <a href="https://platform.deepseek.com/api-docs/pricing">DeepSeek Pricing Documentation</a>
 */
public object DeepSeekModels : LLModelDefinitions {

    /**
     * DeepSeek Chat model optimized for general conversation and quick responses.
     *
     * @see <a href="https://platform.deepseek.com/api-docs/api/create-chat-completion">Chat Completion API</a>
     */
    @JvmField
    public val DeepSeekChat: LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = "deepseek-chat",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
        ),
        contextLength = 64_000,
        maxOutputTokens = 8_000
    )

    /**
     * DeepSeek Reasoner model specialized for complex reasoning and analytical tasks.
     *
     * @see <a href="https://platform.deepseek.com/api-docs/api/create-chat-completion">Chat Completion API</a>
     */
    @JvmField
    public val DeepSeekReasoner: LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = "deepseek-reasoner",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
        ),
        contextLength = 64_000,
        maxOutputTokens = 64_000
    )
}
