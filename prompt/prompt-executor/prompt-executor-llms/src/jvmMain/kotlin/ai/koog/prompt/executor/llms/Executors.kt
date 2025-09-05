package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.JavaPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Provides utility methods for creating instances of `JavaPromptExecutor`.
 *
 * The `Executors` object is designed to simplify the process of configuring and initializing
 * prompt executors that interact with Large Language Model (LLM) clients. It supports both
 * single and multiple LLM providers, enabling flexibility in execution configurations.
 *
 * This utility ensures that appropriate `JavaPromptExecutor` instances are created with
 * the required delegates or configurations.
 *
 * Note: This API is experimental and may change in future versions.
 */
@ApiStatus.Experimental
public object Executors {

    /**
     * Creates a new instance of `JavaPromptExecutor` using the provided map of LLM clients.
     * The `JavaPromptExecutor` is configured with a delegated instance of `MultiLLMPromptExecutor`
     * to handle prompts across multiple LLM providers.
     *
     * @param llmClients A map where keys are `LLMProvider` instances representing the language model providers
     *                   and values are `LLMClient` instances for interacting with the respective providers.
     * @return A configured `JavaPromptExecutor` instance ready to handle prompt execution.
     */
    @JvmStatic
    @ApiStatus.Experimental
    public fun promptExecutor(llmClients: Map<LLMProvider, LLMClient>): JavaPromptExecutor =
        JavaPromptExecutor(
            delegate = MultiLLMPromptExecutor(llmClients)
        )

    /**
     * Creates an instance of [JavaPromptExecutor] by associating a specific [LLMProvider] with an [LLMClient].
     *
     * @param llmProvider The `LLMProvider` instance specifying the large language model provider.
     * @param llmClient The `LLMClient` instance used to execute prompts and interact with the specified provider.
     * @return A `JavaPromptExecutor` configured with the given provider and client.
     */
    @JvmStatic
    @ApiStatus.Experimental
    public fun promptExecutor(llmProvider: LLMProvider, llmClient: LLMClient): JavaPromptExecutor =
        promptExecutor(mapOf(llmProvider to llmClient))

    /**
     * Creates and returns a Java-friendly prompt executor that delegates prompt execution
     * to a [SingleLLMPromptExecutor].
     *
     * @param llmClient The client used for direct communication with a large language model (LLM) provider.
     * @return An instance of [JavaPromptExecutor] that wraps a [SingleLLMPromptExecutor].
     */
    @JvmStatic
    public fun promptExecutor(llmClient: LLMClient): JavaPromptExecutor =
        JavaPromptExecutor(
            delegate = SingleLLMPromptExecutor(llmClient)
        )
}
