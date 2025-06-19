package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

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
        LLMCapability.Schema.JSON.Full
    )

    // Full capabilities (multimodal + tools)
    private val fullCapabilities: List<LLMCapability> = standardCapabilities + listOf(
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Schema.JSON.Full,
        LLMCapability.Vision.Image
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
    public val AnthropicClaude3Opus: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-3-opus-20240229-v1:0",
        capabilities = fullCapabilities
    )

    /**
     * Claude 4 Opus - Anthropic's most powerful and intelligent model yet
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
    public val AnthropicClaude4Opus: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-opus-4-20250514-v1:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude4Sonnet: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-sonnet-4-20250514-v1:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude3Sonnet: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-3-sonnet-20240229-v1:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude35SonnetV2: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-3-5-sonnet-20241022-v2:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude35Haiku: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-3-5-haiku-20241022-v1:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude3Haiku: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-3-haiku-20240307-v1:0",
        capabilities = fullCapabilities
    )

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
    public val AnthropicClaude21: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-v2:1",
        capabilities = standardCapabilities
    )

    /**
     * Claude 2.0 - Previous generation Claude model
     *
     * Features:
     * - 100K context window
     * - Good general-purpose performance
     * - Text-only (no vision support)
     * - No tool calling support
     */
    public val AnthropicClaude2: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-v2",
        capabilities = standardCapabilities
    )

    /**
     * Claude Instant - Fast, affordable model for simple tasks
     *
     * Optimized for:
     * - Quick responses
     * - Simple Q&A and text tasks
     * - High-volume applications
     * - Cost-sensitive use cases
     */
    public val AnthropicClaudeInstant: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "anthropic.claude-instant-v1",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Titan Text G1 - Express
     *
     * Amazon's fast, cost-effective model for:
     * - Text generation
     * - Summarization
     * - Q&A tasks
     * - Classification
     */
    public val AmazonTitanTextExpressV1: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.titan-text-express-v1",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Titan Text G1 - Lite
     *
     * Lightweight model optimized for:
     * - Simple text tasks
     * - High-speed inference
     * - Cost-sensitive applications
     */
    public val AmazonTitanTextLiteV1: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.titan-text-lite-v1",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Titan Text G1 - Premier
     *
     * Amazon's advanced model for:
     * - Complex reasoning
     * - Long-form content generation
     * - Advanced text understanding
     */
    public val AmazonTitanTextPremierV1: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.titan-text-premier-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Jurassic-2 Ultra - AI21's most powerful model
     *
     * Excels at:
     * - Complex language understanding
     * - Long-form content generation
     * - Reasoning tasks
     * - Following complex instructions
     */
    public val AI21Jurassic2Ultra: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "ai21.j2-ultra-v1",
        capabilities = standardCapabilities
    )

    /**
     * Jurassic-2 Mid - Balanced performance model
     *
     * Good for:
     * - General text generation
     * - Moderate complexity tasks
     * - Cost-effective production use
     */
    public val AI21Jurassic2Mid: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "ai21.j2-mid-v1",
        capabilities = standardCapabilities
    )

    /**
     * Cohere Command - Text generation model
     *
     * Designed for:
     * - Instruction following
     * - Text generation
     * - Conversational AI
     */
    public val CohereCommand: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "cohere.command-text-v14",
        capabilities = standardCapabilities
    )

    /**
     * Cohere Command Light - Lightweight version
     *
     * Optimized for:
     * - Fast inference
     * - Simple tasks
     * - High-volume applications
     */
    public val CohereCommandLight: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "cohere.command-light-text-v14",
        capabilities = standardCapabilities
    )

    /**
     * Llama 2 70B Chat - Meta's large chat model
     *
     * Features:
     * - 70 billion parameters
     * - Optimized for dialogue
     * - Strong reasoning capabilities
     * - Open-source foundation
     */
    public val MetaLlama2_70BChat: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama2-70b-chat-v1",
        capabilities = standardCapabilities
    )

    /**
     * Llama 2 13B Chat - Meta's medium chat model
     *
     * Features:
     * - 13 billion parameters
     * - Good balance of performance and speed
     * - Dialogue optimization
     */
    public val MetaLlama2_13BChat: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama2-13b-chat-v1",
        capabilities = standardCapabilities
    )

    /**
     * Llama 3 8B Instruct - Meta's latest instruction-tuned model
     *
     * Features:
     * - 8 billion parameters
     * - Latest Llama architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_8BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-8b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Llama 3 70B Instruct - Meta's large instruction-tuned model
     *
     * Features:
     * - 70 billion parameters
     * - Latest Llama architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_70BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-70b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Mistral 7B Instruct - Efficient instruction-following model
     *
     * Features:
     * - 7 billion parameters
     * - Fast inference
     * - Good instruction following
     * - Cost-effective
     */
    public val Mistral7BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "mistral.mistral-7b-instruct-v0:2",
        capabilities = standardCapabilities
    )

    /**
     * Mixtral 8x7B Instruct - Mixture of experts model
     *
     * Features:
     * - Mixture of 8 experts, 7B each
     * - High quality outputs
     * - Efficient routing
     * - Strong performance
     */
    public val MistralMixtral8x7BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "mistral.mixtral-8x7b-instruct-v0:1",
        capabilities = standardCapabilities
    )

    /**
     * Mistral Large - Mistral's most capable model
     *
     * Features:
     * - Top-tier reasoning
     * - Multilingual support
     * - Complex task handling
     * - Tool calling support
     */
    public val MistralLarge: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "mistral.mistral-large-2402-v1:0",
        capabilities = toolCapabilities
    )

    /**
     * Mistral Small - Lightweight but capable model
     *
     * Features:
     * - Fast inference
     * - Good cost-performance ratio
     * - Suitable for most tasks
     */
    public val MistralSmall: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "mistral.mistral-small-2402-v1:0",
        capabilities = standardCapabilities
    )
}
