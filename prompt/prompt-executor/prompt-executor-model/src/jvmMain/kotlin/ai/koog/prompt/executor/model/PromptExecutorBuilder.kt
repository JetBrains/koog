package ai.koog.prompt.executor.model

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor.FallbackPromptExecutorSettings
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor

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
            llmClients = *clients.map { it.llmProvider() to it }.toTypedArray(),
            fallback = fallbackSettings
        )
    }
}
