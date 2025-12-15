package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ContextualAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.ContextualPromptExecutor
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlin.reflect.KType

/**
 * Represents an implementation of an AI agent that provides functionalities to execute prompts,
 * manage tools, handle agent pipelines, and interact with various configurable strategies and features.
 *
 * The agent operates within a coroutine scope and leverages a tool registry and feature context
 * to enable dynamic additions or configurations during its lifecycle. Its behavior is driven
 * by a local agent strategy and executed via a prompt executor.
 *
 * @param Input Type of agent input.
 * @param Output Type of agent output.
 *
 * @property inputType [KType] representing [Input] - agent input.
 * @property outputType [KType] representing [Output] - agent output.
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy The execution strategy defining how the agent processes input and produces output.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
 * @property clock The clock used to calculate message timestamps
 * @constructor Initializes the AI agent instance and prepares the feature context and pipeline for use.
 */
@Suppress("ktlint:standard:wrapping")
@OptIn(InternalAgentsApi::class)
public open class GraphAIAgent<Input, Output>(
    public val inputType: KType,
    public val outputType: KType,
    public val promptExecutor: PromptExecutor,
    override val agentConfig: AIAgentConfig,
    override val strategy: AIAgentGraphStrategy<Input, Output>,
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    public val clock: Clock = Clock.System,
    @property:InternalAgentsApi
    public val installFeatures: FeatureContext.() -> Unit = {}
) : StatefulSingleUseAIAgent<Input, Output, AIAgentGraphContextBase>(
    logger = logger,
    id = id,
) {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val pipeline: AIAgentGraphPipeline = AIAgentGraphPipeline(clock)

    /**
     * The context for adding and configuring features in a Kotlin AI Agent instance.
     *
     * Note: The method is used to hide internal install() method from a public API to prevent
     *       calls in an [AIAgent] instance, like `agent.install(MyFeature) { ... }`.
     *       This makes the API a bit stricter and clear.
     */
    public class FeatureContext internal constructor(private val agent: GraphAIAgent<*, *>) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentGraphFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.pipeline.install(feature, configure)
        }
    }

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun prepareContext(agentInput: Input, runId: String, eventId: String): AIAgentGraphContextBase {
        val stateManager = AIAgentStateManager()
        val storage = AIAgentStorage()

        val executionInfo = AgentExecutionInfo(parent = null, partName = id)
        val initialEnvironment = prepareAgentEnvironment(eventId = eventId, executionInfo = executionInfo)

        // Initial
        val initialLLMContext = AIAgentLLMContext(
            tools = toolRegistry.tools.map { it.descriptor },
            toolRegistry = toolRegistry,
            prompt = agentConfig.prompt,
            model = agentConfig.model,
            responseProcessor = agentConfig.responseProcessor,
            promptExecutor = promptExecutor,
            environment = initialEnvironment,
            config = agentConfig,
            clock = clock
        )

        val agentContext = AIAgentGraphContext(
            environment = initialEnvironment,
            agentId = id,
            agentInput = agentInput,
            agentInputType = inputType,
            config = agentConfig,
            llm = initialLLMContext,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategy.name,
            pipeline = pipeline,
            executionInfo = executionInfo,
            parentContext = null,
        )

        val contextualEnvironment = ContextualAgentEnvironment(
            environment = initialEnvironment,
            context = agentContext,
        )

        val contextualPromptExecutor = ContextualPromptExecutor(
            executor = promptExecutor,
            context = agentContext,
        )

        val updatedLLMContext = agentContext.llm.copy(
            environment = contextualEnvironment,
            promptExecutor = contextualPromptExecutor,
        )

        agentContext.replace(agentContext.copy(
            executionInfo = executionInfo,
            llm = updatedLLMContext,
            environment = contextualEnvironment,
        ))

        return agentContext
    }

    /**
     * Prepares the environment for the AI agent by initializing a base environment
     * and applying any registered environment transformations defined in the pipeline.
     *
     * Environment (initially equal to the current agent), transformed by some features
     * (ex: testing feature transforms it into a MockEnvironment with mocked tools
     *
     * @return An instance of `AIAgentEnvironment` that represents the finalized environment
     *         for the AI agent after applying all transformations.
     */
    private suspend fun prepareAgentEnvironment(eventId: String, executionInfo: AgentExecutionInfo): AIAgentEnvironment {
        // Create a base environment implementation
        val environment = GenericAgentEnvironment(
            agentId = id,
            logger = logger,
            toolRegistry = toolRegistry,
        )

        val preparedEnvironment = pipeline.onAgentEnvironmentTransforming(
            eventId = eventId,
            executionInfo = executionInfo,
            agent = this,
            baseEnvironment = environment,
        )

        return preparedEnvironment
    }
}
