@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentNonGraphFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
public class FunctionalAIAgent<Input, Output>(
    public val promptExecutor: PromptExecutor,
    override val agentConfig: AIAgentConfig,
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    public val strategy: AIAgentFunctionalStrategy<Input, Output>,
    id: String? = null,
    public val clock: Clock = Clock.System,
    featureContext: FeatureContext.() -> Unit = {}
) : AIAgent<Input, Output> {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val id: String by lazy { id ?: Uuid.random().toString() }

    /**
     * FunctionalAIAgentSession manages the execution context of a functional AI agent session.
     * It extends the generic AIAgentSession interface, allowing for the initialization, execution,
     * and completion of AI-driven tasks with specific input and output types.
     *
     * Key responsibilities of this class include:
     * - Managing the session state and ensuring that concurrent sessions are not executed.
     * - Preparing the pipeline and associated features required for session execution.
     * - Establishing and maintaining contextual data specific to the current session.
     * - Delegating the execution process to the strategy configured for the AI agent.
     *
     * This class is designed for use within the FunctionalAIAgent context and relies
     * on several dependencies, such as a tool registry, configuration data, and a strategy.
     *
     * @constructor Constructs a session bound to a specific FunctionalAIAgent instance,
     *              utilizing its configuration settings and operational components.
     */
    public inner class FunctionalAIAgentSession : AIAgentSession<Input, Output> {
        private var isRunning = false

        private val runningMutex = Mutex()

        private val environment = GenericAgentEnvironment(
            this@FunctionalAIAgent.id,
            strategy.name,
            logger,
            toolRegistry,
            pipeline = pipeline
        )

        private val resultDeferred: CompletableDeferred<Output> = CompletableDeferred()

        private lateinit var sessionJob: Job

        private lateinit var context: AIAgentFunctionalContext

        override suspend fun withContext(action: suspend AIAgentContext.() -> Unit) {
            context.action()
        }

        override suspend fun stop() {
            sessionJob.cancel()
        }

        override suspend fun launch(agentInput: Input, scope: CoroutineScope) {
            runningMutex.withLock {
                if (isRunning) {
                    throw IllegalStateException("Agent is already running")
                }
                isRunning = true
            }

            pipeline.prepareFeatures()
            val runId = Uuid.random().toString()

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

            this@FunctionalAIAgentSession.context = AIAgentFunctionalContext(
                environment,
                this@FunctionalAIAgent.id,
                runId,
                agentInput,
                agentConfig,
                llm,
                AIAgentStateManager(),
                storage = AIAgentStorage(),
                strategyName = strategy.name,
                pipeline = pipeline
            )


            sessionJob = scope.launch {
                withContext(
                    AgentRunInfoContextElement(
                        agentId = this@FunctionalAIAgent.id,
                        runId = runId,
                        agentConfig = agentConfig,
                        strategyName = strategy.name
                    )
                ) {
                    val result = strategy.execute(this@FunctionalAIAgentSession.context, agentInput)

                    runningMutex.withLock {
                        isRunning = false
                    }

                    resultDeferred.complete(result)
                }
            }
        }

        override suspend fun result(): Output = resultDeferred.await()
    }

    override suspend fun launch(agentInput: Input, scope: CoroutineScope): AIAgentSession<Input, Output> =
        FunctionalAIAgentSession().also { it.launch(agentInput, scope) }

    private val pipeline = AIAgentNonGraphPipeline(clock)

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

    private var isRunning = false

    private val runningMutex = Mutex()

    private fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentNonGraphFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    init {
        FeatureContext(this).featureContext()
    }

    override suspend fun close() {
        pipeline.onAgentClosing(agentId = this@FunctionalAIAgent.id)
        pipeline.closeFeaturesStreamProviders()
    }
}
