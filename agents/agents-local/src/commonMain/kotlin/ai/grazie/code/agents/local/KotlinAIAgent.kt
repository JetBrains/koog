package ai.grazie.code.agents.local

import ai.grazie.code.agents.core.AIAgent
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.model.message.*
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.engine.LocalAgentSession
import ai.grazie.code.agents.local.engine.UnexpectedAgentMessageException
import ai.grazie.code.agents.local.engine.UnexpectedDoubleInitializationException
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.code.agents.local.features.KotlinAIAgentFeature
import ai.grazie.code.agents.local.features.config.FeatureConfig
import ai.grazie.code.agents.local.model.message.LocalAgentEnvironmentToAgentInitializeMessage
import ai.grazie.code.agents.local.model.message.LocalAgentEnvironmentToolResultToAgentContent
import ai.grazie.utils.mpp.SuitableForIO
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.text.TextContentBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KotlinAIAgent(
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    strategy: LocalAgentStrategy,
    eventHandler: EventHandler = EventHandler.NO_HANDLER,
    agentConfig: LocalAgentConfig,
    val promptExecutor: CodePromptExecutor,
    private val cs: CoroutineScope,
    installFeatures: suspend FeatureContext.() -> Unit = {}
) : AIAgent<LocalAgentStrategy, LocalAgentConfig>(
    strategy = strategy,
    toolRegistry = toolRegistry,
    agentConfig = agentConfig,
    eventHandler = eventHandler
) {

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

    override suspend fun CoroutineScope.init(prompt: String): AgentToEnvironmentMessage {
        val activeSession =
            LocalAgentSession(
                this@KotlinAIAgent,
                strategy,
                agentConfig,
                toolRegistry,
                prompt,
                promptExecutor,
                pipeline
            )
        session = activeSession
        launch(Dispatchers.SuitableForIO) {
            activeSession.run()
        }

        return activeSession.engineChannel.receive()
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
        val activeSession = session ?: return AgentTerminationToEnvironmentMessage(UUID.random())

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
}
