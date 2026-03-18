@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.serialization.TypeToken
import kotlin.time.Clock

/**
 * Builder for creating AIAgentService instances.
 *
 * Mirrors AIAgentBuilder but returns service-level objects:
 * - GraphAIAgentService<Input, Output>
 * - FunctionalAIAgentService<Input, Output>
 */
public expect class AIAgentServiceBuilder internal constructor() :
    AIAgentServiceBuilderCommon<AIAgentServiceBuilder> {
    override fun self(): AIAgentServiceBuilder
}

/**
 * A builder class for constructing a GraphAIAgentService with configurable properties
 * such as prompt executor, model, tool registry, prompts, and various other configurations.
 *
 * @param Input The input type for the AI agent graph service.
 * @param Output The output type for the AI agent graph service.
 *
 * @property strategy The AI agent graph strategy governing the behavior and structure of the service.
 * @property inputType The TypeToken representation of the input type.
 * @property outputType The TypeToken representation of the output type.
 * @property promptExecutor The executor responsible for handling and orchestrating prompts.
 * @property toolRegistry The registry managing the tools available for the service.
 * @property config [AIAgentConfig] containing initial agent configuration for the builder
 * @property featureInstallers A collection of feature configuration functions to be applied to the service.
 */
public class GraphAgentServiceBuilder<Input, Output> internal constructor(
    private val strategy: AIAgentGraphStrategy<Input, Output>,
    private val inputType: TypeToken,
    private val outputType: TypeToken,
    promptExecutor: PromptExecutor?,
    toolRegistry: ToolRegistry,
    config: AIAgentConfig,
    private var featureInstallers: MutableList<FeatureContext.() -> Unit> = mutableListOf(),
) : AIAgentBuilderBase<GraphAgentServiceBuilder<Input, Output>>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    id = null,
    config = config,
    clock = Clock.System,
) {
    override fun self(): GraphAgentServiceBuilder<Input, Output> = this

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
        val executor = validatedPromptExecutor

        val installCombined: FeatureContext.() -> Unit = {
            featureInstallers.forEach { it(this) }
        }

        return GraphAIAgentService(
            promptExecutor = executor,
            agentConfig = validatedConfig,
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
    promptExecutor: PromptExecutor? = null,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    config: AIAgentConfig,
    private var featureInstallers: MutableList<FunctionalAIAgent.FeatureContext.() -> Unit> = mutableListOf(),
) : AIAgentBuilderBase<FunctionalAgentServiceBuilder<Input, Output>>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    id = null,
    config = config,
    clock = Clock.System,
) {
    override fun self(): FunctionalAgentServiceBuilder<Input, Output> = this

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
        val installCombined: FunctionalAIAgent.FeatureContext.() -> Unit = {
            featureInstallers.forEach { it(this) }
        }

        return FunctionalAIAgentService(
            promptExecutor = validatedPromptExecutor,
            agentConfig = validatedConfig,
            toolRegistry = toolRegistry,
            strategy = strategy,
            installFeatures = installCombined
        )
    }
}
