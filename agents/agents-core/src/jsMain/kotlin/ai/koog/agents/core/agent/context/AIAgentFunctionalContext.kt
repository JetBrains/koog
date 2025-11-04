@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse

@Suppress("MissingKDocForPublicAPI")
public actual class AIAgentFunctionalContext internal actual constructor(
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
    parentContext: AIAgentContext?,
    @PublishedApi internal actual val delegate: AIAgentFunctionalContextImpl
) : AIAgentContext, AIAgentFunctionalContextAPI by delegate {
    public actual suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>> = delegate.requestLLMStructuredImpl(message, examples, fixingParser)

    public actual suspend inline fun <Input, reified Output> subtask(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
    ): Output = delegate.subtaskImpl(
        taskDescription,
        input,
        tools,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
    )

    public actual fun copy(
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
        parentRootContext: AIAgentContext?
    ): AIAgentFunctionalContext = AIAgentFunctionalContext(
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
        parentContext = parentRootContext,
        delegate = delegate,
    )
}
