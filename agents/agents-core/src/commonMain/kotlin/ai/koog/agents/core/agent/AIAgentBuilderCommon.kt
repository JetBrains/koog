package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.utils.BuilderChainAction
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.AIAgentPlannerStrategyBuilder
import ai.koog.agents.planner.TypedAgentPlannerStrategyBuilder
import ai.koog.serialization.typeToken

/**
 * Common chained implementation for [AIAgentBuilder] actual classes.
 */
public abstract class AIAgentBuilderCommon<Self : AIAgentBuilderCommon<Self>> internal constructor() : AIAgentBuilderBase<Self>() {

    /**
     * Configures and returns a `GraphAgentBuilder` instance using the specified `AIAgentGraphStrategy`.
     *
     * The method allows associating an AI agent with a specific graph-based strategy for managing
     * and executing workflows. It provides flexibility to define input and output types
     * specific to the desired strategy.
     *
     * @param strategy The `AIAgentGraphStrategy` instance defining the workflow, including
     * the start and finish nodes as well as the tool selection strategy.
     * @return An instance of `GraphAgentBuilder` configured with the specified strategy.
     */
    public fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = GraphAgentBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        promptExecutor = this.promptExecutor,
        id = this.id,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    /**
     * Sets the functional strategy to be used by the agent builder.
     *
     * The provided [strategy] defines the behavior and processing logic for the AI agent in a
     * loop-based execution model. This method configures the builder to utilize the specified
     * strategy and returns an instance of [FunctionalAgentBuilder] for further configuration.
     *
     * @param strategy An instance of [AIAgentFunctionalStrategy] that contains the custom logic
     * used by the AI agent for decision-making or execution processes.
     * @return An instance of [FunctionalAgentBuilder] configured with the provided functional strategy.
     */
    public fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = FunctionalAgentBuilder(
        strategy = strategy,
        id = this.id,
        promptExecutor = this.promptExecutor,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    /**
     * Configures the planner strategy to be used by an AI agent planner.
     *
     * @param strategy The planning strategy to define how the AI agent should plan actions.
     * @return An instance of [PlannerAgentBuilder] configured with the specified planning strategy.
     */
    public fun <Input, Output> plannerStrategy(
        strategy: AIAgentPlannerStrategy<Input, Output, *>
    ): PlannerAgentBuilder<Input, Output> = PlannerAgentBuilder(
        strategy = strategy,
        id = this.id,
        promptExecutor = this.promptExecutor,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    /**
     * Defines a planner strategy for the planner using a specified builder chain action.
     *
     * @param buildStrategy A function that builds the planner strategy by chaining actions using
     * an instance of [AIAgentPlannerStrategyBuilder] and optionally [TypedAgentPlannerStrategyBuilder].
     * @return A [PlannerAgentBuilder] instance configured with the specified input and output types.
     */
    public fun <Input : Any, Output : Any> plannerStrategy(
        name: String,
        buildStrategy: BuilderChainAction<AIAgentPlannerStrategyBuilder, TypedAgentPlannerStrategyBuilder<Input, Output>>
    ): PlannerAgentBuilder<Input, Output> = plannerStrategy(
        buildStrategy.configure(AIAgentPlannerStrategyBuilder(name)).build()
    )

    /**
     * Installs a graph-specific AI agent feature into the builder with its provided configuration.
     *
     * This method allows the integration of an [AIAgentGraphFeature] into the builder and its
     * configuration using a lambda function.
     *
     * @param feature The [AIAgentGraphFeature] to be installed into the builder.
     * @param configure A lambda function to configure the feature's properties and behavior.
     * @return An instance of [GraphAgentBuilder] configured with the installed feature.
     */
    public fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = GraphAgentBuilder(
        strategy = singleRunStrategy(),
        inputType = typeToken<String>(),
        outputType = typeToken<String>(),
        promptExecutor = this.promptExecutor,
        id = this.id,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry,
        featureInstallers = mutableListOf({
            install(feature) {
                configure.configure(this)
            }
        })
    )

    /**
     * Builds and returns an instance of [AIAgent] configured according to the builder's settings.
     *
     * This method finalizes the current configuration and constructs an AI agent. The agent is
     * equipped with the specified execution strategy, tool registry, identifier, prompt, language
     * model, and other optional configurations.
     *
     * @return An instance of [AIAgent] with the configured input and output types as `String`.
     */
    public fun build(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = validatedPromptExecutor,
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = validatedConfig,
            clock = clock
        )
    }
}
