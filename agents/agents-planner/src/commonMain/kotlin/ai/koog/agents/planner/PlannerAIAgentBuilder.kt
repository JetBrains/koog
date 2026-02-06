package ai.koog.agents.planner

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

/**
 * A builder for creating [PlannerAIAgent] instances.
 */
public class PlannerAIAgentBuilder<State, Plan>(
    private val strategy: AIAgentPlannerStrategy<State, Plan>
) {
    private var promptExecutor: PromptExecutor? = null
    private var toolRegistry: ToolRegistry = ToolRegistry.EMPTY
    private var id: String? = null
    private var prompt: Prompt = Prompt.Empty
    private var llmModel: LLModel? = null
    private var temperature: Double = 1.0
    private var numberOfChoices: Int = 1
    private var missingToolsConversionStrategy: MissingToolsConversionStrategy =
        MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)
    private var maxIterations: Int = 50
    private var clock: Clock = Clock.System
    private var featureInstallers: MutableList<(PlannerAIAgent.FeatureContext.() -> Unit)> = mutableListOf()

    /**
     * Sets the `PromptExecutor` to be used by the builder instance.
     *
     * This method configures the builder with the provided `PromptExecutor`, which is responsible
     * for executing prompts against language models, managing tool interactions, and handling output.
     *
     * @param promptExecutor An instance of `PromptExecutor` that will be utilized for processing prompts
     * and interacting with language models.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining additional configurations.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): PlannerAIAgentBuilder<State, Plan> = apply {
        this.promptExecutor = promptExecutor
    }

    /**
     * Sets the `LLModel` instance to be used by the builder.
     *
     * This method configures the builder with a specified Large Language Model (LLM),
     * representing the model's provider, identifier, capabilities, and constraints such as
     * context length or maximum output tokens.
     *
     * @param model The [LLModel] instance representing the large language model to set.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining additional configurations.
     */
    public fun llmModel(model: LLModel): PlannerAIAgentBuilder<State, Plan> = apply {
        this.llmModel = model
    }

    /**
     * Sets the given `ToolRegistry` instance to the builder configuration.
     *
     * @param toolRegistry The instance of `ToolRegistry` to be used in the builder.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining further configurations.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): PlannerAIAgentBuilder<State, Plan> = apply {
        this.toolRegistry = toolRegistry
    }

    /**
     * Sets the identifier for the builder configuration.
     *
     * @param id The identifier string to be set. Can be null.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining method calls.
     */
    public fun id(id: String?): PlannerAIAgentBuilder<State, Plan> = apply {
        this.id = id
    }

    /**
     * Sets the system prompt to be used by the builder.
     *
     * This method configures the prompt with a system-level message that provides
     * instructions or context for a language model.
     *
     * @param systemPrompt The content of the system message to set as the prompt.
     * @return The current instance of the [PlannerAIAgentBuilder] with the updated system prompt.
     */
    public fun systemPrompt(systemPrompt: String): PlannerAIAgentBuilder<State, Plan> = apply {
        this.prompt = prompt(id = "agent") { system(systemPrompt) }
    }

    /**
     * Sets the prompt to be used by the builder.
     *
     * @param prompt The [Prompt] instance to set.
     * @return The current instance of the [PlannerAIAgentBuilder].
     */
    public fun prompt(prompt: Prompt): PlannerAIAgentBuilder<State, Plan> = apply {
        this.prompt = prompt
    }

    /**
     * Sets the temperature value for the builder.
     *
     * Temperature is typically used to control the randomness of outputs in language models. Higher values result in more
     * random outputs, while lower values make outputs more deterministic.
     *
     * @param temperature The temperature value to set. It should be a non-negative double, where common values are within
     *                     the range [0.0, 1.0].
     * @return The current instance of the [PlannerAIAgentBuilder] for method chaining.
     */
    public fun temperature(temperature: Double): PlannerAIAgentBuilder<State, Plan> = apply {
        this.temperature = temperature
    }

    /**
     * Sets the number of choices to be utilized by the builder instance.
     *
     * This method configures the builder with a specified number of discrete choices,
     * which could be utilized in the decision-making process or output generation.
     *
     * @param numberOfChoices The integer representing the number of choices to configure.
     *                        Must be a positive value.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining additional configurations.
     */
    public fun numberOfChoices(numberOfChoices: Int): PlannerAIAgentBuilder<State, Plan> = apply {
        this.numberOfChoices = numberOfChoices
    }

    /**
     * Sets the maximum number of iterations for the builder.
     *
     * @param maxIterations The maximum number of iterations to be used. Must be a positive integer.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining additional configurations.
     */
    public fun maxIterations(maxIterations: Int): PlannerAIAgentBuilder<State, Plan> = apply {
        this.maxIterations = maxIterations
    }

    /**
     * Configures the current `PlannerAIAgentBuilder` instance using the provided `AIAgentConfig`.
     *
     * This method applies the settings from the given `AIAgentConfig`, such as the prompt, language model,
     * maximum agent iterations, and strategy to handle missing tools, to the builder instance.
     *
     * @param config An `AIAgentConfig` instance containing the configuration settings to be applied.
     * @return The current instance of `PlannerAIAgentBuilder` for chaining further methods.
     */
    public fun agentConfig(config: AIAgentConfig): PlannerAIAgentBuilder<State, Plan> = apply {
        this.llmModel = config.model
        this.prompt = config.prompt
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    /**
     * Installs a planner-specific AI agent feature into the builder with its provided configuration.
     *
     * This method allows the integration of an [AIAgentFunctionalFeature] into the builder and its
     * configuration using a lambda function. The feature is then added to the list of feature
     * installers, enabling its functionality within the AI agent being constructed.
     *
     * @param TConfig The type of the configuration for the feature, extending [FeatureConfig].
     * @param feature The [AIAgentFunctionalFeature] to be installed into the builder.
     * @param configure A lambda function to configure the feature's properties and behavior.
     * @return The current instance of the [PlannerAIAgentBuilder] for chaining additional configurations.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentFunctionalFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): PlannerAIAgentBuilder<State, Plan> = apply {
        featureInstallers.add {
            install(feature) {
                configure.configure(this)
            }
        }
    }

    /**
     * Builds and returns an instance of [AIAgent] configured according to the builder's settings.
     *
     * This method finalizes the current configuration and constructs an AI agent. The agent is
     * equipped with the specified execution strategy, tool registry, identifier, prompt, language
     * model, and other optional configurations. If required fields, such as `promptExecutor` or
     * `llmModel`, are not set, this method throws an exception.
     *
     * @return An instance of [AIAgent] with the configured state type.
     */
    public fun build(): AIAgent<State, State> = PlannerAIAgent(
        promptExecutor = requireNotNull(promptExecutor) { "Prompt executor must be set" },
        agentConfig = AIAgentConfig(
            prompt,
            requireNotNull(llmModel) { "llmModel must me set" },
            maxIterations,
            missingToolsConversionStrategy
        ),
        strategy = strategy,
        toolRegistry = toolRegistry,
        id = id,
        clock = clock,
        installFeatures = { featureInstallers.forEach { it() } }
    )
}
