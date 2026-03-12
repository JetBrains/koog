@file:OptIn(ExperimentalRoutingApi::class)

package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.ExperimentalRoutingApi
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Entry point for constructing a [PromptExecutor] via the fluent builder API.
 *
 * Get an instance through [PromptExecutor.builder].
 *
 * Example usage in Java:
 * ```java
 * PromptExecutor executor = PromptExecutor.builder()
 *     .addClient(openAIClient)
 *     .addClient(anthropicClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.builder
 * @see PromptExecutorBuilder
 */
@JavaAPI
public class InitialPromptExecutorBuilder {

    /**
     * Registers the first [LLMClient] and returns a [PromptExecutorBuilder] ready for further configuration.
     *
     * @param client The first LLM client to register.
     * @return A [PromptExecutorBuilder] with the given client already added.
     */
    public fun addClient(client: LLMClient): PromptExecutorBuilder = PromptExecutorBuilder(client)
}

/**
 * Builder for constructing a [PromptExecutor] that automatically selects the appropriate executor
 * implementation based on the registered clients.
 *
 * **Executor selection heuristic** (determined at [build] time):
 * - If every registered provider appears exactly once, a [MultiLLMPromptExecutor] is created.
 *   It dispatches each request to the single client registered for the requested model's provider.
 * - If any provider has more than one client registered, a [RoutingLLMPromptExecutor] is created.
 *   It load-balances requests across all clients for the same provider using a round-robin strategy.
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
 * // Two clients for the same provider → RoutingLLMPromptExecutor
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
public class PromptExecutorBuilder internal constructor(firstClient: LLMClient) {
    private val clients: MutableList<LLMClient> = mutableListOf(firstClient)
    private var fallbackModel: LLModel? = null

    /**
     * Registers an additional [LLMClient].
     *
     * Multiple clients for the same provider are allowed. When more than one client is registered
     * for the same provider, [build] will create a [RoutingLLMPromptExecutor] that load-balances
     * across them using a round-robin strategy.
     *
     * @param client The LLM client to add.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): PromptExecutorBuilder = apply {
        clients += client
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
