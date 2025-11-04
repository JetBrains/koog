@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlin.reflect.KType

/**
 * Builder for creating AIAgentService instances.
 *
 * Mirrors AIAgentBuilder but returns service-level objects:
 * - GraphAIAgentService<Input, Output>
 * - FunctionalAIAgentService<Input, Output>
 */
public expect class AIAgentServiceBuilder internal constructor() : AIAgentServiceBuilderAPI {
    public override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentServiceBuilder

    public override fun llmModel(model: LLModel): AIAgentServiceBuilder

    public override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentServiceBuilder

    public override fun systemPrompt(systemPrompt: String): AIAgentServiceBuilder

    public override fun prompt(prompt: Prompt): AIAgentServiceBuilder

    public override fun temperature(temperature: Double): AIAgentServiceBuilder

    public override fun numberOfChoices(numberOfChoices: Int): AIAgentServiceBuilder

    public override fun maxIterations(maxIterations: Int): AIAgentServiceBuilder

    @JavaAPI
    public override fun agentConfig(config: AIAgentConfig): AIAgentServiceBuilder

    public override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentServiceBuilder<Input, Output>

    public override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentServiceBuilder<Input, Output>

    public override fun build(): GraphAIAgentService<String, String>
}

/**
 * A builder class for constructing a GraphAIAgentService with configurable properties
 * such as prompt executor, model, tool registry, prompts, and various other configurations.
 *
 * @param Input The input type for the AI agent graph service.
 * @param Output The output type for the AI agent graph service.
 *
 * @property strategy The AI agent graph strategy governing the behavior and structure of the service.
 * @property inputType The KType representation of the input type.
 * @property outputType The KType representation of the output type.
 * @property promptExecutor The executor responsible for handling and orchestrating prompts.
 * @property toolRegistry The registry managing the tools available for the service.
 * @property prompt The system or user-defined prompt used in interactions.
 * @property llmModel The language model to be utilized within the service.
 * @property temperature The level of randomness in the language model's responses.
 * @property numberOfChoices The number of response options provided by the language model.
 * @property maxIterations The maximum number of iterations the strategy may execute before completion.
 * @property clock A time source for handling temporal operations such as delays or deadlines.
 * @property featureInstallers A collection of feature configuration functions to be applied to the service.
 */
public class GraphAgentServiceBuilder<Input, Output> internal constructor(
    private val strategy: AIAgentGraphStrategy<Input, Output>,
    private val inputType: KType,
    private val outputType: KType,
    internal var promptExecutor: PromptExecutor? = null,
    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private var prompt: Prompt = Prompt.Empty,
    private var llmModel: LLModel? = null,
    private var temperature: Double? = null,
    private var numberOfChoices: Int = 1,
    private var maxIterations: Int = 50,
    private var missingToolsConversionStrategy: MissingToolsConversionStrategy = MissingToolsConversionStrategy.Missing(
        ToolCallDescriber.JSON
    ),
    private var clock: Clock = Clock.System,
    private var featureInstallers: MutableList<FeatureContext.() -> Unit> = mutableListOf(),
) {

    /**
     * Sets the prompt executor for the `GraphServiceBuilder`. The prompt executor is responsible
     * for handling the interaction with the language model based on the provided prompts and configurations.
     *
     * @param promptExecutor An instance of `PromptExecutor` that will handle the prompt execution logic.
     * @return The updated instance of `GraphServiceBuilder` with the prompt executor set.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): GraphAgentServiceBuilder<Input, Output> =
        apply {
            this.promptExecutor = promptExecutor
        }

    /**
     * Sets the Large Language Model (LLM) for the current `GraphServiceBuilder` instance.
     *
     * @param model The `LLModel` instance to configure, specifying the LLM's provider, capabilities, and other characteristics.
     * @return An updated `GraphServiceBuilder` instance with the specified LLM model set.
     */
    public fun llmModel(model: LLModel): GraphAgentServiceBuilder<Input, Output> = apply {
        this.llmModel = model
    }

    /**
     * Sets the `ToolRegistry` to be used by the `GraphServiceBuilder`.
     *
     * This method allows specifying a `ToolRegistry` instance that manages the available tools
     * for the builder. The provided registry will replace any previously set registry.
     *
     * @param toolRegistry The `ToolRegistry` to be associated with the builder.
     * @return The current instance of `GraphServiceBuilder<Input, Output>` to allow method chaining.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): GraphAgentServiceBuilder<Input, Output> = apply {
        this.toolRegistry = toolRegistry
    }

    /**
     * Sets the system prompt for the `GraphServiceBuilder`, defining the initial context or instructions
     * to be used by the language model.
     *
     * @param systemPrompt The system message that provides instructions or context to the language model.
     * @return The updated `GraphServiceBuilder` instance.
     */
    public fun systemPrompt(systemPrompt: String): GraphAgentServiceBuilder<Input, Output> = apply {
        this.prompt = prompt(id = "agent") { system(systemPrompt) }
    }

    /**
     * Sets the prompt to be used by the GraphServiceBuilder.
     *
     * @param prompt The prompt to configure the service with.
     * @return The current instance of GraphServiceBuilder with the prompt set.
     */
    public fun prompt(prompt: Prompt): GraphAgentServiceBuilder<Input, Output> = apply {
        this.prompt = prompt
    }

    /**
     * Sets the temperature parameter for the GraphServiceBuilder. The temperature controls
     * the randomness of the model's output, where a lower value results in more deterministic
     * results, and a higher value increases variability.
     *
     * @param temperature the temperature value to set, typically ranging from 0.0 (deterministic)
     * to 1.0 or higher (more random results)
     * @return the updated instance of GraphServiceBuilder<Input, Output>
     */
    public fun temperature(temperature: Double): GraphAgentServiceBuilder<Input, Output> = apply {
        this.temperature = temperature
    }

    /**
     * Sets the number of choices that the service will consider during processing.
     *
     * @param numberOfChoices the number of choices to be used in the service logic
     * @return the current instance of GraphServiceBuilder for method chaining
     */
    public fun numberOfChoices(numberOfChoices: Int): GraphAgentServiceBuilder<Input, Output> = apply {
        this.numberOfChoices = numberOfChoices
    }

    /**
     * Sets the maximum number of iterations to be used in the graph processing service.
     * This parameter controls how many processing cycles will be executed.
     *
     * @param maxIterations the maximum number of iterations to perform
     * @return the updated instance of the GraphServiceBuilder
     */
    public fun maxIterations(maxIterations: Int): GraphAgentServiceBuilder<Input, Output> = apply {
        this.maxIterations = maxIterations
    }

    /**
     * Configures the agent with the specified parameters from the provided `AIAgentConfig` instance.
     *
     * This method applies the settings such as prompt, model, maximum agent iterations,
     * and strategy for handling missing tools from the given configuration to the current instance of `GraphAgentServiceBuilder`.
     *
     * @param config An instance of `AIAgentConfig` containing the configuration settings for the agent,
     *               including the prompt, model, maximum iterations, and strategy for missing tools.
     * @return The current instance of `GraphAgentServiceBuilder` with the updated configuration, allowing for method chaining.
     */
    @JavaAPI
    public fun agentConfig(config: AIAgentConfig): GraphAgentServiceBuilder<Input, Output> = apply {
        this.prompt = config.prompt
        this.llmModel = config.model
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    /**
     * Installs a specified feature into the `GraphServiceBuilder` and applies the given configuration to it.
     *
     * @param feature An instance of [AIAgentGraphFeature] that represents the feature to be installed, requiring a specific type of [FeatureConfig].
     * @param configure A [ConfigureAction] that applies custom configurations to the provided [FeatureConfig] object for the feature.
     * @return The current instance of [GraphAgentServiceBuilder], allowing for method chaining.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentServiceBuilder<Input, Output> = apply {
        this.featureInstallers += {
            install(feature) { configure.configure(this) }
        }
    }

    /**
     * Builds and returns an instance of `GraphAIAgentService` configured with the specified parameters.
     *
     * This method finalizes the construction of a `GraphAIAgentService` by ensuring that all required
     * components, such as the `PromptExecutor` and `LLModel`, are provided. It sets up the configuration
     * using the provided or defaulted prompt, language model, and other agent settings. The resulting
     * service is ready to manage AI agents in a graph-based strategy with support for configurable features.
     *
     * @return A configured instance of `GraphAIAgentService` for managing graph-based AI agents.
     */
    @OptIn(InternalAgentsApi::class)
    public fun build(): GraphAIAgentService<Input, Output> {
        val executor = requireNotNull(promptExecutor) { "PromptExecutor must be provided" }
        val model = requireNotNull(llmModel) { "LLModel must be provided" }

        val config = AIAgentConfig(
            prompt = if (prompt === Prompt.Empty) {
                prompt(
                    id = "chat", params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {}
            } else prompt,
            model = model,
            maxAgentIterations = maxIterations
        )

        val installCombined: FeatureContext.() -> Unit = {
            featureInstallers.forEach { it(this) }
        }

        return GraphAIAgentService(
            promptExecutor = executor,
            agentConfig = config,
            strategy = strategy,
            inputType = inputType,
            outputType = outputType,
            toolRegistry = toolRegistry,
            installFeatures = installCombined
        )
    }
}

/**
 * A builder class for constructing a `FunctionalAIAgentService`, enabling a fluent configuration style.
 *
 * This class allows customization of various parameters and components required for the AI agent
 * service, including strategies, prompts, models, tool registries, and other operational settings.
 *
 * @param Input The type of data input to the AI agent during execution.
 * @param Output The type of data output by the AI agent after processing.
 * @constructor Internal constructor initializing the builder with a functional strategy, optional
 * prompt executor, and a tool registry. Other parameters use default values unless set explicitly.
 */
public class FunctionalAgentServiceBuilder<Input, Output> internal constructor(
    private val strategy: AIAgentFunctionalStrategy<Input, Output>,
    internal var promptExecutor: PromptExecutor? = null,
    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private var prompt: Prompt = Prompt.Empty,
    private var llmModel: LLModel? = null,
    private var temperature: Double? = null,
    private var numberOfChoices: Int = 1,
    private var maxIterations: Int = 50,
    private var missingToolsConversionStrategy: MissingToolsConversionStrategy = MissingToolsConversionStrategy.Missing(
        ToolCallDescriber.JSON
    ),
    private var clock: Clock = Clock.System,
    private var featureInstallers: MutableList<FunctionalAIAgent.FeatureContext.() -> Unit> = mutableListOf(),
) {
    /**
     * Sets the `PromptExecutor` for this `FunctionalServiceBuilder`. The `PromptExecutor` is responsible for processing
     * prompts during the execution of the functional AI service being built.
     *
     * @param promptExecutor The `PromptExecutor` instance to use for executing prompts.
     * @return An updated `FunctionalServiceBuilder` instance configured with the given `PromptExecutor`.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): FunctionalAgentServiceBuilder<Input, Output> =
        apply {
            this.promptExecutor = promptExecutor
        }

    /**
     * Sets the specified Large Language Model (LLM) to be used by the Functional Service.
     *
     * @param model The instance of [LLModel] to be used, representing the selected LLM with its defined provider, identifier, and capabilities.
     * @return The updated instance of [FunctionalAgentServiceBuilder] with the specified LLM set.
     */
    public fun llmModel(model: LLModel): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.llmModel = model
    }

    /**
     * Sets the tool registry to be used by the `FunctionalServiceBuilder`.
     *
     * This method updates the `toolRegistry` field of the builder, specifying the registry
     * of tools that will be available to the agent during execution.
     *
     * @param toolRegistry The `ToolRegistry` instance to associate with the builder.
     * @return The current instance of the `FunctionalServiceBuilder` for method chaining.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.toolRegistry = toolRegistry
    }

    /**
     * Configures a system-level prompt message for the functional service builder.
     * This method sets the prompt for the agent, providing necessary system context or instructions
     * that guide the behavior of the language model.
     *
     * @param systemPrompt The system message content to provide context or instructions to the language model.
     * @return The updated instance of FunctionalServiceBuilder with the configured system prompt.
     */
    public fun systemPrompt(systemPrompt: String): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.prompt = prompt(id = "agent") { system(systemPrompt) }
    }

    /**
     * Assigns the given prompt to the builder configuration.
     *
     * @param prompt the prompt to be used in the service.
     * @return the current instance of FunctionalServiceBuilder for method chaining.
     */
    public fun prompt(prompt: Prompt): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.prompt = prompt
    }

    /**
     * Sets the temperature parameter for the functional service builder.
     * The temperature controls the randomness of the model's output, where a lower value makes the output more deterministic,
     * and a higher value increases randomness.
     *
     * @param temperature the temperature value to configure for the model's output randomness. It should typically be in a range between 0.0 and 1.0.
     * @return the current instance of the FunctionalServiceBuilder with the temperature parameter applied.
     */
    public fun temperature(temperature: Double): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.temperature = temperature
    }

    /**
     * Sets the number of choices for the service builder configuration.
     *
     * @param numberOfChoices the number of choices to be considered during processing
     * @return the updated instance of FunctionalServiceBuilder for chaining further configurations
     */
    public fun numberOfChoices(numberOfChoices: Int): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.numberOfChoices = numberOfChoices
    }

    /**
     * Sets the maximum number of iterations to use for the service.
     *
     * @param maxIterations The maximum number of iterations.
     * @return The updated FunctionalServiceBuilder instance with the specified maximum iterations.
     */
    public fun maxIterations(maxIterations: Int): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.maxIterations = maxIterations
    }

    /**
     * Configures the FunctionalAgentServiceBuilder with the given AI agent configuration.
     *
     * This method applies the provided `AIAgentConfig` to the builder, setting up the prompt,
     * language model, maximum iterations, and strategy for handling missing tools.
     *
     * @param config The AI agent configuration containing prompt settings, model configuration,
     *               maximum agent iterations, and strategies for handling missing tools.
     * @return The current instance of FunctionalAgentServiceBuilder for method chaining.
     */
    @JavaAPI
    public fun agentConfig(config: AIAgentConfig): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.prompt = config.prompt
        this.llmModel = config.model
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    /**
     * Installs the specified feature into the functional service builder and applies the provided configuration.
     *
     * @param TConfig The type of configuration required for the feature, extending [FeatureConfig].
     * @param feature The functional AI agent feature to be installed.
     * @param configure A configuration action used to customize the feature's settings.
     * @return The current instance of [FunctionalAgentServiceBuilder] with the feature installed.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentFunctionalFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): FunctionalAgentServiceBuilder<Input, Output> = apply {
        this.featureInstallers += {
            install(feature) { configure.configure(this) }
        }
    }

    /**
     * Builds and returns a configured instance of `FunctionalAIAgentService`.
     *
     * This method initializes the necessary components, including the prompt executor and
     * language model, and uses the provided configuration parameters to construct a
     * functional AI agent service. The service supports the execution of features and tools
     * through the defined strategy and installed context.
     *
     * @return A fully configured instance of `FunctionalAIAgentService` ready to process input
     * and generate output using the specified execution strategy, tools, and features.
     */
    @OptIn(InternalAgentsApi::class)
    public fun build(): FunctionalAIAgentService<Input, Output> {
        val executor = requireNotNull(promptExecutor) { "PromptExecutor must be provided" }
        val model = requireNotNull(llmModel) { "LLModel must be provided" }

        val config = AIAgentConfig(
            prompt = if (prompt === Prompt.Empty) {
                prompt(
                    id = "chat", params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {}
            } else prompt,
            model = model,
            maxAgentIterations = maxIterations
        )

        val installCombined: FunctionalAIAgent.FeatureContext.() -> Unit = {
            featureInstallers.forEach { it(this) }
        }

        return FunctionalAIAgentService(
            promptExecutor = executor,
            agentConfig = config,
            toolRegistry = toolRegistry,
            strategy = strategy,
            installFeatures = installCombined
        )
    }
}
