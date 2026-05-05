package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import kotlin.time.Clock

/**
 * Minimal [AIAgentContext] for unit-testing dispatch and tool wiring.
 *
 * Provides a real (no-feature) [pipeline] so the merged environment can fire lifecycle events
 * harmlessly. Only [agentId] / [runId] / [pipeline] / [executionInfo] are populated meaningfully;
 * other fields throw on access.
 */
@OptIn(InternalAgentsApi::class)
internal class StubAIAgentContext(
    override val agentId: String,
    override val runId: String,
) : AIAgentContext {
    private val stubConfig: AIAgentConfig = AIAgentConfig(
        prompt = prompt("stub") { },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 1,
    )

    override val environment: AIAgentEnvironment get() = error("not used in stub context")
    override val pipeline: AIAgentPipeline = AIAgentGraphPipeline(stubConfig, Clock.System)
    override val agentInput: Any? = null
    override val config: AIAgentConfig get() = stubConfig
    override val llm: AIAgentLLMContext get() = error("not used in stub context")
    override val stateManager: AIAgentStateManager get() = error("not used in stub context")
    override val storage: AIAgentStorage get() = error("not used in stub context")
    override val strategyName: String = "stub-strategy"
    override val parentContext: AIAgentContext? = null
    override var executionInfo: AgentExecutionInfo = AgentExecutionInfo(parent = null, partName = agentId)

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun store(key: AIAgentStorageKey<*>, value: Any) = error("not used in stub context")

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun <T> get(key: AIAgentStorageKey<*>): T? = error("not used in stub context")

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun remove(key: AIAgentStorageKey<*>): Boolean = error("not used in stub context")

    override suspend fun getHistory(): List<Message> = emptyList()
}
