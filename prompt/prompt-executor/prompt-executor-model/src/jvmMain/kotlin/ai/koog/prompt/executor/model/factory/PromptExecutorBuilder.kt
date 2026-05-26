package ai.koog.prompt.executor.model.factory

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.builder.MultiLLMPromptExecutorBuilder
import ai.koog.prompt.executor.builder.RoutingLLMPromptExecutorBuilder
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.factory.MultiLLMPromptExecutor
import ai.koog.prompt.executor.factory.RoutingLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock

/**
 * Java-friendly factory for constructing a [PromptExecutor] from registered LLM clients.
 *
 * Distinct from [ai.koog.prompt.executor.model.PromptExecutorBuilder] (the extension-point
 * abstraction); this class is a *factory* whose [build] method selects an appropriate executor
 * implementation based on the registered clients:
 *
 * - If every registered provider appears exactly once, a [MultiLLMPromptExecutor] is created.
 * - If any provider has more than one client registered, a [RoutingLLMPromptExecutorBuilder] is created
 *   (load-balanced across the duplicate clients).
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
 * @see RoutingLLMPromptExecutorBuilder
 */
@JavaAPI
public class PromptExecutorBuilder {
    private val clients: MutableList<LLMClient> = mutableListOf()
    private var fallbackModel: LLModel? = null

    /**
     * Registers an additional [LLMClient].
     *
     * Multiple clients for the same provider are allowed. When more than one client is registered
     * for the same provider, [build] will create a [RoutingLLMPromptExecutorBuilder] that load-balances
     * across them.
     *
     * @param client The LLM client to add.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): PromptExecutorBuilder = apply {
        clients += client
    }

    /**
     * Adds an OpenAI client. [httpClientFactory] defaults to
     * [HttpClientFactoryResolver.resolve].
     */
    @JvmOverloads
    public fun openAI(
        apiKey: String,
        settings: OpenAIClientSettings = OpenAIClientSettings(),
        httpClientFactory: KoogHttpClient.Factory = HttpClientFactoryResolver.resolve(),
        clock: KoogClock = KoogClock.System,
        toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
    ): PromptExecutorBuilder = apply {
        addClient(OpenAILLMClient(apiKey, settings, httpClientFactory, clock, toolsConverter))
    }

    /**
     * Adds an Anthropic client. [httpClientFactory] defaults to
     * [HttpClientFactoryResolver.resolve].
     */
    @JvmOverloads
    public fun anthropic(
        apiKey: String,
        settings: AnthropicClientSettings = AnthropicClientSettings(),
        httpClientFactory: KoogHttpClient.Factory = HttpClientFactoryResolver.resolve(),
        clock: KoogClock = KoogClock.System,
    ): PromptExecutorBuilder = apply {
        addClient(AnthropicLLMClient(apiKey, settings, httpClientFactory, clock))
    }

    /**
     * Adds an Ollama client. [httpClientFactory] defaults to
     * [HttpClientFactoryResolver.resolve].
     */
    @JvmOverloads
    public fun ollama(
        baseUrl: String = "http://localhost:11434",
        httpClientFactory: KoogHttpClient.Factory = HttpClientFactoryResolver.resolve(),
        timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
        clock: KoogClock = KoogClock.System,
        contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategy.Companion.None,
        toolDescriptorConverter: ToolDescriptorSchemaGenerator = OllamaToolDescriptorSchemaGenerator(),
    ): PromptExecutorBuilder = apply {
        addClient(
            OllamaClient(
                httpClientFactory = httpClientFactory,
                baseUrl = baseUrl,
                timeoutConfig = timeoutConfig,
                clock = clock,
                contextWindowStrategy = contextWindowStrategy,
                toolDescriptorConverter = toolDescriptorConverter,
            )
        )
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
                fallbackModel?.let { RoutingLLMPromptExecutorBuilder.FallbackPromptExecutorSettings(it) }
            )
        } else {
            MultiLLMPromptExecutor(
                clients,
                fallbackModel?.let { MultiLLMPromptExecutorBuilder.FallbackPromptExecutorSettings(it.provider, it) }
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
