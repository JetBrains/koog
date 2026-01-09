package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.CriticResultFromLLM
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.ext.agent.executeFinishTool
import ai.koog.agents.ext.agent.identityTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST")
@PublishedApi
internal class AIAgentFunctionalContextImpl(
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
    internal val storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf(),
    override val parentContext: AIAgentContext? = null
) : AIAgentFunctionalContextAPI {

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? = storeMap[key] as T?

    override fun remove(key: AIAgentStorageKey<*>): Boolean = storeMap.remove(key) != null

    override suspend fun getHistory(): List<Message> {
        return llm.readSession { prompt.messages }
    }

    override suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            if (allowToolCalls) {
                requestLLM()
            } else {
                requestLLMWithoutTools()
            }
        }
    }

    override fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    ) {
        if (response is Message.Assistant) {
            action(response)
        }
    }

    override fun Message.Response.asAssistantMessageOrNull(): Message.Assistant? = this as? Message.Assistant

    override fun Message.Response.asAssistantMessage(): Message.Assistant = this as Message.Assistant

    override fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    ) {
        response.filterIsInstance<Message.Tool.Call>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    override fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call> = response.filterIsInstance<Message.Tool.Call>()

    override fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    ) {
        response.filterIsInstance<Message.Assistant>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    override suspend fun latestTokenUsage(): Int {
        return llm.readSession { prompt.latestTokenUsage }
    }

    @PublishedApi
    internal suspend inline fun <reified T> requestLLMStructured(
        message: String,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(message, serializer<T>(), examples, fixingParser)

    @PublishedApi
    internal suspend fun <T> requestLLMStructured(
        message: String,
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStructured(
                serializer,
                examples,
                fixingParser
            )
        }
    }

    override suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition?
    ): Flow<StreamFrame> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStreaming(structureDefinition)
        }
    }

    override suspend fun requestLLMMultiple(message: String): List<Message.Response> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMMultiple()
        }
    }

    override suspend fun requestLLMOnlyCallingTools(message: String): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

    override suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

    override suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        return environment.executeTool(toolCall)
    }

    override suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean
    ): List<ReceivedToolResult> {
        return if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }

    override suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response {
        return llm.writeSession {
            updatePrompt {
                tool {
                    result(toolResult)
                }
            }

            requestLLM()
        }
    }

    override suspend fun sendMultipleToolResults(
        results: List<ReceivedToolResult>
    ): List<Message.Response> {
        return llm.writeSession {
            updatePrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultiple()
        }
    }

    public override suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean
    ): SafeTool.Result<TResult> {
        return llm.writeSession {
            if (doUpdatePrompt) {
                updatePrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called with args: ${
                            tool.encodeArgs(toolArgs)
                        }"
                    )
                }
            }

            val toolResult = findTool(tool).execute(toolArgs)

            if (doUpdatePrompt) {
                updatePrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called and returned result: ${
                            toolResult.content
                        }"
                    )
                }
            }
            toolResult
        }
    }

    override suspend fun compressHistory(
        strategy: HistoryCompressionStrategy,
        preserveMemory: Boolean
    ) {
        llm.writeSession {
            replaceHistoryWithTLDR(strategy, preserveMemory)
        }
    }

    override suspend fun <Input> subtaskWithVerification(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>?,
        llmModel: LLModel?,
        llmParams: LLMParams?,
        runMode: ToolCalls,
        assistantResponseRepeatMax: Int?,
        responseProcessor: ResponseProcessor?
    ): CriticResult<Input> {
        val result = subtask(
            taskDescription,
            input,
            CriticResultFromLLM::class,
            tools,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax,
            responseProcessor
        )

        return CriticResult(
            successful = result.isCorrect,
            feedback = result.feedback,
            input = input
        )
    }

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
    ): Output {
        val finishTool = identityTool(outputClass)

        return subtask(
            taskDescription,
            input,
            tools,
            finishTool,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax,
            responseProcessor

        )
    }

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
    ): OutputTransformed {
        val maxAssistantResponses = assistantResponseRepeatMax ?: SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX

        val toolsSubset = tools?.map { it.descriptor } ?: llm.readSession { this.tools.toList() }

        val originalTools = llm.readSession { this.tools.toList() }
        val originalModel = llm.readSession { this.model }
        val originalParams = llm.readSession { this.prompt.params }
        val originalResponseProcessor = llm.readSession { this.responseProcessor }

        // setup:
        llm.writeSession {
            if (finishTool.descriptor !in toolsSubset) {
                this.tools = toolsSubset + finishTool.descriptor
            }

            if (llmModel != null) {
                model = llmModel
            }

            if (llmParams != null) {
                prompt = prompt.withParams(llmParams)
            }

            if (responseProcessor != null) {
                this.responseProcessor = responseProcessor
            }

            setToolChoiceRequired()
        }

        val result = when (runMode) {
            ToolCalls.SINGLE_RUN_SEQUENTIAL -> subtaskWithSingleToolMode(
                taskDescription,
                finishTool,
                maxAssistantResponses
            )

            else -> subtaskWithMultiToolMode(
                taskDescription,
                finishTool,
                runMode,
                maxAssistantResponses
            )
        }

        // rollback
        llm.writeSession {
            this.tools = originalTools
            this.model = originalModel
            this.prompt = prompt.withParams(originalParams)
            this.responseProcessor = originalResponseProcessor
        }

        return result
    }

    @OptIn(InternalAgentToolsApi::class)
    @PublishedApi
    internal suspend inline fun <Input, reified Output : Any> subtaskImpl(
        taskDescription: String,
        input: Input,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null
    ): Output {
        return subtask(
            taskDescription = taskDescription,
            input = input,
            outputClass = Output::class,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            runMode = runMode,
            assistantResponseRepeatMax = assistantResponseRepeatMax,
        )
    }

    @PublishedApi
    @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    internal suspend fun <Output, OutputTransformed> subtaskWithSingleToolMode(
        task: String,
        finishTool: Tool<Output, OutputTransformed>,
        maxAssistantResponses: Int
    ): OutputTransformed {
        var feedbacksCount = 0
        var response = requestLLM(task)
        while (true) {
            when {
                response is Message.Tool.Call -> {
                    val toolResult = executeToolHacked(response, finishTool)
                    response = sendToolResult(toolResult)

                    if (toolResult.tool == finishTool.descriptor.name) {
                        return toolResult.toSafeResult(finishTool).asSuccessful().result
                    }
                }

                else -> {
                    if (feedbacksCount++ > maxAssistantResponses) {
                        error(
                            "Unable to finish subtask. Reason: the model '${llm.model.id}' does not support tool choice, " +
                                "and was not able to call `${finishTool.name}` tool after " +
                                "<$maxAssistantResponses> attempts."
                        )
                    }

                    response = requestLLM(
                        message = markdown {
                            h1("DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.")
                            h2("IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!")
                        }
                    )
                }
            }
        }
    }

    @OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
    @PublishedApi
    internal suspend fun <Output, OutputTransformed> executeMultipleToolsHacked(
        toolCalls: List<Message.Tool.Call>,
        finishTool: Tool<Output, OutputTransformed>,
        parallelTools: Boolean = false
    ): List<ReceivedToolResult> {
        val finishTools = toolCalls.filter { it.tool == finishTool.descriptor.name }
        val normalTools = toolCalls.filterNot { it.tool == finishTool.descriptor.name }

        val finishToolResults = finishTools.map { toolCall ->
            executeFinishTool(toolCall, finishTool)
        }

        val normalToolResults = if (parallelTools) {
            environment.executeTools(normalTools)
        } else {
            normalTools.map { environment.executeTool(it) }
        }

        return finishToolResults + normalToolResults
    }

    @OptIn(InternalAgentToolsApi::class)
    @PublishedApi
    internal suspend fun <Output, OutputTransformed> executeToolHacked(
        toolCall: Message.Tool.Call,
        finishTool: Tool<Output, OutputTransformed>
    ): ReceivedToolResult = executeMultipleToolsHacked(listOf(toolCall), finishTool).first()

    @PublishedApi
    @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    internal suspend fun <Output, OutputTransformed> subtaskWithMultiToolMode(
        task: String,
        finishTool: Tool<Output, OutputTransformed>,
        runMode: ToolCalls,
        maxAssistantResponses: Int
    ): OutputTransformed {
        var feedbacksCount = 0
        var responses = requestLLMMultiple(task)
        while (true) {
            when {
                responses.containsToolCalls() -> {
                    val toolCalls = extractToolCalls(responses)
                    val toolResults =
                        executeMultipleToolsHacked(toolCalls, finishTool, parallelTools = runMode == ToolCalls.PARALLEL)
                    responses = sendMultipleToolResults(toolResults)

                    toolResults.firstOrNull { it.tool == finishTool.descriptor.name }
                        ?.let { finishResult ->
                            return finishResult.toSafeResult(finishTool).asSuccessful().result
                        }
                }

                else -> {
                    if (feedbacksCount++ > maxAssistantResponses) {
                        error(
                            "Unable to finish subtask. Reason: the model '${llm.model.id}' does not support tool choice, " +
                                "and was not able to call `${finishTool.name}` tool after " +
                                "<$maxAssistantResponses> attempts."
                        )
                    }

                    responses = requestLLMMultiple(
                        message = markdown {
                            h1("DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.")
                            h2("IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!")
                        }
                    )
                }
            }
        }
    }
}
