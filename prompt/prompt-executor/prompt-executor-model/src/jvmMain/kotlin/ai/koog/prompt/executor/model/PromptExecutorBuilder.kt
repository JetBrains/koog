@file:OptIn(ExperimentalRoutingApi::class)

package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
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
import ai.koog.prompt.executor.llms.ExperimentalRoutingApi
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient
import kotlin.time.Clock

/**
 * Builder for constructing a [PromptExecutor] that automatically selects the appropriate executor
 * implementation based on the registered clients.
 *
 * **Executor selection heuristic** (determined at [build] time):
 * - If every registered provider appears exactly once, a [MultiLLMPromptExecutor] is created.
 *   It dispatches each request to the single client registered for the requested model's provider.
 * - If any provider has more than one client registered, a [RoutingLLMPromptExecutor] is created.
 *   It load-balances requests across all clients for the same provider.
 *
 * Obtain an instance through [PromptExecutor.builder].
 *
 * Example usage in Java:
 * ```java
 * // Two distinct providers → MultiLLMPromptExecutor
 * PromptExecutor executor = PromptExecutor.builder()
 *     .addClient(openAIClient)
 *     .addClient(anthropicClient)
 *     .build();
 *
 * // Two clients for the same provider → RoutingLLMPromptExecutor (load balanced)
 * PromptExecutor executor = PromptExecutor.builder()
 *     .addClient(firstOpenAIClient)
 *     .addClient(secondOpenAIClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.builder
 * @see MultiLLMPromptExecutor
 * @see RoutingLLMPromptExecutor
 */
@JavaAPI
public class PromptExecutorBuilder {
    private val clients: MutableList<LLMClient> = mutableListOf()
    private var fallbackModel: LLModel? = null

    /**
     * Registers an additional [LLMClient].
     *
     * Multiple clients for the same provider are allowed. When more than one client is registered
     * for the same provider, [build] will create a [RoutingLLMPromptExecutor] that load-balances
     * across them.
     *
     * @param client The LLM client to add.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): PromptExecutorBuilder = apply {
        clients += client
    }

    /**
     * Adds an OpenAI client.
     *
     * Multiple OpenAI clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(OpenAILLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds an Anthropic client.
     *
     * Multiple Anthropic clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(AnthropicLLMClient(apiKey, settings, baseClient, clock))
    }

    /**
     * Adds a Google AI client.
     *
     * Multiple Google clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(GoogleLLMClient(apiKey, settings, baseClient, clock))
    }

    /**
     * Adds a DeepSeek client.
     *
     * Multiple DeepSeek clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(DeepSeekLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds a Mistral AI client.
     *
     * Multiple Mistral clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(MistralAILLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds an Ollama client.
     *
     * Multiple Ollama clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
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
     * Multiple OpenRouter clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(OpenRouterLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Adds a Dashscope client.
     *
     * Multiple Dashscope clients are allowed — adding more than one will result in
     * a [RoutingLLMPromptExecutor] being created at [build] time.
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
    ): PromptExecutorBuilder = apply {
        addClient(DashscopeLLMClient(apiKey, settings, baseClient, clock, toolsConverter))
    }

    /**
     * Configures a fallback model to use when no client is registered for the requested model's provider.
     *
     * The fallback model's provider must already be registered via [addClient]; otherwise [build] will throw.
     *
     * @param model The model to use as a fallback.
     * @return This builder instance for chaining.
     */
    public fun fallback(model: LLModel): PromptExecutorBuilder = apply {
        fallbackModel = model
    }

    /**
     * Constructs a [PromptExecutor] from the registered clients.
     *
     * The concrete implementation is chosen automatically:
     * - [MultiLLMPromptExecutor] when each provider appears at most once.
     * - [RoutingLLMPromptExecutor] when any provider has two or more clients (enables load balancing).
     *
     * @return A configured [PromptExecutor] instance.
     * @throws IllegalArgumentException if a fallback model was configured but its provider has no registered client.
     */
    public fun build(): PromptExecutor {
        require(clients.isNotEmpty()) {
            "At least one LLM client must be added to PromptExecutorBuilder"
        }
        fallbackModel?.provider?.let { fallbackProvider ->
            require(clients.any { it.llmProvider() == fallbackProvider }) {
                "Fallback model provider '$fallbackProvider' is not registered. " +
                    "Add a client for this provider before setting it as fallback."
            }
        }
        return if (shouldUseRouting()) {
            RoutingLLMPromptExecutor(
                clients,
                fallbackModel?.let { RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(it) }
            )
        } else {
            MultiLLMPromptExecutor(
                clients,
                fallbackModel?.let { MultiLLMPromptExecutor.FallbackPromptExecutorSettings(it.provider, it) }
            )
        }
    }

    private fun shouldUseRouting(): Boolean {
        val visitedProviders = mutableSetOf<LLMProvider>()
        clients.forEach { client ->
            if (client.llmProvider() in visitedProviders) return true
            visitedProviders.add(client.llmProvider())
        }
        return false
    }
}
