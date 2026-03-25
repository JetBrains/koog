@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.serialization.TypeToken
import kotlin.time.Clock

/**
 * Represents a configurational builder for setting up and customizing the execution parameters and
 * components of an AI agent. This builder enables fine-grained control over tools, strategies,
 * and prompts utilized by an AI agent during its execution.
 */
public expect class AIAgentBuilder internal constructor() : AIAgentBuilderCommon<AIAgentBuilder> {
    override fun self(): AIAgentBuilder
}

/**
 * A builder class for creating instances of [AIAgent]. This builder provides a fluent interface
 * to configure various parameters and components required to construct an AI agent with a
 * specific set of features, tools, and execution strategies.
 *
 * @param Input The input type that the agent processes.
 * @param Output The output type that the agent produces.
 * @param strategy The execution strategy used by the agent for processing input and generating results.
 * @param inputType The [TypeToken] representation of the input parameter type.
 * @param outputType The [TypeToken] representation of the output parameter type.
 * @param promptExecutor [PromptExecutor] for the agent
 * @param id id of the agent
 * @param config [AIAgentConfig] containing initial agent configuration for the builder
 * @param clock optional [Clock] to be used in the agent for calculating timestamps
 */
public class GraphAgentBuilder<Input, Output>(
    private val strategy: AIAgentGraphStrategy<Input, Output>,
    private val inputType: TypeToken,
    private val outputType: TypeToken,
    promptExecutor: PromptExecutor? = null,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    config: AIAgentConfig,
    clock: Clock = Clock.System,
    private var featureInstallers: MutableList<FeatureContext.() -> Unit> = mutableListOf(),
) : AIAgentBuilderBase<GraphAgentBuilder<Input, Output>>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    id = id,
    config = config,
    clock = clock,
) {
    override fun self(): GraphAgentBuilder<Input, Output> = this

    /**
     * Installs a specified feature into the current context and applies its configuration.
     *
     * @param TConfig The type of configuration required by the feature, extending [FeatureConfig].
     * @param feature The feature to install, represented by an implementation of [AIAgentGraphFeature].
     * @param configure A lambda used to customize the configuration of the feature.
     * @return The current [GraphAgentBuilder] instance, enabling further configurations.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<Input, Output> = apply {
        this.featureInstallers += {
            install(feature) {
                configure.configure(this)
            }
        }
    }

    /**
     * Builds and returns an instance of `AIAgent` configured using the parameters
     * provided to the `GraphAgentBuilder`.
     *
     * @return an instance of `AIAgent` initialized with the specified input and output types,
     *         strategy, tool registry, prompt executor, model configuration, and other optional settings.
     */
    public fun build(): AIAgent<Input, Output> {
        return GraphAIAgent(
            inputType = inputType,
            outputType = outputType,
            strategy = strategy,
            promptExecutor = validatedPromptExecutor,
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = validatedConfig,
            clock = clock
        ) {
            featureInstallers.forEach { install ->
                install()
            }
        }
    }
}

/**
 * A builder class for constructing instances of `FunctionalAIAgent` with customizable configuration.
 *
 * This builder simplifies the configuration process by providing a fluent API to set various
 * parameters for the `FunctionalAIAgent`, including its behavior strategy, prompt details, model settings,
 * tool registry, and additional features. The builder enforces the presence of required configurations
 * and allows the addition of optional parameters to tailor the agent's functionality.
 *
 * @param Input The type of input that the resulting AI agent will process.
 * @param Output The type of output that the resulting AI agent will produce.
 * @property strategy The strategy defining the behavior of the AI agent, responsible for the core iterative logic.
 * @property promptExecutor The initial executor responsible for executing prompts, defaults to `null` if not set.
 * @property toolRegistry A registry of tools available to the agent, by default set to `ToolRegistry.EMPTY`.
 * @property id An optional unique identifier for the agent.
 * @property config [AIAgentConfig] containing initial agent configuration for the builder
 * @property clock The clock instance used for time-related functionality, default is `Clock.System`.
 * @property featureInstallers A list of feature installation lambdas defining additional functionalities the agent should have.
 */
public class FunctionalAgentBuilder<Input, Output>(
    private val strategy: AIAgentFunctionalStrategy<Input, Output>,
    promptExecutor: PromptExecutor? = null,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    config: AIAgentConfig,
    clock: Clock = Clock.System,
    private var featureInstallers: MutableList<FunctionalAIAgent.FeatureContext.() -> Unit> = mutableListOf(),
) : AIAgentBuilderBase<FunctionalAgentBuilder<Input, Output>>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    id = id,
    config = config,
    clock = clock,
) {
    override fun self(): FunctionalAgentBuilder<Input, Output> = this

    /**
     * Installs and configures a given feature into the functional agent builder.
     *
     * @param TConfig the type of the feature configuration, which extends [FeatureConfig].
     * @param feature the feature to be installed, represented by an implementation of [AIAgentFunctionalFeature].
     * @param configure a lambda function to customize the configuration of the feature, where the provided [TConfig] can be modified.
     * @return the current [FunctionalAgentBuilder] instance for chaining further configurations.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentFunctionalFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): FunctionalAgentBuilder<Input, Output> = apply {
        this.featureInstallers += {
            install(feature) {
                configure.configure(this)
            }
        }
    }

    /**
     * Builds and returns an instance of `AIAgent<Input, Output>` based on the current configuration
     * of the `FunctionalAgentBuilder`. This method ensures that all required fields are set,
     * and applies any configured feature installers to the agent.
     *
     * @return an instance of `AIAgent<Input, Output>` created using the provided configuration.
     * @throws IllegalArgumentException if required fields, such as `promptExecutor` or `llmModel`, are not set.
     */
    public fun build(): AIAgent<Input, Output> {
        return FunctionalAIAgent(
            strategy = strategy,
            promptExecutor = validatedPromptExecutor,
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = validatedConfig,
            clock = clock
        ) {
            featureInstallers.forEach { install ->
                install()
            }
        }
    }
}

/**
 * Builds an AI-based planning agent by configuring various parameters and defining custom behaviors
 * for the agent. This builder allows flexible setup of an agent's functionality and behavior
 * based on the provided configuration and tools.
 *
 * @param strategy The planning strategy used by the agent to process and execute tasks.
 * @param promptExecutor The executor responsible for handling AI prompts.
 * @param toolRegistry The registry of tools available for use by the agent. Defaults to an empty tool registry.
 * @param id The optional identifier of the agent.
 * @param config [AIAgentConfig] containing initial agent configuration for the builder
 * @param clock The clock instance used to track time-related operations for the agent. Defaults to the system clock.
 * @param featureInstallers A list of feature installers that enhance the agent's behavior with additional functionality.
 */
public class PlannerAgentBuilder<Input, Output>(
    private val strategy: AIAgentPlannerStrategy<Input, Output, *>,
    promptExecutor: PromptExecutor? = null,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    config: AIAgentConfig,
    clock: Clock = Clock.System,
    private var featureInstallers: MutableList<PlannerAIAgent.FeatureContext.() -> Unit> = mutableListOf(),
) : AIAgentBuilderBase<PlannerAgentBuilder<Input, Output>>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    id = id,
    config = config,
    clock = clock,
) {
    override fun self(): PlannerAgentBuilder<Input, Output> = this

    /**
     * Installs a functional feature into the PlannerAgentBuilder with the specified configuration.
     *
     * @param feature The functional feature to be installed, parameterized with a configuration type and an additional type.
     * @param configure A lambda or action responsible for configuring the provided feature with the appropriate settings.
     * @return The current instance of PlannerAgentBuilder with the feature installed, enabling method chaining.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentPlannerFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): PlannerAgentBuilder<Input, Output> = apply {
        this.featureInstallers += {
            install(feature) {
                configure.configure(this)
            }
        }
    }

    /**
     * Constructs and returns an instance of [AIAgent] configured with the provided parameters and features.
     *
     * @return An instance of [AIAgent] that uses the specified strategy, model, prompt, tools, and other configurations defined in the builder.
     */
    public fun build(): AIAgent<Input, Output> {
        return PlannerAIAgent(
            strategy = strategy,
            promptExecutor = validatedPromptExecutor,
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = validatedConfig,
            clock = clock
        ) {
            featureInstallers.forEach { install ->
                install()
            }
        }
    }
}
