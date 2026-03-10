package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockGuardrailsSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModelFamilies
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor.FallbackPromptExecutorSettings
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import io.ktor.client.HttpClient
import kotlin.time.Clock

/**
 * Builder for creating instances of [PromptExecutor] with configurable settings.
 */
@JavaAPI
public class PromptExecutorBuilder {
    private val clients: MutableList<LLMClient> = mutableListOf()
    private var fallbackSettings: FallbackPromptExecutorSettings? = null

    /**
     * Adds a new LLM client to the builder.
     *
     * @param client The LLM client to add.
     * @return The updated instance of [PromptExecutorBuilder].
     */
    public fun addClient(client: LLMClient): PromptExecutorBuilder = apply {
        clients += client
    }

    /**
     * Adds an OpenAI client to the `PromptExecutorBuilder`.
     *
     * This method configures and registers an OpenAI LLM client with the given API key and optional settings.
     * It allows customization of the client by specifying settings, an HTTP client, a clock, and a tool descriptor converter.
     *
     * @param apiKey The API key used to authenticate requests to the OpenAI API.
     * @param settings The configuration settings for the OpenAI client. Defaults to a new instance of `OpenAIClientSettings`.
     * @param baseClient The underlying HTTP client used for making API requests. Defaults to a new instance of `HttpClient`.
     * @param clock The clock used for time-related operations. Defaults to the system clock.
     * @param toolsConverter An instance of a tool descriptor schema generator for compatibility with OpenAI tools. Defaults to `OpenAICompatibleToolDescriptorSchemaGenerator`.
     * @return An updated instance of `PromptExecutorBuilder` with the OpenAI client added.
     */
    @JvmOverloads
    public fun openAI(
        apiKey: String,
        settings: OpenAIClientSettings = OpenAIClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): PromptExecutorBuilder = apply {
        clients += OpenAILLMClient(apiKey, settings, baseClient, clock, toolsConverter)
    }

    /**
     * Adds an Anthropic LLM client to the `PromptExecutorBuilder`.
     *
     * This method integrates an Anthropic client into the builder, enabling it to interact
     * with the Anthropic API for prompt execution. Configuration settings for the client,
     * such as the API key and additional client settings, can be specified.
     *
     * @param apiKey The API key used to authenticate requests with the Anthropic API.
     * @param settings The settings for the Anthropic client, including model configuration,
     * base URL, and API version. Defaults to a new instance of `AnthropicClientSettings`.
     * @param baseClient The underlying HTTP client used for making API requests. Defaults to `HttpClient()`.
     * @param clock The clock instance used for timestamp generation and time-related operations.
     * Defaults to `Clock.System`.
     * @return The updated instance of `PromptExecutorBuilder`, allowing for method chaining.
     */
    @JvmOverloads
    public fun anthropic(
        apiKey: String,
        settings: AnthropicClientSettings = AnthropicClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System
    ): PromptExecutorBuilder = apply {
        clients += AnthropicLLMClient(apiKey, settings, baseClient, clock)
    }

    /**
     * Adds a Google LLM client to the `PromptExecutorBuilder` with the specified API key,
     * settings, HTTP client, and clock.
     *
     * @param apiKey The API key used to authenticate with the Google API.
     * @param settings Optional configuration settings for the Google AI client. Defaults to a new instance of [GoogleClientSettings].
     * @param baseClient Optional HTTP client to use for API communication. Defaults to a new instance of [HttpClient].
     * @param clock Optional clock implementation to manage timing and timestamps. Defaults to [Clock.System].
     * @return The updated instance of [PromptExecutorBuilder] for chaining further method calls.
     */
    @JvmOverloads
    public fun google(
        apiKey: String,
        settings: GoogleClientSettings = GoogleClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System
    ): PromptExecutorBuilder = apply {
        clients += GoogleLLMClient(apiKey, settings, baseClient, clock)
    }

    /**
     * Adds a Bedrock client to the `PromptExecutorBuilder` with optional settings
     * such as API method, moderation guardrails, fallback model family, and clock.
     *
     * @param bedrockClient The BedrockRuntimeClient instance used for Bedrock API interactions.
     * @param apiMethod The Bedrock API method to use, defaulting to `BedrockAPIMethod.InvokeModel`.
     * @param moderationGuardrailsSettings Optional settings for configuring moderation guardrails.
     * @param fallbackModelFamily The family of fallback models to be used, if configured.
     * @param clock The clock instance to use for time-based operations, defaulting to `Clock.System`.
     * @return The updated instance of `PromptExecutorBuilder` with the Bedrock client added.
     */
    @JvmOverloads
    public fun bedrock(
        bedrockClient: BedrockRuntimeClient,
        apiMethod: BedrockAPIMethod = BedrockAPIMethod.InvokeModel,
        moderationGuardrailsSettings: BedrockGuardrailsSettings? = null,
        fallbackModelFamily: BedrockModelFamilies? = null,
        clock: Clock = Clock.System,
    ): PromptExecutorBuilder = apply {
        clients += BedrockLLMClient(bedrockClient, apiMethod, moderationGuardrailsSettings, fallbackModelFamily, clock)
    }

    /**
     * Adds a new AWS Bedrock-based LLM client to the `PromptExecutorBuilder`.
     *
     * This method configures the builder to use an AWS Bedrock client, which can interact with AWS Bedrock models.
     * It's designed for cases where you need to authenticate using an `IdentityProvider` instance.
     *
     * @param identityProvider The identity provider responsible for obtaining AWS credentials.
     *                         This is typically required for authenticating requests against AWS services.
     * @param settings Configuration options for the AWS Bedrock client, including region, timeout, and API method.
     *                 Defaults to a new instance of [BedrockClientSettings] with default parameters.
     * @param clock An optional clock instance used for managing time-sensitive operations. Defaults to [Clock.System].
     * @return The updated instance of [PromptExecutorBuilder] with the configured AWS Bedrock client.
     */
    @JvmOverloads
    public fun bedrock(
        identityProvider: IdentityProvider,
        settings: BedrockClientSettings = BedrockClientSettings(),
        clock: Clock = Clock.System,
    ): PromptExecutorBuilder = apply {
        clients += BedrockLLMClient(identityProvider, settings, clock)
    }

    /**
     * Adds a DeepSeek LLM client to the `PromptExecutorBuilder` and allows for method chaining.
     *
     * @param apiKey The API key for authenticating with the DeepSeek API.
     * @param settings The configuration settings for the DeepSeek client. Defaults to a new instance of `DeepSeekClientSettings`.
     * @param baseClient The HTTP client to use for making requests. Defaults to a new instance of `HttpClient`.
     * @param clock The clock instance used for time-sensitive operations. Defaults to the system clock.
     * @param toolsConverter The tool descriptor schema generator for converting tools to a format compatible with OpenAI. Defaults to an instance of `OpenAICompatibleToolDescriptor
     * SchemaGenerator`.
     * @return The updated instance of `PromptExecutorBuilder`, allowing for method chaining.
     */
    @JvmOverloads
    public fun deepseek(
        apiKey: String,
        settings: DeepSeekClientSettings = DeepSeekClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
    ): PromptExecutorBuilder = apply {
        clients += DeepSeekLLMClient(apiKey, settings, baseClient, clock, toolsConverter)
    }

    /**
     * Adds a Mistral AI client to the `PromptExecutorBuilder`.
     *
     * This method integrates a Mistral AI client into the builder, enabling the use of
     * Mistral AI's language model and tool descriptor schema within the `PromptExecutor`.
     *
     * @param apiKey The API key required to authenticate with the Mistral AI service.
     * @param settings Optional settings for configuring the Mistral AI client. Defaults to a
     *        new instance of [MistralAIClientSettings].
     * @param baseClient The HTTP client used for making requests to the Mistral AI API.
     *        Defaults to a new instance of [HttpClient].
     * @param clock The clock instance used to manage time-sensitive operations.
     *        Defaults to [Clock.System].
     * @param toolsConverter The tool descriptor schema generator for compatibility with
     *        OpenAI-like tool formats. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return The updated instance of [PromptExecutorBuilder].
     */
    @JvmOverloads
    public fun mistral(
        apiKey: String,
        settings: MistralAIClientSettings = MistralAIClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
    ): PromptExecutorBuilder = apply {
        clients += MistralAILLMClient(apiKey, settings, baseClient, clock, toolsConverter)
    }

    /**
     * Adds an Ollama client to the `PromptExecutorBuilder` with the specified configuration parameters.
     *
     * This method provides default values for its parameters, simplifying the process of adding an Ollama client
     * to the builder while allowing customization when needed.
     *
     * @param baseUrl The base URL of the Ollama server. Defaults to "http://localhost:11434".
     * @param baseClient The `HttpClient` instance used for network operations. Defaults to a new `HttpClient` instance.
     * @param timeoutConfig A `ConnectionTimeoutConfig` instance specifying timeout values for the client. Defaults to the default timeout configuration.
     * @param clock A `Clock` instance used to provide the current time. Defaults to the system clock.
     * @param contextWindowStrategy A `ContextWindowStrategy` defining the strategy for handling context windows. Defaults to `ContextWindowStrategy.None`.
     * @param toolDescriptorConverter A `ToolDescriptorSchemaGenerator` used to generate tool descriptor schemas. Defaults to an `OllamaToolDescriptorSchemaGenerator`.
     * @return The updated instance of `PromptExecutorBuilder`.
     */
    @JvmOverloads
    public fun ollama(
        baseUrl: String = "http://localhost:11434",
        baseClient: HttpClient = HttpClient(),
        timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
        clock: Clock = Clock.System,
        contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategy.Companion.None,
        toolDescriptorConverter: ToolDescriptorSchemaGenerator = OllamaToolDescriptorSchemaGenerator()
    ): PromptExecutorBuilder = apply {
        clients += OllamaClient(
            baseUrl,
            baseClient,
            timeoutConfig,
            clock,
            contextWindowStrategy,
            toolDescriptorConverter
        )
    }

    /**
     * Adds an OpenRouter LLM client to the `PromptExecutorBuilder`.
     *
     * This method configures an instance of `OpenRouterLLMClient` with the provided API key, settings,
     * and optional parameters such as a custom HTTP client, clock, and tools converter. The created
     * client is added to the internal list of clients managed by the builder.
     *
     * @param apiKey The API key used to authenticate with the OpenRouter API.
     * @param settings The configuration settings for the OpenRouter client. Defaults to a new instance of `OpenRouterClientSettings`.
     * @param baseClient The HTTP client used for network communication with the OpenRouter API. Defaults to a new instance of `HttpClient`.
     * @param clock The clock instance used for time-related operations. Defaults to `Clock.System`.
     * @param toolsConverter The tool descriptor schema generator for OpenRouter tools. Defaults to an instance of `OpenAICompatibleToolDescriptorSchemaGenerator`.
     * @return The updated instance of `PromptExecutorBuilder` for method chaining.
     */
    @JvmOverloads
    public fun openRouter(
        apiKey: String,
        settings: OpenRouterClientSettings = OpenRouterClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): PromptExecutorBuilder = apply {
        clients += OpenRouterLLMClient(apiKey, settings, baseClient, clock, toolsConverter)
    }

    /**
     * Adds a Dashscope client to the `PromptExecutorBuilder` with the specified API key and optional configurations.
     *
     * @param apiKey The API key for authenticating with the Dashscope service.
     * @param settings Configuration settings for the Dashscope client, including base URL and timeout settings. Uses default values if not provided.
     * @param baseClient The base HTTP client for handling network requests. Defaults to a new instance of `HttpClient`.
     * @param clock The clock instance used for managing time-related operations. Defaults to `Clock.System`.
     * @param toolsConverter The converter for transforming tool descriptor schemas to an OpenAI-compatible format. Defaults to `OpenAICompatibleToolDescriptorSchemaGenerator`.
     * @return The current instance of `PromptExecutorBuilder`, allowing for method chaining.
     */
    @JvmOverloads
    public fun dashscope(
        apiKey: String,
        settings: DashscopeClientSettings = DashscopeClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): PromptExecutorBuilder = apply {
        clients += DashscopeLLMClient(apiKey, settings, baseClient, clock, toolsConverter)
    }

    /**
     * Configures the fallback settings for the `PromptExecutorBuilder`.
     *
     * This method allows you to specify a `FallbackPromptExecutorSettings` instance, which contains
     * the fallback provider and model to be used in case the primary execution fails.
     *
     * @param settings An instance of `FallbackPromptExecutorSettings` that defines the configuration
     * for fallback execution, including the fallback provider and model.
     * @return The current instance of `PromptExecutorBuilder`, allowing for method chaining.
     */
    public fun fallback(settings: FallbackPromptExecutorSettings): PromptExecutorBuilder = apply {
        fallbackSettings = settings
    }

    /**
     * Builds and returns an instance of [PromptExecutor] based on the configured clients and fallback settings.
     *
     * This method determines the appropriate executor type to construct:
     * - If no clients are added, an [IllegalArgumentException] is thrown.
     * - If a single client is added, a [SingleLLMPromptExecutor] is returned.
     * - If multiple clients are added, a [MultiLLMPromptExecutor] is returned, using the configured clients
     *   and optional fallback settings.
     *
     * @return A [PromptExecutor] instance configured with the added clients and fallback settings.
     * @throws IllegalArgumentException if no clients have been added to the builder.
     */
    public fun build(): PromptExecutor = when (clients.size) {
        0 -> throw IllegalArgumentException("At least one client must be added to the builder")
        1 -> SingleLLMPromptExecutor(clients.single())
        else -> MultiLLMPromptExecutor(
            llmClients = clients.map { it.llmProvider() to it }.toTypedArray(),
            fallback = fallbackSettings
        )
    }
}
