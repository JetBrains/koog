package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.Serializable

/**
 * Represents a Bedrock model with an optional inference profile prefix.
 *
 * This allows users to specify an inference profile prefix that will be prepended
 * to the model ID when making requests to AWS Bedrock.
 *
 * @param model The base LLModel to use
 * @param modelId The ID of the used model. Defaults to the ID of the provided model.
 * @param inferenceProfilePrefix Optional prefix to prepend to the model ID
 */

@Serializable
public data class BedrockModel(
    val model: LLModel,
    val modelId: String = model.id,
    val inferenceProfilePrefix: String = BedrockInferencePrefixes.US.prefix
) {
    /**
     * Returns the effective model ID with inference profile prefix if provided.
     */
    val effectiveModelId: String = "$inferenceProfilePrefix.$modelId"

    /**
     * Returns the LLModel with the effective model ID.
     */
    val effectiveModel: LLModel = model.copy(provider = LLMProvider.Bedrock, id = effectiveModelId)
}

/**
 * AWS Bedrock regions.
 *
 * Represents all available AWS regions where Bedrock service is supported.
 */
@Serializable
public enum class BedrockRegions(public val regionCode: String) {
    US_WEST_1("us-west-1"),
    US_WEST_2("us-west-2"),
    US_EAST_1("us-east-1"),
    US_EAST_2("us-east-2"),
    CA_CENTRAL_1("ca-central-1"),
    CA_WEST_1("ca-west-1"),
    MX_CENTRAL_1("mx-central-1"),
    AF_SOUTH_1("af-south-1"),
    AP_EAST_1("ap-east-1"),
    AP_EAST_2("ap-east-2"),
    AP_NORTHEAST_1("ap-northeast-1"),
    AP_NORTHEAST_2("ap-northeast-2"),
    AP_NORTHEAST_3("ap-northeast-3"),
    AP_SOUTH_1("ap-south-1"),
    AP_SOUTH_2("ap-south-2"),
    AP_SOUTHEAST_1("ap-southeast-1"),
    AP_SOUTHEAST_2("ap-southeast-2"),
    AP_SOUTHEAST_3("ap-southeast-3"),
    AP_SOUTHEAST_4("ap-southeast-4"),
    AP_SOUTHEAST_5("ap-southeast-5"),
    AP_SOUTHEAST_7("ap-southeast-7"),
    EU_CENTRAL_1("eu-central-1"),
    EU_CENTRAL_2("eu-central-2"),
    EU_NORTH_1("eu-north-1"),
    EU_NORTH_2("eu-north-2"),
    EU_WEST_1("eu-west-1"),
    EU_WEST_2("eu-west-2"),
    EU_WEST_3("eu-west-3"),
    IL_CENTRAL_1("il-central-1"),
    ME_CENTRAL_1("me-central-1"),
    ME_SOUTH_1("me-south-1"),
    SA_EAST_1("sa-east-1");

    override fun toString(): String = regionCode
}

/**
 * AWS Bedrock inference ID prefixes.
 *
 */
@Serializable
public enum class BedrockInferencePrefixes(public val prefix: String) {
    US("us"),
    CA("ca"),
    MX("mx"),
    AF("af"),
    AP("ap"),
    EU("eu"),
    IL("il"),
    ME("me"),
    SA("sa");

    override fun toString(): String = prefix
}

/**
 * Bedrock models
 * Models available through the AWS Bedrock API
 */
public object BedrockModels : LLModelDefinitions {
    // Basic capabilities for text-only models
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Completion
    )

    // Capabilities for models that support tools/functions
    private val toolCapabilities: List<LLMCapability> = standardCapabilities + listOf(
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Schema.JSON.Standard
    )

    // Capabilities of the nova models
    private val novaCapabilities: List<LLMCapability> = standardCapabilities + listOf(
        LLMCapability.Tools,
    )

    // Full capabilities (multimodal + tools)
    private val fullCapabilities: List<LLMCapability> = standardCapabilities + listOf(
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.Vision.Image,
        LLMCapability.Document,
    )

    /**
     * Claude 3 Opus - Anthropic's most powerful model with superior performance on complex tasks
     *
     * This model excels at:
     * - Complex reasoning and analysis
     * - Creative and nuanced content generation
     * - Following detailed instructions
     * - Multimodal understanding (text and images)
     * - Tool/function calling
     */
    public val AnthropicClaude3Opus: LLModel = BedrockModel(
        AnthropicModels.Opus_3,
        "anthropic.claude-3-opus-20240229-v1:0",
    ).effectiveModel

    /**
     * Claude 4 Opus - Anthropic's previous flagship model
     *
     * This model sets new standards in:
     * - Complex reasoning and advanced coding
     * - Autonomous management of complex, multi-step tasks
     * - Extended thinking for deeper reasoning
     * - AI agent capabilities for orchestrating workflows
     * - Multimodal understanding (text and images)
     * - Tool/function calling with parallel execution
     * - Memory capabilities for maintaining continuity
     */
    public val AnthropicClaude4Opus: LLModel = BedrockModel(
        AnthropicModels.Opus_4,
        "anthropic.claude-opus-4-20250514-v1:0",
    ).effectiveModel

    /**
     * Claude 4.1 Opus - Anthropic's most capable model
     *
     * This model sets new standards in:
     * - Complex reasoning and advanced coding
     * - Autonomous management of complex, multi-step tasks
     * - Extended thinking for deeper reasoning
     * - AI agent capabilities for orchestrating workflows
     * - Multimodal understanding (text and images)
     * - Tool/function calling with parallel execution
     * - Memory capabilities for maintaining continuity
     */
    public val AnthropicClaude41Opus: LLModel = BedrockModel(
        AnthropicModels.Opus_4_1,
        "anthropic.claude-opus-4-1-20250805-v1:0",
    ).effectiveModel

    /**
     * Claude 4 Sonnet - High-performance model with exceptional reasoning and efficiency
     *
     * This model offers:
     * - Superior coding and reasoning capabilities
     * - High-volume use case optimization
     * - Extended thinking mode for complex problems
     * - Task-specific sub-agent functionality
     * - Multimodal understanding (text and images)
     * - Tool/function calling with parallel execution
     * - Precise instruction following
     */
    public val AnthropicClaude4Sonnet: LLModel = BedrockModel(
        AnthropicModels.Sonnet_4,
        "anthropic.claude-sonnet-4-20250514-v1:0",
    ).effectiveModel

    /**
     * Claude 3 Sonnet - Balanced performance model ideal for most use cases
     *
     * This model offers:
     * - Excellent balance of intelligence and speed
     * - Strong performance on reasoning tasks
     * - Multimodal capabilities
     * - Tool/function calling support
     * - Cost-effective for production use
     */
    public val AnthropicClaude3Sonnet: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "anthropic.claude-3-sonnet-20240229-v1:0",
            capabilities = fullCapabilities,
            contextLength = 200_000,
            maxOutputTokens = 4_096,
        ),
    ).effectiveModel

    /**
     * Claude 3.5 Sonnet v2 - Upgraded model with improved intelligence and capabilities
     *
     * This model offers:
     * - Enhanced coding and reasoning capabilities
     * - Improved agentic workflows
     * - Computer use capabilities (beta)
     * - Advanced tool/function calling
     * - Better software development lifecycle support
     * - Multimodal understanding with vision
     */
    public val AnthropicClaude35SonnetV2: LLModel = BedrockModel(
        AnthropicModels.Sonnet_3_5,
        "anthropic.claude-3-5-sonnet-20241022-v2:0",
    ).effectiveModel

    /**
     * Claude 3.5 Haiku - Fast model with improved reasoning capabilities
     *
     * This model combines:
     * - Rapid response times with intelligence
     * - Performance matching Claude 3 Opus on many benchmarks
     * - Strong coding capabilities
     * - Cost-effective for high-volume use cases
     * - Entry-level user-facing products
     * - Specialized sub-agent tasks
     * - Processing large volumes of data
     */
    public val AnthropicClaude35Haiku: LLModel = BedrockModel(
        AnthropicModels.Haiku_3_5,
        "anthropic.claude-3-5-haiku-20241022-v1:0",
    ).effectiveModel

    /**
     * Claude 3 Haiku - Fast and efficient model for high-volume, simple tasks
     *
     * This model is optimized for:
     * - Quick responses
     * - High-volume processing
     * - Basic reasoning and comprehension
     * - Multimodal understanding
     * - Tool/function calling
     */
    public val AnthropicClaude3Haiku: LLModel = BedrockModel(
        AnthropicModels.Haiku_3,
        "anthropic.claude-3-haiku-20240307-v1:0",
    ).effectiveModel

    /**
     * Claude 2.1 - Previous generation Claude model with 200K context window
     *
     * Features:
     * - Extended context window (200K tokens)
     * - Strong reasoning capabilities
     * - Improved accuracy over Claude 2.0
     * - Text-only (no vision support)
     * - No tool calling support
     */
    public val AnthropicClaude21: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "anthropic.claude-v2:1",
            capabilities = standardCapabilities,
            contextLength = 200_000,
        ),
    ).effectiveModel

    /**
     * Claude Instant - Fast, affordable model for simple tasks
     *
     * Optimized for:
     * - Quick responses
     * - Simple Q&A and text tasks
     * - High-volume applications
     * - Cost-sensitive use cases
     */
    public val AnthropicClaudeInstant: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "anthropic.claude-instant-v1",
            capabilities = standardCapabilities,
            contextLength = 100_000,
        ),
    ).effectiveModel

    /**
     * Amazon Nova Micro - Ultra-fast, low-cost model for simple tasks
     *
     * Amazon's most cost-effective model for:
     * - Simple text generation
     * - Basic Q&A tasks
     * - High-volume applications
     * - Quick responses
     *
     * @see <a href="assets.amazon.science/96/7d/0d3e59514abf8fdcfafcdc574300/nova-tech-report-20250317-0810.pdf">
     */
    public val AmazonNovaMicro: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.nova-micro-v1:0",
            capabilities = novaCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Amazon Nova Lite - Balanced performance and cost
     *
     * Optimized for:
     * - General text tasks
     * - Moderate complexity reasoning
     * - Cost-sensitive applications
     * - Good balance of speed and quality
     *
     * @see <a href="assets.amazon.science/96/7d/0d3e59514abf8fdcfafcdc574300/nova-tech-report-20250317-0810.pdf">
     */
    public val AmazonNovaLite: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.nova-lite-v1:0",
            capabilities = novaCapabilities,
            contextLength = 300_000,
        ),
    ).effectiveModel

    /**
     * Amazon Nova Pro - High-performance model for complex tasks
     *
     * Amazon's advanced model for:
     * - Complex reasoning
     * - Long-form content generation
     * - Advanced text understanding
     * - Professional use cases
     *
     * @see <a href="assets.amazon.science/96/7d/0d3e59514abf8fdcfafcdc574300/nova-tech-report-20250317-0810.pdf">
     */
    public val AmazonNovaPro: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.nova-pro-v1:0",
            capabilities = novaCapabilities,
            contextLength = 300_000,
        ),
    ).effectiveModel

    /**
     * Amazon Nova Premier - Amazon's most capable model
     *
     * Amazon's flagship model for:
     * - Most complex reasoning tasks
     * - Highest quality outputs
     * - Enterprise applications
     * - Mission-critical use cases
     *
     * @see <a href="assets.amazon.science/e5/e6/ccc5378c42dca467d1abe1628ec9/amazon-nova-premier-technical-report-and-model-card.pdf">
     */
    public val AmazonNovaPremier: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.nova-premier-v1:0",
            capabilities = novaCapabilities,
            contextLength = 1_000_000,
        ),
    ).effectiveModel

    /**
     * Jamba Large - AI21's most powerful hybrid SSM-Transformer model
     *
     * Excels at:
     * - Complex language understanding
     * - Long-form content generation
     * - Reasoning tasks
     * - Following complex instructions
     * - Tool/function calling
     * - Large context windows (up to 256K tokens)
     *
     * @see <a href="huggingface.co/ai21labs/AI21-Jamba-Large-1.5">
     */
    public val AI21JambaLarge: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "ai21.jamba-1-5-large-v1:0",
            capabilities = toolCapabilities,
            contextLength = 256_000,
        ),
    ).effectiveModel

    /**
     * Jamba Mini - AI21's efficient hybrid SSM-Transformer model
     *
     * Good for:
     * - General text generation
     * - Moderate complexity tasks
     * - Cost-effective production use
     * - Tool/function calling
     * - Faster inference speeds
     *
     * @see <a href="huggingface.co/ai21labs/AI21-Jamba-Mini-1.5">
     */
    public val AI21JambaMini: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "ai21.jamba-1-5-mini-v1:0",
            capabilities = toolCapabilities,
            contextLength = 256_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3 8B Instruct
     *
     * Features:
     * - 8 billion parameters
     * - Llama 3 architecture
     * - Strong instruction following
     * - Efficient performance
     *
     * @see <a href="huggingface.co/meta-llama/Meta-Llama-3-8B-Instruct">
     */
    public val MetaLlama3_0_8BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-8b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 8_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3 architecture
     * - Superior instruction following
     * - Advanced reasoning
     *
     * @see <a href="huggingface.co/meta-llama/Meta-Llama-3-70B-Instruct">
     */
    public val MetaLlama3_0_70BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-70b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 8_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.1 8B Instruct
     *
     * Features:
     * - 8 billion parameters
     * - Llama 3.1 architecture
     * - Strong instruction following
     * - Efficient performance
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.1-8B-Instruct">
     */
    public val MetaLlama3_1_8BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-1-8b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.1 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3.1 architecture
     * - Superior instruction following
     * - Advanced reasoning
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.1-70B-Instruct">
     */
    public val MetaLlama3_1_70BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-1-70b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.1 405B Instruct
     *
     * Features:
     * - 405 billion parameters
     * - Llama 3.1 architecture
     * - Superior instruction following
     * - Advanced reasoning
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.1-405B-Instruct">
     */
    public val MetaLlama3_1_405BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-1-405b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.2 1B Instruct
     *
     * Features:
     * - 1 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.2-1B-Instruct">
     */
    public val MetaLlama3_2_1BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-2-1b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.2 3B Instruct
     *
     * Features:
     * - 3 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.2-3B-Instruct">
     */
    public val MetaLlama3_2_3BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-2-3b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.2 11B Instruct
     *
     * Features:
     * - 11 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.2-11B-Vision-Instruct">
     */
    public val MetaLlama3_2_11BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-2-11b-instruct-v1:0",
            capabilities = fullCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.2 90B Instruct
     *
     * Features:
     * - 90 billion parameters
     * - Llama 3.2 architecture
     * - Superior instruction following
     * - Advanced reasoning
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.2-90B-Vision-Instruct">
     */
    public val MetaLlama3_2_90BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-2-90b-instruct-v1:0",
            capabilities = fullCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel

    /**
     * Meta's Llama 3.3 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3.3 architecture
     * - Superior instruction following
     * - Advanced reasoning
     *
     * @see <a href="huggingface.co/meta-llama/Llama-3.3-70B-Instruct">
     */
    public val MetaLlama3_3_70BInstruct: LLModel = BedrockModel(
        LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-3-70b-instruct-v1:0",
            capabilities = standardCapabilities,
            contextLength = 128_000,
        ),
    ).effectiveModel
}

/**
 * Extension method to create a new LLModel with a different inference profile prefix.
 * This allows you to easily switch inference profiles for predefined Bedrock models.
 *
 * @param inferencePrefix The inference profile prefix to use (e.g., "eu", "us", "ap")
 * @return A new LLModel with the specified inference profile prefix
 *
 * Example usage:
 * ```kotlin
 * // Use EU inference profile instead of default US
 * val euModel = BedrockModels.AnthropicClaude4Sonnet.withInferenceProfile(BedrockInferencePrefixes.EU.prefix)
 * // euModel.id will be "eu.anthropic.claude-sonnet-4-20250514-v1:0"
 * ```
 */
public fun LLModel.withInferenceProfile(inferencePrefix: String): LLModel {
    require(provider == LLMProvider.Bedrock) {
        "withInferencePrefix() can only be used with Bedrock models, but model provider is $provider"
    }

    val baseModelId = if (id.contains('.')) {
        val potentialPrefix = id.substringBefore('.')
        val validPrefixes = BedrockInferencePrefixes.entries.map { it.prefix }

        if (potentialPrefix in validPrefixes) {
            id.substringAfter('.')
        } else {
            id
        }
    } else {
        id
    }

    return copy(id = "$inferencePrefix.$baseModelId")
}
