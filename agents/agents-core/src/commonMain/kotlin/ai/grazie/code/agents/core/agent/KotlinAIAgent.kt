package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.engine.LocalAgentSession
import ai.grazie.code.agents.core.engine.UnexpectedAgentMessageException
import ai.grazie.code.agents.core.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.AgentEnvironmentUtils.mapToToolResult
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.code.agents.core.feature.config.FeatureConfig
import ai.grazie.code.agents.core.model.AIAgentServiceError
import ai.grazie.code.agents.core.model.AIAgentServiceErrorType
import ai.grazie.code.agents.core.tool.tools.TerminationTool
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.text.TextContentBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class KotlinAIAgent(
    toolRegistry: ToolRegistry = ToolRegistry.Companion.EMPTY,
    strategy: LocalAgentStrategy,
    eventHandler: EventHandler = EventHandler.Companion.NO_HANDLER,
    agentConfig: LocalAgentConfig,
    val promptExecutor: PromptExecutor,
    private val cs: CoroutineScope,
    installFeatures: suspend FeatureContext.() -> Unit = {}
) : AIAgentBase<LocalAgentStrategy, LocalAgentConfig>(
    strategy = strategy,
    toolRegistry = toolRegistry,
    agentConfig = agentConfig,
    eventHandler = eventHandler
), AgentEnvironment {
    companion object {
        private val logger =
            LoggerFactory.create("ai.grazie.code.agents.core.agent.${KotlinAIAgent::class.simpleName}")
    }

    /**
     * The context for adding and configuring features in a Kotlin AI Agent instance.
     *
     * Note: The method is used to hide internal install() method from a public API to prevent
     *       calls in an [KotlinAIAgent] instance, like `agent.install(MyFeature) { ... }`.
     *       This makes the API a bit stricter and clear.
     */
    class FeatureContext internal constructor(val agent: KotlinAIAgent) {
        suspend fun <Config : FeatureConfig, Feature : Any> install(
            feature: KotlinAIAgentFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private val pipeline = AIAgentPipeline()

    init {
        cs.launch(context = Dispatchers.SuitableForIO, start = CoroutineStart.UNDISPATCHED) {
            FeatureContext(this@KotlinAIAgent).installFeatures()
            pipeline.onAgentCreated(strategy, this@KotlinAIAgent)
        }
    }

    private suspend fun <Config : FeatureConfig, Feature : Any> install(
        feature: KotlinAIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    private var session: LocalAgentSession? = null

    override suspend fun init(prompt: String): AgentToEnvironmentMessage {
        val activeSession =
            LocalAgentSession(
                this,
                strategy,
                agentConfig,
                toolRegistry,
                prompt,
                promptExecutor,
                pipeline
            )

        session = activeSession
        cs.launch(Dispatchers.SuitableForIO) {
            activeSession.run()
        }

        return activeSession.engineChannel.receive()
    }

    private var isRunning = false
    private var sessionUuid: UUID? = null
    private val runningMutex = Mutex()

    override suspend fun run(prompt: String) {
        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
            sessionUuid = UUID.random()
        }

        strategy.run(
            sessionUuid = sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            userInput = prompt,
            toolRegistry = toolRegistry,
            promptExecutor = promptExecutor,
            environment = this,
            config = agentConfig,
            pipeline = pipeline
        )

        runningMutex.withLock {
            isRunning = false
            sessionUuid = null
        }
    }

    override suspend fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult?
    ): EnvironmentToolResultToAgentContent = LocalAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    override suspend fun sendToAgent(message: EnvironmentToAgentMessage): AgentToEnvironmentMessage {
        val sessionId = when (message) {
            is EnvironmentToAgentErrorMessage -> {
                message.sessionUuid
            }

            is EnvironmentToolResultToAgentMessage -> {
                message.sessionUuid
            }

            is EnvironmentToAgentTerminationMessage -> {
                message.sessionUuid
            }

            is LocalAgentEnvironmentToAgentInitializeMessage -> throw UnexpectedDoubleInitializationException()

            else -> throw UnexpectedAgentMessageException()
        }
        val activeSession = session ?: return AgentTerminationToEnvironmentMessage(UUID.Companion.random())

        if (sessionId != activeSession.sessionUuid)
            throw IllegalStateException("Session ID mismatch")

        activeSession.environmentChannel.send(message)
        return activeSession.engineChannel.receive()
    }

    suspend fun run(builder: suspend TextContentBuilder.() -> Unit) {
        pipeline.awaitFeaturesStreamProvidersReady()

        val prompt = TextContentBuilder().apply { this.builder() }.build()
        run(prompt = prompt)

        pipeline.closeFeaturesStreamProviders()
    }

    private fun formatLog(message: String): String = "$message [${strategy.name}, ${sessionUuid?.text ?: throw IllegalStateException("Session UUID is null")}]"

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        logger.info { formatLog("Executing tools '$toolCalls'") }
        pipeline.onBeforeToolCalls(toolCalls)

        val message = AgentToolCallsToEnvironmentMessage(
            sessionUuid = sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = strategy.name,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        pipeline.onAfterToolCalls(results)
        return results
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) { formatLog("Reporting problem: ${exception.message}") }
        processError(
            AIAgentServiceError(
                type = AIAgentServiceErrorType.UNEXPECTED_ERROR,
                message = exception.message ?: "unknown error"
            )
        )
    }

    override suspend fun sendTermination(result: String?) {
        logger.info { formatLog("Sending final result") }
        val message = AgentTerminationToEnvironmentMessage(
            sessionUuid ?: throw IllegalStateException("Session UUID is null"),
            content = AgentToolCallToEnvironmentContent(
                agentId = strategy.name,
                toolCallId = null,
                toolName = TerminationTool.NAME,
                toolArgs = JsonObject(mapOf(TerminationTool.ARG to JsonPrimitive(result)))
            )
        )

        terminate(message)
    }
}