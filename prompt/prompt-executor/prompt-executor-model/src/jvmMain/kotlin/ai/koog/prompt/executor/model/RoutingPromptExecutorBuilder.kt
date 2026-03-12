@file:OptIn(ExperimentalRoutingApi::class)

package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.ExperimentalRoutingApi
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Entry point for constructing a [RoutingLLMPromptExecutor] via the fluent builder API.
 *
 * Obtain an instance through [PromptExecutor.routingExecutorBuilder].
 *
 * Example usage in Java:
 * ```java
 * PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
 *     .addClient(openAIClientPrimary)
 *     .addClient(openAIClientSecondary)
 *     .addClient(anthropicClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.routingExecutorBuilder
 * @see RoutingPromptExecutorBuilder
 */
@JavaAPI
@ExperimentalRoutingApi
public class InitialRoutingPromptExecutorBuilder {

    /**
     * Registers the first [LLMClient] and returns a [RoutingPromptExecutorBuilder] ready for further configuration.
     *
     * @param client The first LLM client to register.
     * @return A [RoutingPromptExecutorBuilder] with the given client already added.
     */
    public fun addClient(client: LLMClient): RoutingPromptExecutorBuilder =
        RoutingPromptExecutorBuilder(client)
}

/**
 * Builder for constructing a [RoutingLLMPromptExecutor].
 *
 * [RoutingLLMPromptExecutor] distributes requests across all registered clients using a round-robin
 * strategy per provider. Multiple clients for the same provider are allowed and will be load-balanced.
 *
 * Obtain an instance through [PromptExecutor.routingExecutorBuilder].
 *
 * Example usage in Java:
 * ```java
 * // Two OpenAI clients load-balanced, one Anthropic client
 * PromptExecutor executor = PromptExecutor.routingExecutorBuilder()
 *     .addClient(openAIClientPrimary)
 *     .addClient(openAIClientSecondary)
 *     .addClient(anthropicClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.routingExecutorBuilder
 * @see RoutingLLMPromptExecutor
 */
@JavaAPI
@ExperimentalRoutingApi
public class RoutingPromptExecutorBuilder internal constructor(firstClient: LLMClient) {
    private val clients: MutableList<LLMClient> = mutableListOf(firstClient)
    private var fallbackModel: LLModel? = null

    /**
     * Registers an additional [LLMClient].
     *
     * Multiple clients for the same provider are allowed and will be load-balanced.
     *
     * @param client The LLM client to add.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): RoutingPromptExecutorBuilder = apply {
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
    public fun fallback(model: LLModel): RoutingPromptExecutorBuilder = apply {
        fallbackModel = model
    }

    /**
     * Constructs a [RoutingLLMPromptExecutor] from the registered clients.
     *
     * @return A configured [RoutingLLMPromptExecutor] instance.
     * @throws IllegalArgumentException if a fallback model was configured but its provider has no registered client.
     */
    public fun build(): RoutingLLMPromptExecutor {
        fallbackModel?.provider?.let { fallbackProvider ->
            require(clients.any { it.llmProvider() == fallbackProvider }) {
                "Fallback model provider '$fallbackProvider' is not registered. " +
                    "Add a client for this provider before setting it as fallback."
            }
        }
        return RoutingLLMPromptExecutor(
            clients,
            fallbackModel?.let { RoutingLLMPromptExecutor.FallbackPromptExecutorSettings(it) }
        )
    }
}
