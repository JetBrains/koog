package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_3
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_3_5
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_4_5
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_3
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4_1
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_3_5
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_3_7
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4_5
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Anthropic models for text generation and embeddings.
 *
 * | Name         | Speed           | Price (MTok) | Input                        | Output      |
 * |--------------|-----------------|--------------|------------------------------|-------------|
 * | [Opus_3]     | Moderately fast | $15-$75      | Text, Image, Tools           | Text, Tools |
 * | [Haiku_3]    | Fast            | $0.25-$1.25  | Text, Image, Tools           | Text, Tools |
 * | [Haiku_3_5]  | Fastest         | $0.8-$4      | Text, Image, Tools, Document | Text, Tools |
 * | [Sonnet_3_5] | Fast            | $3-$15       | Text, Image, Tools, Document | Text, Tools |
 * | [Sonnet_3_7] | Fast            | $3-$15       | Text, Image, Tools, Document | Text, Tools |
 * | [Sonnet_4]   | Fast            | $3-$15       | Text, Image, Tools, Document | Text, Tools |
 * | [Opus_4]     | Moderately fast | $15-$75      | Text, Image, Tools, Document | Text, Tools |
 * | [Opus_4_1]   | Moderately fast | $15-$75      | Text, Image, Tools, Document | Text, Tools |
 * | [Sonnet_4_5] | Fast            | $3-$15       | Text, Image, Tools, Document | Text, Tools |
 * | [Haiku_4_5]  | Fastest         | $1-$5        | Text, Image, Tools, Document | Text, Tools |
 */
public object AnthropicModels : LLModelDefinitions {

    /**
     * Claude 3 Opus is Anthropic's most powerful model, designed for highly complex tasks.
     * It excels at tasks requiring deep expertise, nuanced understanding, and careful analysis.
     *
     * 200K context window
     * Knowledge cutoff: August 2023
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Opus_3: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-3-opus",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Claude 3 Haiku is Anthropic's fastest and most compact model.
     * It's designed for high-throughput, cost-effective applications where speed is a priority.
     *
     * 200K context window
     * Knowledge cutoff: August 2023
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Haiku_3: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-3-haiku",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 4_096,
    )

    /**
     * Claude 3.5 Sonnet is an improved version of Claude 3 Sonnet.
     * It offers enhanced performance while maintaining the balance between intelligence and speed.
     *
     * 200K context window
     * Knowledge cutoff: April 2024 (v1) / July 2024 (v2)
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Sonnet_3_5: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-3-5-sonnet",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 8_192,
    )

    /**
     * Claude 3.5 Haiku is Anthropic's fastest model.
     * It offers intelligence at blazing speeds.
     *
     * 200K context window
     * Knowledge cutoff: July 2024
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Haiku_3_5: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-3-5-haiku",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 8_192,
    )

    /**
     * Claude 3.7 Sonnet is Anthropic's intelligent model.
     * It offers the highest level of intelligence and capability with toggleable extended thinking.
     *
     * 200K context window
     * Knowledge cutoff: November 2024
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Sonnet_3_7: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-3-7-sonnet",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude Sonnet 4 is Anthropic's high-performance model with exceptional reasoning and efficiency.
     *
     * 200K context window
     * Knowledge cutoff: March 2025
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Sonnet_4: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-sonnet-4-0",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude Opus 4 is Anthropic's previous flagship model.
     * It has very high intelligence and capability.
     *
     * 200K context window
     * Knowledge cutoff: March 2025
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Opus_4: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-opus-4-0",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 32_000,
    )

    /**
     * Claude Opus 4.1 is Anthropic's exceptional model for specialized complex tasks.
     * It has a very high level of intelligence and capability.
     *
     * 200K context window
     * Knowledge cutoff: March 2025
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Opus_4_1: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-opus-4-1",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion
        ),
        contextLength = 200_000,
        maxOutputTokens = 32_000,
    )

    /**
     * Claude Sonnet 4.5 is Anthropic's best model for complex agents and coding.
     * It has the highest level of intelligence across most tasks with exceptional agent and coding capabilities.
     *
     * 200K context window
     * Knowledge cutoff: July 2025
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Sonnet_4_5: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-sonnet-4-5",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion,
        ),
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )

    /**
     * Claude Haiku 4.5 is Anthropic's fastest and most intelligent Haiku model.
     * It has the near-frontier intelligence at blazing speeds with extended thinking and exceptional cost-efficiency.
     *
     * 200K context window
     * Knowledge cutoff: July 2025
     *
     * @see <a href="https://docs.anthropic.com/claude/docs/models-overview">
     */
    public val Haiku_4_5: LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-haiku-4-5",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Vision.Image,
            LLMCapability.Document,
            LLMCapability.Completion,
        ),
        contextLength = 200_000,
        maxOutputTokens = 64_000,
    )
}

internal val DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP: Map<LLModel, String> = mapOf(
    Opus_3 to "claude-3-opus-20240229",
    Haiku_3 to "claude-3-haiku-20240307",
    Sonnet_3_5 to "claude-3-5-sonnet-20241022",
    Haiku_3_5 to "claude-3-5-haiku-20241022",
    Sonnet_3_7 to "claude-3-7-sonnet-20250219",
    Sonnet_4 to "claude-sonnet-4-20250514",
    Opus_4 to "claude-opus-4-20250514",
    Opus_4_1 to "claude-opus-4-1-20250805",
    Sonnet_4_5 to "claude-sonnet-4-5-20250929",
    Haiku_4_5 to "claude-haiku-4-5-20251001",
)
