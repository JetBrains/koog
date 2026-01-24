@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Represents the execution context for an AI agent operating in a loop.
 * It provides access to critical parts such as the environment, configuration, large language model (LLM) context,
 * state management, and storage. Additionally, it enables the agent to store, retrieve, and manage context-specific data
 * during its execution lifecycle.
 *
 * @property environment The environment interface allowing the agent to interact with the external world,
 * including executing tools and reporting problems.
 * @property agentId A unique identifier for the agent, differentiating it from other agents in the system.
 * @property runId A unique identifier for the current run or instance of the agent's operation.
 * @property agentInput The input data passed to the agent, which can be of any type, depending on the agent's context.
 * @property config The configuration settings for the agent, including its prompt and model details,
 * as well as operational constraints like iteration limits.
 * @property llm The context for interacting with the large language model used by the agent, enabling message history
 * retrieval and processing.
 * @property stateManager The state management component responsible for tracking and updating the agent's state during its execution.
 * @property storage A storage interface providing persistent storage capabilities for the agent's data.
 * @property strategyName The name of the agent's strategic approach or operational method, determining its behavior
 * during execution.
 */
@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST", "MissingKDocForPublicAPI")
public expect class AIAgentFunctionalContext internal constructor(
    delegate: AIAgentFunctionalContextImpl
) : AIAgentFunctionalContextAPI {
    public constructor(
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
    )

    @PublishedApi
    internal val delegate: AIAgentFunctionalContextImpl

    override val environment: AIAgentEnvironment

    override val agentId: String

    override val pipeline: AIAgentFunctionalPipeline

    override var executionInfo: AgentExecutionInfo

    override val runId: String

    override val agentInput: Any?

    override val config: AIAgentConfig

    override val llm: AIAgentLLMContext

    override val stateManager: AIAgentStateManager

    override val storage: AIAgentStorage

    override val strategyName: String

    override val parentContext: AIAgentContext?
    override fun store(key: AIAgentStorageKey<*>, value: Any)

    override fun <T> get(key: AIAgentStorageKey<*>): T?

    override fun remove(key: AIAgentStorageKey<*>): Boolean

    override suspend fun getHistory(): List<Message>

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
     * @return A shiny new [AIAgentFunctionalContext] with your desired modifications applied!
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
    ): AIAgentFunctionalContext

    public override suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean
    ): Message.Response

    public override fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    )

    public override fun Message.Response.asAssistantMessageOrNull(): Message.Assistant?

    public override fun Message.Response.asAssistantMessage(): Message.Assistant

    public override fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    )

    public override fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call>

    public override fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    )

    public override suspend fun latestTokenUsage(): Int

    public suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>>

    public override suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?
    ): Flow<StreamFrame>

    public override suspend fun requestLLMMultiple(message: String): List<Message.Response>

    public override suspend fun requestLLMOnlyCallingTools(message: String): Message.Response

    public override suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response

    public override suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response

    public override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult

    public override suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult>

    public override suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response

    public override suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response>

    public override suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean
    ): SafeTool.Result<TResult>

    public override suspend fun compressHistory(
        strategy: HistoryCompressionStrategy,
        preserveMemory: Boolean
    )

    public suspend inline fun <Input, reified Output> subtask(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null
    ): Output

    override suspend fun <Input> subtaskWithVerification(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        responseProcessor: ResponseProcessor?
    ): CriticResult<Input>

    override suspend fun <Input, Output : Any> subtask(
        taskDescription: String,
        input: Input,
        outputClass: KClass<Output>,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        responseProcessor: ResponseProcessor?
    ): Output

    override suspend fun <Input, OutputTransformed> subtask(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>?,
        finishTool: Tool<*, OutputTransformed>,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        responseProcessor: ResponseProcessor?
    ): OutputTransformed
}
