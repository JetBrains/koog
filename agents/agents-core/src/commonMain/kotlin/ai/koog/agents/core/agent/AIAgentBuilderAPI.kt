package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * A platform-agnostic API describing the full surface of AIAgentBuilder.
 *
 * Note on return types:
 *  - Methods that previously returned AIAgentBuilder now return AIAgentBuilderAPI here
 *    so that common implementation classes can return `this` while platform `actual`
 *    classes can covariantly narrow the return type back to AIAgentBuilder.
 */
public interface AIAgentBuilderAPI {
    /**
     * Sets the `PromptExecutor` to be used by the builder instance.
     *
     * This method configures the builder with the provided `PromptExecutor`, which is responsible
     * for executing prompts against language models, managing tool interactions, and handling output.
     *
     * @param promptExecutor An instance of `PromptExecutor` that will be utilized for processing prompts
     * and interacting with language models.
     * @return The current instance of the `Builder` for chaining additional configurations.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilderAPI

    /**
     * Sets the `LLModel` instance to be used by the builder.
     *
     * This method configures the builder with a specified Large Language Model (LLM),
     * representing the model's provider, identifier, capabilities, and constraints such as
     * context length or maximum output tokens.
     *
     * @param model The [LLModel] instance representing the large language model to set.
     * @return The current instance of the `Builder` for chaining additional configurations.
     */
    public fun llmModel(model: LLModel): AIAgentBuilderAPI

    /**
     * Sets the given `ToolRegistry` instance to the builder configuration.
     *
     * @param toolRegistry The instance of `ToolRegistry` to be used in the builder.
     * @return The current instance of the `Builder` for chaining further configurations.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilderAPI

    /**
     * Configures and returns a `GraphAgentBuilder` instance using the specified `AIAgentGraphStrategy`.
     *
     * The method allows associating an AI agent with a specific graph-based strategy for managing
     * and executing workflows. It provides flexibility to define input and output types
     * specific to the desired strategy.
     *
     * @param Input The type of input data that the strategy will process.
     * @param Output The type of output data that the strategy will produce.
     * @param strategy The `AIAgentGraphStrategy` instance defining the workflow, including
     * the start and finish nodes as well as the tool selection strategy.
     * @return An instance of `GraphAgentBuilder` configured with the specified input type,
     * output type, and strategy.
     */
    public fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output>

    /**
     * Sets the functional strategy to be used by the agent builder.
     *
     * The provided [strategy] defines the behavior and processing logic for the AI agent in a
     * loop-based execution model. This method configures the builder to utilize the specified
     * strategy and returns an instance of [FunctionalAgentBuilder] for further configuration.
     *
     * @param Input The type of the input data to be processed by the strategy.
     * @param Output The type of the output data to be produced by the strategy.
     * @param strategy An instance of [AIAgentFunctionalStrategy] that contains the custom logic
     * used by the AI agent for decision-making or execution processes.
     * @return An instance of [FunctionalAgentBuilder] configured with the provided functional strategy.
     */
    public fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output>

    /**
     * Sets the identifier for the builder configuration.
     *
     * @param id The identifier string to be set. Can be null.
     * @return The current instance of the builder for chaining method calls.
     */
    public fun id(id: String?): AIAgentBuilderAPI

    /**
     * Sets the system prompt to be used by the builder.
     *
     * This method configures the prompt with a system-level message that provides
     * instructions or context for a language model.
     *
     * @param systemPrompt The content of the system message to set as the prompt.
     * @return The current instance of the builder with the updated system prompt.
     */
    public fun systemPrompt(systemPrompt: String): AIAgentBuilderAPI

    /**
     * Sets the prompt to be used by the builder.
     *
     * @param prompt The [Prompt] instance to set.
     * @return The current instance of the builder.
     */
    public fun prompt(prompt: Prompt): AIAgentBuilderAPI

    /**
     * Sets the temperature value for the builder.
     *
     * Temperature is typically used to control the randomness of outputs in language models. Higher values result in more
     * random outputs, while lower values make outputs more deterministic.
     *
     * @param temperature The temperature value to set. It should be a non-negative double, where common values are within
     *                     the range [0.0, 1.0].
     * @return The current instance of the Builder for method chaining.
     */
    public fun temperature(temperature: Double): AIAgentBuilderAPI

    /**
     * Sets the number of choices to be utilized by the builder instance.
     *
     * This method configures the builder with a specified number of discrete choices,
     * which could be utilized in the decision-making process or output generation.
     *
     * @param numberOfChoices The integer representing the number of choices to configure.
     *                        Must be a positive value.
     * @return The current instance of the `Builder` for chaining additional configurations.
     */
    public fun numberOfChoices(numberOfChoices: Int): AIAgentBuilderAPI

    /**
     * Sets the maximum number of iterations for the builder.
     *
     * @param maxIterations The maximum number of iterations to be used. Must be a positive integer.
     * @return The current instance of the*/
    public fun maxIterations(maxIterations: Int): AIAgentBuilderAPI

    /**
     * Configures the current `AIAgentBuilder` instance using the provided `AIAgentConfig`.
     *
     * This method applies the settings from the given `AIAgentConfig`, such as the prompt, language model,
     * maximum agent iterations, and strategy to handle missing tools, to the builder instance.
     *
     * @param config An `AIAgentConfig` instance containing the configuration settings to be applied.
     * @return The current instance of `AIAgentBuilder` for chaining further methods.
     */
    public fun agentConfig(config: AIAgentConfig): AIAgentBuilderAPI

    /**
     * Installs a graph-specific AI agent feature into the builder with its provided configuration.
     *
     * This method allows the integration of an [AIAgentGraphFeature] into the builder and its
     * configuration using a lambda function. The feature is then added to the list of feature
     * installers, enabling its functionality within the AI agent being constructed.
     *
     * @param TConfig The type of the configuration for the feature, extending [FeatureConfig].
     * @param feature The [AIAgentGraphFeature] to be installed into the builder.
     * @param configure A lambda function to configure the feature's properties and behavior.
     * @return An instance of [GraphAgentBuilder] configured with the installed feature.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String>

    /**
     * Builds and returns an instance of [AIAgent] configured according to the builder's settings.
     *
     * This method finalizes the current configuration and constructs an AI agent. The agent is
     * equipped with the specified execution strategy, tool registry, identifier, prompt, language
     * model, and other optional configurations. If required fields, such as `promptExecutor` or
     * `llmModel`, are not set, this method throws an exception.
     *
     * @return An instance of [AIAgent] with the configured input and output types as `String`.
     */
    public fun build(): AIAgent<String, String>
}
