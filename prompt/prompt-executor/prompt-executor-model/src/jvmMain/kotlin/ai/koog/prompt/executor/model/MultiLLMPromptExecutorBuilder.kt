package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Entry point for constructing a [MultiLLMPromptExecutor] via the fluent builder API.
 *
 * Obtain an instance through [PromptExecutor.multiExecutorBuilder].
 *
 * Example usage in Java:
 * ```java
 * PromptExecutor executor = PromptExecutor.multiExecutorBuilder()
 *     .addClient(openAIClient)
 *     .addClient(anthropicClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.multiExecutorBuilder
 * @see MultiLLMPromptExecutorBuilder
 */
@JavaAPI
public class InitialMultiLLMPromptExecutorBuilder {

    /**
     * Registers the first [LLMClient] and returns a [MultiLLMPromptExecutorBuilder] ready for further configuration.
     *
     * @param client The first LLM client to register.
     * @return A [MultiLLMPromptExecutorBuilder] with the given client already added.
     */
    public fun addClient(client: LLMClient): MultiLLMPromptExecutorBuilder =
        MultiLLMPromptExecutorBuilder(client)
}

/**
 * Builder for constructing a [MultiLLMPromptExecutor].
 *
 * [MultiLLMPromptExecutor] dispatches each request to the client registered for the requested
 * model's provider. If multiple clients are registered for the same provider, the last one wins.
 *
 * Obtain an instance through [PromptExecutor.multiExecutorBuilder].
 *
 * Example usage in Java:
 * ```java
 * PromptExecutor executor = PromptExecutor.multiExecutorBuilder()
 *     .addClient(openAIClient)
 *     .addClient(anthropicClient)
 *     .build();
 * ```
 *
 * @see PromptExecutor.multiExecutorBuilder
 * @see MultiLLMPromptExecutor
 */
@JavaAPI
public class MultiLLMPromptExecutorBuilder internal constructor(firstClient: LLMClient) {
    private val clients: MutableList<LLMClient> = mutableListOf(firstClient)
    private var fallbackModel: LLModel? = null

    /**
     * Registers an additional [LLMClient].
     *
     * If two clients are registered for the same provider, the last one registered wins.
     *
     * @param client The LLM client to add.
     * @return This builder instance for chaining.
     */
    public fun addClient(client: LLMClient): MultiLLMPromptExecutorBuilder = apply {
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
    public fun fallback(model: LLModel): MultiLLMPromptExecutorBuilder = apply {
        fallbackModel = model
    }

    /**
     * Constructs a [MultiLLMPromptExecutor] from the registered clients.
     *
     * @return A configured [MultiLLMPromptExecutor] instance.
     * @throws IllegalArgumentException if a fallback model was configured but its provider has no registered client.
     */
    public fun build(): MultiLLMPromptExecutor {
        fallbackModel?.provider?.let { fallbackProvider ->
            require(clients.any { it.llmProvider() == fallbackProvider }) {
                "Fallback model provider '$fallbackProvider' is not registered. " +
                    "Add a client for this provider before setting it as fallback."
            }
        }
        return MultiLLMPromptExecutor(
            clients,
            fallbackModel?.let { MultiLLMPromptExecutor.FallbackPromptExecutorSettings(it.provider, it) }
        )
    }
}
