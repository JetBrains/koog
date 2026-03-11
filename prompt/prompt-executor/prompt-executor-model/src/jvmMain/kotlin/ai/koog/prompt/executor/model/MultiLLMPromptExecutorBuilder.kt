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
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import io.ktor.client.HttpClient
import kotlin.time.Clock

/**
 * Builder for creating [MultiLLMPromptExecutor] instances with multiple LLM providers.
 *
 * Supports one client per provider. If a client for the same provider is added more than once,
 * the last registration takes precedence and replaces the previous one.
 *
 * Use [PromptExecutor.multiExecutorBuilder] to obtain an instance.
 *
 * Example usage in Java:
 * ```java
 * PromptExecutor executor = PromptExecutor.multiExecutorBuilder()
 *     .openAI("openai-api-key")
 *     .anthropic("anthropic-api-key")
 *     .withFallback(OpenAIModels.Chat.GPT4o)
 *     .build();
 * ```
 *
 * @see PromptExecutor.multiExecutorBuilder
 * @see RoutingPromptExecutorBuilder
 */
@JavaAPI
public class MultiLLMPromptExecutorBuilder {
    private val clients: LinkedHashMap<LLMProvider, LLMClient> = LinkedHashMap()
    private var fallbackSettings: MultiLLMPromptExecutor.FallbackPromptExecutorSettings? = null

    /**
     * Adds a custom [LLMClient] to the builder.
     *
     * If a client for the same provider was already registered, it will be replaced by this one.
     *
     * @param client The LLM client to register.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): MultiLLMPromptExecutorBuilder = apply {
        clients[client.llmProvider()] = client
    }

    /**
     * Adds an OpenAI client.
     *
     * If a client for the OpenAI provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the OpenAI API.
     * @param settings Configuration settings for the OpenAI client. Defaults to [OpenAIClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param toolsConverter Tool descriptor schema generator for OpenAI. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun openAI(
        apiKey: String,
        settings: OpenAIClientSettings = OpenAIClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(OpenAILLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds an Anthropic client.
     *
     * If a client for the Anthropic provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the Anthropic API.
     * @param settings Configuration settings for the Anthropic client. Defaults to [AnthropicClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun anthropic(
        apiKey: String,
        settings: AnthropicClientSettings = AnthropicClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(AnthropicLLMClient(apiKey, settings, baseClient, clock))
    }

    /**
     * Adds a Google AI client.
     *
     * If a client for the Google provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the Google AI API.
     * @param settings Configuration settings for the Google client. Defaults to [GoogleClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun google(
        apiKey: String,
        settings: GoogleClientSettings = GoogleClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(GoogleLLMClient(apiKey, settings, baseClient, clock))
    }

    /**
     * Adds an AWS Bedrock client using a pre-built [BedrockRuntimeClient].
     *
     * If a client for the Bedrock provider was already registered, it will be replaced.
     *
     * @param bedrockClient The AWS Bedrock runtime client.
     * @param apiMethod The Bedrock API method to use. Defaults to [BedrockAPIMethod.InvokeModel].
     * @param moderationGuardrailsSettings Optional guardrails settings for moderation.
     * @param fallbackModelFamily Optional fallback model family.
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun bedrock(
        bedrockClient: BedrockRuntimeClient,
        apiMethod: BedrockAPIMethod = BedrockAPIMethod.InvokeModel,
        moderationGuardrailsSettings: BedrockGuardrailsSettings? = null,
        fallbackModelFamily: BedrockModelFamilies? = null,
        clock: Clock = Clock.System,
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(BedrockLLMClient(bedrockClient, apiMethod, moderationGuardrailsSettings, fallbackModelFamily, clock))
    }

    /**
     * Adds an AWS Bedrock client using an [IdentityProvider] for authentication.
     *
     * If a client for the Bedrock provider was already registered, it will be replaced.
     *
     * @param identityProvider The AWS identity provider for obtaining credentials.
     * @param settings Configuration settings for the Bedrock client. Defaults to [BedrockClientSettings].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun bedrock(
        identityProvider: IdentityProvider,
        settings: BedrockClientSettings = BedrockClientSettings(),
        clock: Clock = Clock.System,
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(BedrockLLMClient(identityProvider, settings, clock))
    }

    /**
     * Adds a DeepSeek client.
     *
     * If a client for the DeepSeek provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the DeepSeek API.
     * @param settings Configuration settings for the DeepSeek client. Defaults to [DeepSeekClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param toolsConverter Tool descriptor schema generator. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun deepseek(
        apiKey: String,
        settings: DeepSeekClientSettings = DeepSeekClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(DeepSeekLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds a Mistral AI client.
     *
     * If a client for the Mistral AI provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the Mistral AI API.
     * @param settings Configuration settings for the Mistral AI client. Defaults to [MistralAIClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param toolsConverter Tool descriptor schema generator. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun mistral(
        apiKey: String,
        settings: MistralAIClientSettings = MistralAIClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(MistralAILLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds an Ollama client.
     *
     * If a client for the Ollama provider was already registered, it will be replaced.
     *
     * @param baseUrl The base URL of the Ollama server. Defaults to `http://localhost:11434`.
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param timeoutConfig Connection timeout configuration. Defaults to [ConnectionTimeoutConfig].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param contextWindowStrategy Strategy for managing the context window. Defaults to [ContextWindowStrategy.None].
     * @param toolDescriptorConverter Tool descriptor schema generator. Defaults to [OllamaToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun ollama(
        baseUrl: String = "http://localhost:11434",
        baseClient: HttpClient = HttpClient(),
        timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
        clock: Clock = Clock.System,
        contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategy.Companion.None,
        toolDescriptorConverter: ToolDescriptorSchemaGenerator = OllamaToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(
            OllamaClient(
                baseUrl,
                baseClient,
                timeoutConfig,
                clock,
                contextWindowStrategy,
                toolDescriptorConverter
            )
        )
    }

    /**
     * Adds an OpenRouter client.
     *
     * If a client for the OpenRouter provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the OpenRouter API.
     * @param settings Configuration settings for the OpenRouter client. Defaults to [OpenRouterClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param toolsConverter Tool descriptor schema generator. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun openRouter(
        apiKey: String,
        settings: OpenRouterClientSettings = OpenRouterClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(OpenRouterLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds a Dashscope client.
     *
     * If a client for the Dashscope provider was already registered, it will be replaced.
     *
     * @param apiKey The API key for authenticating with the Dashscope API.
     * @param settings Configuration settings for the Dashscope client. Defaults to [DashscopeClientSettings].
     * @param baseClient The HTTP client used for API requests. Defaults to a new [HttpClient].
     * @param clock The clock used for time-related operations. Defaults to [Clock.System].
     * @param toolsConverter Tool descriptor schema generator. Defaults to [OpenAICompatibleToolDescriptorSchemaGenerator].
     * @return This builder instance for chaining.
     */
    @JvmOverloads
    public fun dashscope(
        apiKey: String,
        settings: DashscopeClientSettings = DashscopeClientSettings(),
        baseClient: HttpClient = HttpClient(),
        clock: Clock = Clock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): MultiLLMPromptExecutorBuilder = apply {
        addClient(DashscopeLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Configures a fallback to be used when no client is registered for the requested model's provider.
     *
     * The fallback model's provider must be registered in this builder; otherwise [build] will throw.
     *
     * @param fallback Fallback settings to use.
     * @return This builder instance for chaining.
     */
    public fun withFallback(
        fallback: MultiLLMPromptExecutor.FallbackPromptExecutorSettings
    ): MultiLLMPromptExecutorBuilder = apply {
        fallbackSettings = fallback
    }

    /**
     * Configures a fallback model to be used when no client is registered for the requested model's provider.
     *
     * The fallback model's provider must be registered in this builder; otherwise [build] will throw.
     *
     * @param model The model to use as fallback.
     * @return This builder instance for chaining.
     */
    public fun withFallback(model: LLModel): MultiLLMPromptExecutorBuilder =
        withFallback(MultiLLMPromptExecutor.FallbackPromptExecutorSettings(model.provider, model))

    /**
     * Builds a [MultiLLMPromptExecutor] with the configured clients and optional fallback.
     *
     * @return A configured [PromptExecutor] instance.
     * @throws IllegalArgumentException if no clients were added.
     * @throws IllegalArgumentException if a fallback model was set but its provider is not registered.
     */
    public fun build(): PromptExecutor {
        require(clients.isNotEmpty()) {
            "At least one LLM client must be added to MultiLLMPromptExecutorBuilder"
        }
        fallbackSettings?.let {
            require(it.fallbackProvider in clients) {
                "Fallback model provider '${it.fallbackProvider}' is not registered. " +
                    "Add a client for this provider before setting it as fallback."
            }
        }
        return MultiLLMPromptExecutor(clients, fallbackSettings)
    }
}
