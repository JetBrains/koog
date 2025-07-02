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
     * Amazon Nova Micro - Ultra-fast, low-cost model for simple tasks
     *
     * Amazon's most cost-effective model for:
     * - Simple text generation
     * - Basic Q&A tasks
     * - High-volume applications
     * - Quick responses
     */
    public val AmazonNovaMicro: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.nova-micro-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Nova Lite - Balanced performance and cost
     *
     * Optimized for:
     * - General text tasks
     * - Moderate complexity reasoning
     * - Cost-sensitive applications
     * - Good balance of speed and quality
     */
    public val AmazonNovaLite: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.nova-lite-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Nova Pro - High-performance model for complex tasks
     *
     * Amazon's advanced model for:
     * - Complex reasoning
     * - Long-form content generation
     * - Advanced text understanding
     * - Professional use cases
     */
    public val AmazonNovaPro: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.nova-pro-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Amazon Nova Premier - Amazon's most capable model
     *
     * Amazon's flagship model for:
     * - Most complex reasoning tasks
     * - Highest quality outputs
     * - Enterprise applications
     * - Mission-critical use cases
     */
    public val AmazonNovaPremier: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "amazon.nova-premier-v1:0",
        capabilities = standardCapabilities
    )

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
     */
    public val AI21JambaLarge: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "ai21.jamba-1-5-large-v1:0",
        capabilities = toolCapabilities
    )

    /**
     * Jamba Mini - AI21's efficient hybrid SSM-Transformer model
     *
     * Good for:
     * - General text generation
     * - Moderate complexity tasks
     * - Cost-effective production use
     * - Tool/function calling
     * - Faster inference speeds
     */
    public val AI21JambaMini: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "ai21.jamba-1-5-mini-v1:0",
        capabilities = toolCapabilities
    )

    /**
     * Meta's Llama 3 8B Instruct
     *
     * Features:
     * - 8 billion parameters
     * - Llama 3 architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_0_8BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-8b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3 architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_0_70BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-70b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.1 8B Instruct
     *
     * Features:
     * - 8 billion parameters
     * - Llama 3.1 architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_1_8BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-1-8b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.1 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3.1 architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_1_70BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-1-70b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.1 405B Instruct
     *
     * Features:
     * - 405 billion parameters
     * - Llama 3.1 architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_1_405BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-1-405b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.2 1B Instruct
     *
     * Features:
     * - 1 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_2_1BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-2-1b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.2 3B Instruct
     *
     * Features:
     * - 3 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_2_3BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-2-3b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.2 11B Instruct
     *
     * Features:
     * - 11 billion parameters
     * - Llama 3.2 architecture
     * - Strong instruction following
     * - Efficient performance
     */
    public val MetaLlama3_2_11BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-2-11b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.2 90B Instruct
     *
     * Features:
     * - 90 billion parameters
     * - Llama 3.2 architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_2_90BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-2-90b-instruct-v1:0",
        capabilities = standardCapabilities
    )

    /**
     * Meta's Llama 3.3 70B Instruct
     *
     * Features:
     * - 70 billion parameters
     * - Llama 3.3 architecture
     * - Superior instruction following
     * - Advanced reasoning
     */
    public val MetaLlama3_3_70BInstruct: LLModel = LLModel(
        provider = LLMProvider.Bedrock,
        id = "meta.llama3-3-70b-instruct-v1:0",
        capabilities = standardCapabilities
    )
}
