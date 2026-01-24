@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * API for the [AIAgentServiceBuilder]
 */
public interface AIAgentServiceBuilderAPI {
    /**
     * Sets the prompt executor for the `AIAgentServiceBuilder`.
     *
     * @param promptExecutor The instance of `PromptExecutor` to be used by the service.
     * @return The current instance of `AIAgentServiceBuilder` for method chaining.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): AIAgentServiceBuilderAPI

    /**
     * Sets the Large Language Model (LLM) instance to be used by the service builder.
     *
     * @param model The LLM instance to be used, represented by an [LLModel] object.
     * @return The current instance of [AIAgentServiceBuilder] for method chaining.
     */
    public fun llmModel(model: LLModel): AIAgentServiceBuilderAPI

    /**
     * Configures the `ToolRegistry` for the `AIAgentServiceBuilder`.
     *
     * This method allows setting a `ToolRegistry`, which manages a collection of tools
     * available for use by AI agents during their operation.
     *
     * @param toolRegistry The `ToolRegistry` instance to be used by the agent service.
     * @return The current instance of `AIAgentServiceBuilder` for fluent method chaining.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): AIAgentServiceBuilderAPI

    /**
     * Sets a system-level instruction or context for the AI agent.
     * The provided string serves as the system prompt, defining the
     * role or behavior of the AI within the generated service.
     *
     * @param systemPrompt The system-level instruction to configure the prompt.
     * @return The current instance of AIAgentServiceBuilder with the updated system prompt.
     */
    public fun systemPrompt(systemPrompt: String): AIAgentServiceBuilderAPI

    /**
     * Sets the prompt to be used for the AI Agent service and returns the builder instance.
     *
     * @param prompt the Prompt object that defines the input or behavior for the AI Agent service
     * @return the current instance of AIAgentServiceBuilder to allow method chaining
     */
    public fun prompt(prompt: Prompt): AIAgentServiceBuilderAPI

    /**
     * Sets the temperature parameter, which adjusts the randomness of the model's outputs.
     * A higher temperature value results in more varied and creative responses, whereas a lower
     * temperature value yields more focused and deterministic responses.
     *
     * @param temperature The desired temperature value for controlling output randomness.
     * @return The updated instance of AIAgentServiceBuilder.
     */
    public fun temperature(temperature: Double): AIAgentServiceBuilderAPI

    /**
     * Sets the number of choices to be used in the AI agent service.
     *
     * @param numberOfChoices The number of choices to be configured for the AI agent service.
     * @return The instance of AIAgentServiceBuilder with the number of choices configured.
     */
    public fun numberOfChoices(numberOfChoices: Int): AIAgentServiceBuilderAPI

    /**
     * Sets the maximum number of iterations for the AI agent's process.
     *
     * @param maxIterations the maximum number of iterations to be performed
     * @return the updated instance of AIAgentServiceBuilder
     */
    public fun maxIterations(maxIterations: Int): AIAgentServiceBuilderAPI

    /**
     * Configures the AI agent service builder using the specified agent configuration.
     *
     * This method applies the parameters defined in the provided `AIAgentConfig` object
     * to the current instance of the `AIAgentServiceBuilder`. It sets the prompt, language
     * model, maximum number of iterations, and the strategy for handling missing tools during execution.
     *
     * @param config The configuration object containing the settings to be applied, including
     *        the prompt, model, maximum agent iterations, and missing tools conversion strategy.
     * @return The current instance of `AIAgentServiceBuilder` for method chaining.
     */
    @JavaAPI
    public fun agentConfig(config: AIAgentConfig): AIAgentServiceBuilderAPI

    /**
     * Configure a graph strategy and continue with a graph service builder.
     */
    public fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentServiceBuilder<Input, Output>

    /**
     * Configure a functional strategy and continue with a functional service builder.
     */
    public fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentServiceBuilder<Input, Output>

    /**
     * Convenience build for GraphAIAgentService<String, String> using singleRunStrategy.
     */
    public fun build(): GraphAIAgentService<String, String>
}
