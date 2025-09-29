@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentNonGraphFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi

/**
 * Represents the core AI agent for processing input and generating output using
 * a defined configuration, toolset, and prompt execution pipeline.
 *
 * @param Input The type of input data expected by the agent.
 * @param Output The type of output data produced by the agent.
 * @param id The unique identifier for the agent instance.
 * @param promptExecutor The executor responsible for processing prompts and interacting with language models.
 * @param agentConfig The configuration for the agent, including the prompt structure and execution parameters.
 * @param toolRegistry The registry of tools available for the agent. Defaults to an empty registry if not specified.
 */
@OptIn(InternalAgentsApi::class)
public class FunctionalAIAgent<Input, Output>(
    public val promptExecutor: PromptExecutor,
    override val agentConfig: AIAgentConfig,
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    strategy: AIAgentFunctionalStrategy<Input, Output>,
    id: String? = null,
    public val clock: Clock = Clock.System,
    @property:InternalAgentsApi public val featureContext: FeatureContext.() -> Unit = {}
) : StatefulSingleUseAIAgent<Input, Output, AIAgentFunctionalContext>(
    strategy = strategy,
    logger = logger,
    agentId = id,
) {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val pipeline: AIAgentNonGraphPipeline = AIAgentNonGraphPipeline(clock)

    private val environment = GenericAgentEnvironment(
        this@FunctionalAIAgent.id,
        strategy.name,
        logger,
        toolRegistry,
        pipeline = pipeline
    )

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
            feature: AIAgentNonGraphFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentNonGraphFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    init {
        FeatureContext(this).featureContext()
    }

    override suspend fun prepareContext(agentInput: Input, runId: String): AIAgentFunctionalContext {
        val llm = AIAgentLLMContext(
            tools = toolRegistry.tools.map { it.descriptor },
            toolRegistry = toolRegistry,
            prompt = agentConfig.prompt,
            model = agentConfig.model,
            promptExecutor = PromptExecutorProxy(
                executor = promptExecutor,
                pipeline = pipeline,
                runId = runId
            ),
            environment = environment,
            config = agentConfig,
            clock = clock
        )

        return AIAgentFunctionalContext(
            environment,
            this@FunctionalAIAgent,
            runId,
            agentInput,
            agentConfig,
            llm,
            AIAgentStateManager(),
            storage = AIAgentStorage(),
            strategyName = strategy.name,
            pipeline = pipeline
        )
    }
}
