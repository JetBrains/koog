package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentPlannerPipeline

/**
 * Represents a context for the AI agent of [ai.koog.agents.planner.PlannerAIAgent] type, responsible for managing
 * execution pipelines, configurations, and other contextual data required for agent operations.
 *
 * @constructor
 * Primary constructor for internal use, leveraging a delegate of type [AIAgentFunctionalContextBaseImpl].
 *
 * @property delegate The delegated functional context implementation, encapsulating the core functionality.
 */
public class AIAgentPlannerContext private constructor(
    delegate: AIAgentFunctionalContextBaseImpl<AIAgentPlannerPipeline>
) : AIAgentFunctionalContextBase<AIAgentPlannerPipeline>(delegate) {

    /**
     * Constructs an instance of AIAgentPlannerContext by delegating to the base implementation
     * for the AI agent's functional context. This constructor initializes the context with
     * the provided components and parameters to facilitate agent planning and execution.
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
    public constructor (
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
    ) : this(
        AIAgentFunctionalContextBaseImpl(
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
        )
    )

    /**
     * Creates a copy of the current [AIAgentFunctionalContext], allowing for selective overriding of its properties.
     * This method is particularly useful for creating modified contexts during agent execution without mutating
     * the original context - perfect for when you need to experiment with different configurations or
     * pass tweaked contexts down the execution pipeline while keeping the original pristine!
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or retain the current playground if not specified.
     * @param agentId The unique agent identifier, or keep the same identity if you're feeling attached.
     * @param runId The run identifier for this execution adventure, or stick with the current journey.
     * @param agentInput The input data for the agent - fresh data or the same trusty input, your choice!
     * @param config The [AIAgentConfig] for the new context, or keep the current rulebook.
     * @param llm The [AIAgentLLMContext] to be used, or maintain the current AI conversation partner.
     * @param stateManager The [AIAgentStateManager] to be used, or preserve the current state keeper.
     * @param storage The [AIAgentStorage] to be used, or stick with the current memory bank.
     * @param strategyName The strategy name, or maintain the current game plan.
     * @param pipeline The [AIAgentPlannerContext] to be used, or keep the current execution superhighway.
     * @param parentRootContext The parent root context, or maintain the current family tree.
     * @return A new [AIAgentFunctionalContext] with your desired modifications applied!
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
        AIAgentFunctionalContextBaseImpl(
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
    )
}
