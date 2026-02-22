package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline

/**
 * Represents the context for an AI agent of [ai.koog.agents.core.agent.FunctionalAIAgent] type,
 * serving as the execution environment and state holder
 * while an agent operates within a predefined pipeline. It extends [AIAgentFunctionalContextBase] and is
 * designed to allow configuration, state management, and storage for an agent's functional operations.
 *
 * @constructor Primary constructor used internally for context delegation. External usage is enabled through
 * the public secondary constructor which initializes the context with a complete set of required parameters.
 */
public class AIAgentFunctionalContext private constructor(
    delegate: AIAgentFunctionalContextBaseImpl<AIAgentFunctionalPipeline>
) : AIAgentFunctionalContextBase<AIAgentFunctionalPipeline>(delegate) {

    /**
     * Secondary constructor for the [AIAgentFunctionalContext] class that initializes the context
     * by delegating to the primary constructor of [AIAgentFunctionalContextBaseImpl]. This constructor
     * simplifies the creation of an instance by reusing the initialization logic within the base implementation.
     *
     * @param environment The [AIAgentEnvironment] in which the AI agent operates, facilitating interaction
     *        with the external environment for tool execution and error reporting.
     * @param agentId A unique identifier for the agent, used to distinguish it from other agents.
     * @param runId An identifier representing the execution run of the agent, useful for tracking and managing runs.
     * @param agentInput The input data provided to the agent, which can guide its execution or decision-making process.
     * @param config The [AIAgentConfig] object containing configuration information for the agent, such as behavior settings.
     * @param llm The [AIAgentLLMContext] providing access to the large language model interactions for generating outputs.
     * @param stateManager The [AIAgentStateManager] responsible for managing and persisting the state of the agent during its lifecycle.
     * @param storage The [AIAgentStorage] interface facilitating storage and retrieval of data in the agent's environment.
     * @param strategyName The name of the strategic approach or plan under which the agent is functioning.
     * @param pipeline The [AIAgentFunctionalPipeline] defining the functional execution flow of the agent's operations.
     * @param executionInfo The [AgentExecutionInfo] containing metadata and runtime information about the agent's current execution.
     * @param parentContext An optional reference to the parent [AIAgentContext], enabling hierarchical context structure if needed.
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
        pipeline: AIAgentFunctionalPipeline,
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
     * @param pipeline The [AIAgentFunctionalPipeline] to be used, or keep the current execution superhighway.
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
        pipeline: AIAgentFunctionalPipeline = this.pipeline,
        executionInfo: AgentExecutionInfo = this.executionInfo,
        parentRootContext: AIAgentContext? = this.parentContext,
    ): AIAgentFunctionalContext = AIAgentFunctionalContext(
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
