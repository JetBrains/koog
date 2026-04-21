package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline

/**
 * Represents a context for the AI agent of PlannerAIAgent type, responsible for managing
 * execution pipelines, configurations, and other contextual data required for agent operations.
 *
 * @param environment The AI agent's operating environment, which provides tools, error reporting,
 *        and mechanism for execution.
 * @param agentId A unique identifier for the agent.
 * @param runId A unique identifier representing the current execution run.
 * @param agentInput Input data provided to the agent for processing or decision-making.
 * @param config Configuration details for the AI agent, dictating its behavior and settings.
 * @param llm The context for interactions with the underlying language model.
 * @param stateManager Responsible for managing the state of the agent across executions.
 * @param storage Provides long-term memory or storage for the agent's operations.
 * @param strategyName Name of the strategy guiding the agent's behavior.
 * @param pipeline The planning pipeline that orchestrates the agent's decision-making processes.
 * @param executionInfo Metadata and details regarding the agent's execution, such as timestamps or states.
 * @param parentContext An optional parent context, allowing nested or hierarchical composition of agent contexts.
 */
public class AIAgentPlannerContext(
    environment: AIAgentEnvironment,
    agentId: String,
    runId: String,
    agentInput: Any?,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    strategyName: String,
    pipeline: AIAgentPlannerPipeline,
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentContext? = null
) : AIAgentFunctionalContextBase<AIAgentPlannerPipeline>(
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
    parentContext = parentContext
) {

    /**
     * Creates a copy of the current [AIAgentPlannerContext], allowing for selective overriding of its properties.
     * This method is useful for creating modified contexts during agent execution without mutating the original context.
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or the current one if not specified.
     * @param agentId The unique agent identifier, or the current one if not specified.
     * @param runId The run identifier, or the current run ID if not specified.
     * @param agentInput The input data for the agent, or the current input if not specified.
     * @param config The [AIAgentConfig] for the new context, or the current configuration if not specified.
     * @param llm The [AIAgentLLMContext] to be used, or the current LLM context if not specified.
     * @param stateManager The [AIAgentStateManager] to be used, or the current state manager if not specified.
     * @param storage The [AIAgentStorage] to be used, or the current storage if not specified.
     * @param strategyName The strategy name, or the current strategy name if not specified.
     * @param pipeline The [AIAgentPlannerPipeline] to be used, or the current pipeline if not specified.
     * @param executionInfo The [AgentExecutionInfo] to be used, or the current execution info if not specified.
     * @param parentRootContext The parent context, or the current parent context if not specified.
     * @return A new [AIAgentPlannerContext] with the specified modifications applied.
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
        pipeline: AIAgentPlannerPipeline = this.pipeline,
        executionInfo: AgentExecutionInfo = this.executionInfo,
        parentRootContext: AIAgentContext? = this.parentContext,
    ): AIAgentPlannerContext = AIAgentPlannerContext(
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
