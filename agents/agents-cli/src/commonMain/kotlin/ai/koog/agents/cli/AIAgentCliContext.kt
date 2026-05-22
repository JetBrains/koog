package ai.koog.agents.cli

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline

/**
 * Context for CLI-based AI agents.
 *
 * This context uses the functional pipeline which provides raw pipeline functionality
 * without any additional features beyond the base [ai.koog.agents.core.agent.context.AIAgentContext] interface.
 */
@OptIn(InternalAgentsApi::class)
public class AIAgentCliContext(
    override val environment: AIAgentEnvironment,
    override val agentId: String,
    override val runId: String,
    override val agentInput: Any?,
    override val config: AIAgentConfig,
    override val llm: AIAgentLLMContext,
    override val stateManager: AIAgentStateManager,
    override val storage: AIAgentStorage,
    override val strategyName: String,
    override val pipeline: AIAgentFunctionalPipeline,
    override var executionInfo: AgentExecutionInfo,
    override val parentContext: AIAgentContext? = null
) : AIAgentContext {

    /**
     * Copies the current [AIAgentCliContext] with the specified parameters.
     */
    public fun copy(
        environment: AIAgentEnvironment = this.environment,
        agentId: String = this.agentId,
        runId: String = this.runId,
        agentInput: Any? = this.agentInput,
        config: AIAgentConfig = this.config,
        llm: AIAgentLLMContext = this.llm,
        stateManager: AIAgentStateManager = this.stateManager,
        storage: AIAgentStorage = this.storage,
        strategyName: String = this.strategyName,
        pipeline: AIAgentFunctionalPipeline = this.pipeline,
        executionInfo: AgentExecutionInfo = this.executionInfo,
        parentRootContext: AIAgentContext? = this.parentContext
    ): AIAgentCliContext = AIAgentCliContext(
        environment = environment,
        agentId = agentId,
        runId = runId,
        agentInput = agentInput,
        config = config,
        llm = llm,
        stateManager = stateManager,
        storage = storage,
        strategyName = strategyName,
        pipeline = pipeline,
        executionInfo = executionInfo,
        parentContext = parentRootContext
    )
}
