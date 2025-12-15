package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ContextualAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.ContextualPromptExecutor
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock

/**
 * Represents the core AI agent for processing input and generating output using
 * a defined configuration, toolset, and prompt execution pipeline.
 *
 * @param Input The type of input data expected by the agent.
 * @param Output The type of output data produced by the agent.
 * @property id The unique identifier for the agent instance.
 * @property promptExecutor The executor responsible for processing prompts and interacting with language models.
 * @property agentConfig The configuration for the agent, including the prompt structure and execution parameters.
 * @property strategy The strategy for processing input and generating output.
 * @property toolRegistry The registry of tools available for the agent. Defaults to an empty registry if not specified.
 * @property clock The clock used to calculate message timestamps
 * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 */
@OptIn(InternalAgentsApi::class)
public class FunctionalAIAgent<Input, Output>(
    public val promptExecutor: PromptExecutor,
    override val agentConfig: AIAgentConfig,
    override val strategy: AIAgentFunctionalStrategy<Input, Output>,
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    public val clock: Clock = Clock.System,
    @property:InternalAgentsApi
    public val installFeatures: FeatureContext.() -> Unit = {}
) : StatefulSingleUseAIAgent<Input, Output, AIAgentFunctionalContext>(
    logger = logger,
    id = id,
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val pipeline: AIAgentFunctionalPipeline = AIAgentFunctionalPipeline(clock)

    /**
     * Represents a context for managing and configuring features in an AI agent.
     * Provides functionality to install and configure features into a specific instance of an AI agent.
     */
    public class FeatureContext internal constructor(private val agent: FunctionalAIAgent<*, *>) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentFunctionalFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.pipeline.install(feature, configure)
        }
    }

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun prepareContext(agentInput: Input, runId: String, eventId: String): AIAgentFunctionalContext {
        val environment = GenericAgentEnvironment(
            agentId = id,
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
        val preparedEnvironment = prepareEnvironment()

        // Context
        val initialAgentContext = AIAgentFunctionalContext(
            environment = preparedEnvironment,
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
            parentContext = null
        )

        // Updated environment
        val contextualEnvironment = ContextualAgentEnvironment(
            environment = preparedEnvironment,
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

    //region Private Methods

    private fun prepareEnvironment(): AIAgentEnvironment {
        val baseEnvironment = GenericAgentEnvironment(
            agentId = id,
            logger = logger,
            toolRegistry = toolRegistry,
        )

        return baseEnvironment
    }

    //endregion Private Methods
}
