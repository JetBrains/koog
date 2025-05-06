package ai.grazie.code.agents.local.engine

import ai.grazie.code.agents.core.model.message.AgentToEnvironmentMessage
import ai.grazie.code.agents.core.model.message.EnvironmentToAgentMessage
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.grazie.code.agents.local.environment.LocalAgentEnvironmentProxy
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.channels.Channel

class LocalAgentSession(
    agent: KotlinAIAgent,
    val strategy: LocalAgentStrategy,
    val config: LocalAgentConfig,
    val tools: ToolRegistry,
    val userMessage: String,
    val promptExecutor: PromptExecutor,
    internal val pipeline: AIAgentPipeline
) {
    val sessionUuid: UUID = UUID.Companion.random()
    val engineChannel = Channel<AgentToEnvironmentMessage>(capacity = Channel.Factory.UNLIMITED)
    val environmentChannel = Channel<EnvironmentToAgentMessage>(capacity = Channel.Factory.UNLIMITED)

    val environment = LocalAgentEnvironmentProxy(
        engineChannel,
        environmentChannel,
        sessionUuid,
        strategy,
        pipeline
    ).let { pipeline.transformEnvironment(strategy, agent, it) }

    suspend fun run() {
        strategy.run(
            sessionUuid = sessionUuid,
            userInput = userMessage,
            toolRegistry = tools,
            promptExecutor = promptExecutor,
            environment = environment,
            config = config,
            pipeline = pipeline,
        )
    }
}