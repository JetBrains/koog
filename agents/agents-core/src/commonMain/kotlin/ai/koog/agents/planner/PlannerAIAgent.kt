package ai.koog.agents.planner

import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.ContextualAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPlannerFeature
import ai.koog.agents.core.feature.ContextualPromptExecutor
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlin.jvm.JvmStatic

/**
 * Represents an instance of planner agent using [AIAgentPlannerStrategy].
 *
 * @param State The type of the state.
 * @param Plan The type of the plan.
 * @property id The unique identifier for the agent instance.
 * @property promptExecutor The executor responsible for processing prompts.
 * @property agentConfig The configuration for the agent.
 * @property strategy The strategy for processing input and generating output.
 * @property toolRegistry The registry of tools available for the agent. Defaults to an empty registry if not specified.
 * @property clock The clock used to calculate message timestamps
 * @property installFeatures Lambda for installing additional features within the agent environment.
 */
@OptIn(InternalAgentsApi::class)
public class PlannerAIAgent<Input, Output>(
    public val promptExecutor: PromptExecutor,
    override val agentConfig: AIAgentConfig,
    override val strategy: AIAgentPlannerStrategy<Input, Output, *>,
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    public val clock: Clock = kotlin.time.Clock.System,
    @property:InternalAgentsApi
    public val installFeatures: FeatureContext.() -> Unit = {}
) : AIAgentBase<Input, Output, AIAgentPlannerContext>(
    logger = logger,
    id = id,
) {
    /**
     * Companion object providing the static `builder()` method.
     */
    public companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * Creates a new instance of [PlannerAIAgentBuilder] for configuring and building a planner AI agent.
         */
        @JvmStatic
        public fun <Input, Output> builder(
            strategy: AIAgentPlannerStrategy<Input, Output, *>
        ): PlannerAIAgentBuilder<Input, Output> = PlannerAIAgentBuilder(strategy)
    }

    override val pipeline: AIAgentPlannerPipeline = AIAgentPlannerPipeline(agentConfig, clock)

    /**
     * Represents a context for managing and configuring features in an AI agent.
     * Provides functionality to install and configure features into a specific instance of an AI agent.
     */
    public class FeatureContext internal constructor(private val agent: PlannerAIAgent<*, *>) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentPlannerFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.pipeline.install(feature, configure)
        }
    }

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun prepareContext(agentInput: Input, runId: String, eventId: String): AIAgentPlannerContext {
        val environment = GenericAgentEnvironment(
            agentId = this.id,
            logger = logger,
            toolRegistry = toolRegistry,
        )

        val initialLLMContext = AIAgentLLMContext(
            tools = toolRegistry.tools.map { it.descriptor },
            toolRegistry = toolRegistry,
            prompt = agentConfig.prompt,
            model = agentConfig.model,
            responseProcessor = agentConfig.responseProcessor,
            promptExecutor = promptExecutor,
            environment = environment,
            config = agentConfig,
            clock = clock
        )

        val executionInfo = AgentExecutionInfo(parent = null, partName = id)

        // Context
        val initialAgentContext = AIAgentPlannerContext(
            environment = environment,
            agentId = id,
            runId = runId,
            agentInput = agentInput,
            config = agentConfig,
            llm = initialLLMContext,
            stateManager = AIAgentStateManager(),
            storage = AIAgentStorage(),
            strategyName = strategy.name,
            pipeline = pipeline,
            executionInfo = executionInfo,
            parentContext = null,
        )

        // Updated environment
        val contextualEnvironment = ContextualAgentEnvironment(
            environment = environment,
            context = initialAgentContext,
        )

        val contextualPromptExecutor = ContextualPromptExecutor(
            executor = promptExecutor,
            context = initialAgentContext,
        )

        val updatedLLMContext = initialAgentContext.llm.copy(
            environment = contextualEnvironment,
            promptExecutor = contextualPromptExecutor,
        )

        val updatedAgentContext = initialAgentContext.copy(
            llm = updatedLLMContext,
            environment = contextualEnvironment,
            parentRootContext = initialAgentContext.parentContext, // Keep the original parent context
        )

        return updatedAgentContext
    }
}
