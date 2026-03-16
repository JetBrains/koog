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
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.CriticResultFromLLM
import ai.koog.agents.ext.agent.FinishTool
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.ext.agent.executeFinishTool
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Base [AIAgentContext] implementation providing functionality common across platforms
 */
@OptIn(InternalAgentsApi::class)
@Suppress("UNCHECKED_CAST")
public open class AIAgentFunctionalContextBaseCommon<Pipeline : AIAgentPipeline> internal constructor(
    override val environment: AIAgentEnvironment,
    override val agentId: String,
    override val runId: String,
    override val agentInput: Any?,
    override val config: AIAgentConfig,
    override val llm: AIAgentLLMContext,
    override val stateManager: AIAgentStateManager,
    override val storage: AIAgentStorage,
    override val strategyName: String,
    override val pipeline: Pipeline,
    override var executionInfo: AgentExecutionInfo,
    internal val storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf(),
    override val parentContext: AIAgentContext? = null
) : AIAgentContext {

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T? = storeMap[key] as T?

    override fun remove(key: AIAgentStorageKey<*>): Boolean = storeMap.remove(key) != null

    override suspend fun getHistory(): List<Message> {
        return llm.readSession { prompt.messages }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and optionally allows the use of tools during the LLM interaction.
     * The message becomes part of the current prompt, and the LLM's response is processed accordingly,
     * either with or without tool integrations based on the provided parameters.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param allowToolCalls Specifies whether tool calls are allowed during the LLM interaction. Defaults to `true`.
     */
    public suspend fun requestLLM(
        message: String,
        allowToolCalls: Boolean = true
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

    /**
     * Executes the provided action if the given response is of type [Message.Assistant].
     *
     * @param response The response message to evaluate, which may or may not be of type [Message.Assistant].
     * @param action A lambda function to execute if the response is an instance of [Message.Assistant].
     */
    public fun onAssistantMessage(
        response: Message.Response,
        action: (Message.Assistant) -> Unit
    ) {
        if (response is Message.Assistant) {
            action(response)
        }
    }

    /**
     * Attempts to cast a [Message.Response] instance to a [Message.Assistant] type.
     *
     * @return The [Message.Assistant] instance if the cast is successful, or `null` if the cast fails.
     */
    public fun Message.Response.asAssistantMessageOrNull(): Message.Assistant? = this as? Message.Assistant

    /**
     * Casts the current instance of a [Message.Response] to a [Message.Assistant].
     * This function should only be used when it is guaranteed that the instance
     * is of type [Message.Assistant], as it will throw an exception if the type
     * does not match.
     *
     * @return The current instance cast to [Message.Assistant].
     */
    public fun Message.Response.asAssistantMessage(): Message.Assistant = this as Message.Assistant

    /**
     * Invokes the provided action when multiple tool call messages are found within a given list of response messages.
     * Filters the list of responses to include only instances of [Message.Tool.Call] and executes the action on the
     * filtered list if it is not empty.
     *
     * @param response A list of response messages to be checked for tool call messages.
     * @param action A lambda function to be executed with the list of filtered tool call messages, if any exist.
     */
    public fun onMultipleToolCalls(
        response: List<Message.Response>,
        action: (List<Message.Tool.Call>) -> Unit
    ) {
        response.filterIsInstance<Message.Tool.Call>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    /**
     * Extracts a list of tool call messages from a given list of response messages.
     *
     * @param response A list of response messages to filter, potentially containing various types of responses.
     * @return A list of messages specifically representing tool calls, which are instances of [Message.Tool.Call].
     */
    public fun extractToolCalls(
        response: List<Message.Response>
    ): List<Message.Tool.Call> = response.filterIsInstance<Message.Tool.Call>()

    /**
     * Filters the provided list of response messages to include only assistant messages and,
     * if the filtered list is not empty, performs the specified action with the filtered list.
     *
     * @param response A list of response messages to be processed. Only those of type [Message.Assistant] will be considered.
     * @param action A lambda function to execute on the list of assistant messages if the filtered list is not empty.
     */
    public fun onMultipleAssistantMessages(
        response: List<Message.Response>,
        action: (List<Message.Assistant>) -> Unit
    ) {
        response.filterIsInstance<Message.Assistant>().takeIf { it.isNotEmpty() }?.let {
            action(it)
        }
    }

    /**
     * Retrieves the latest token usage from the prompt within the LLM session.
     *
     * @return The latest token usage information as an integer.
     */
    public suspend fun latestTokenUsage(): Int {
        return llm.readSession { prompt.latestTokenUsage }
    }

    /**
     * Sends a structured request to the Large Language Model (LLM) and processes the response.
     *
     * @param message The input message to be sent to the LLM.
     * @param examples An optional list of example objects used to guide the model's structured response generation.
     * @param fixingParser An optional parser to correct or validate the structured response.
     * @return A [Result] containing a [StructuredResponse] of the requested type.
     */
    public suspend inline fun <reified T> requestLLMStructured(
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

    /**
     * Sends a message to a Large Language Model (LLM) and streams the LLM response.
     * The message becomes part of the current prompt, and the LLM's response is streamed as it's generated.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param structureDefinition Optional structure to guide the LLM response.
     * @return A flow of [StreamFrame] objects from the LLM response.
     */
    public suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition? = null
    ): Flow<StreamFrame> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStreaming(structureDefinition)
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and gets multiple LLM responses with tool calls enabled.
     * The message becomes part of the current prompt, and multiple responses from the LLM are collected.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return A list of LLM responses.
     */
    public suspend fun requestLLMMultiple(message: String): List<Message.Response> {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMMultiple()
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) that will only call tools without generating text responses.
     * The message becomes part of the current prompt, and the LLM is instructed to only use tools.
     *
     * @param message The content of the message to be sent to the LLM.
     * @return The LLM response containing tool calls.
     */
    public suspend fun requestLLMOnlyCallingTools(message: String): Message.Response {
        return llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool descriptor that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    public suspend fun requestLLMForceOneTool(
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

    /**
     * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
     * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param tool The tool that the LLM must use.
     * @return The LLM response containing the tool call.
     */
    public suspend fun requestLLMForceOneTool(
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

    /**
     * Executes a tool call and returns the result.
     *
     * @param toolCall The tool call to execute.
     * @return The result of the tool execution.
     */
    public suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        return environment.executeTool(toolCall)
    }

    /**
     * Executes multiple tool calls and returns their results.
     * These calls can optionally be executed in parallel.
     *
     * @param toolCalls The list of tool calls to execute.
     * @param parallelTools Specifies whether tools should be executed in parallel.
     * @return A list of results from the executed tool calls.
     */
    public suspend fun executeMultipleTools(
        toolCalls: List<Message.Tool.Call>,
        parallelTools: Boolean = false
    ): List<ReceivedToolResult> {
        return if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }

    /**
     * Adds a tool result to the prompt and requests an LLM response.
     *
     * @param toolResult The tool result to add to the prompt.
     * @return The LLM response.
     */
    public suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Response {
        return llm.writeSession {
            updatePrompt {
                tool {
                    result(toolResult)
                }
            }

            requestLLM()
        }
    }

    /**
     * Adds multiple tool results to the prompt and gets multiple LLM responses.
     *
     * @param results The list of tool results to add to the prompt.
     * @return A list of LLM responses.
     */
    public suspend fun sendMultipleToolResults(
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

    /**
     * Calls a specific tool directly using the provided arguments.
     *
     * @param tool The tool to execute.
     * @param toolArgs The arguments to pass to the tool.
     * @param doUpdatePrompt Specifies whether to add tool call details to the prompt.
     * @return The result of the tool execution.
     */
    public suspend fun <ToolArg, TResult> executeSingleTool(
        tool: Tool<ToolArg, TResult>,
        toolArgs: ToolArg,
        doUpdatePrompt: Boolean = true
    ): SafeTool.Result<TResult> {
        return llm.writeSession {
            if (doUpdatePrompt) {
                appendPrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called with args: ${
                            tool.encodeArgs(toolArgs, config.serializer)
                        }"
                    )
                }
            }

            val toolResult = findTool(tool).execute(toolArgs, config.serializer)

            if (doUpdatePrompt) {
                appendPrompt {
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

    /**
     * Compresses the current LLM prompt (message history) into a summary, replacing messages with a TLDR.
     *
     * @param strategy Determines which messages to include in compression.
     * @param preserveMemory Specifies whether to retain message memory after compression.
     */
    public suspend fun compressHistory(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true
    ) {
        llm.writeSession {
            replaceHistoryWithTLDR(strategy, preserveMemory)
        }
    }

    /**
     * Executes a subtask with validation and verification of the results.
     * The method defines a subtask for the AI agent using the provided input
     * and additional parameters and ensures that the output is evaluated
     * based on its correctness and feedback.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param input The input data for the subtask, which will be used to create and execute the task.
     * @param tools An optional list of tools that can be used during the execution of the subtask.
     * @param llmModel An optional parameter specifying the LLM model to be used for the subtask.
     * @param llmParams Optional configuration parameters for the LLM, such as temperature and token limits.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax An optional parameter specifying the maximum number of retries for getting valid
     * assistant responses.
     * @return A [CriticResult] object containing the verification status, feedback, and the original input for the subtask.
     */
    public suspend fun subtaskWithVerification(
        taskDescription: String,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        responseProcessor: ResponseProcessor? = null
    ): CriticResult<String> {
        val finishTool = FinishTool<CriticResultFromLLM>(
            outputType = typeToken<CriticResultFromLLM>(),
            customSerializer = KotlinxSerializer()
        )

        val result = subtask(
            taskDescription = taskDescription,
            finishTool = finishTool,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            runMode = runMode,
            assistantResponseRepeatMax = assistantResponseRepeatMax,
            responseProcessor = responseProcessor
        )

        return CriticResult(
            successful = result.isCorrect,
            feedback = result.feedback,
            input = taskDescription
        )
    }

    /**
     * Executes a subtask within the larger context of an AI agent's functional operation.
     * This method allows defining a specific task to be performed with the given input, tools, and optional configuration.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param input The input data required for the subtask execution.
     * @param outputClass The output type expected from the subtask.
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat.
     * @return The result of the subtask execution.
     */
    public suspend fun <Output : Any> subtask(
        taskDescription: String,
        outputClass: KClass<Output>,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        responseProcessor: ResponseProcessor? = null
    ): Output {
        val finishTool = FinishTool<Output>(typeToken(outputClass))

        return subtask(
            taskDescription,
            tools,
            finishTool,
            llmModel,
            llmParams,
            runMode,
            assistantResponseRepeatMax,
            responseProcessor

        )
    }

    /**
     * Executes a subtask within the AI agent's functional context.
     * This method enables the use of tools to achieve a specific task based on the input provided.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param input The input data required to define and execute the subtask.
     * @param tools An optional list of tools that can be used to achieve the task, excluding the finishing tool.
     * @param finishTool A mandatory tool that determines the final result of the subtask by producing and transforming output.
     * @param llmModel An optional specific LLM to use for executing the subtask.
     * @param llmParams Optional parameters for configuring the behavior of the LLM during subtask execution.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of feedback attempts allowed from the language model if the
     * subtask is not completed.
     * @return The transformed final result of executing the finishing tool to complete the subtask.
     */
    public suspend fun <OutputTransformed> subtask(
        taskDescription: String,
        tools: List<Tool<*, *>>? = null,
        finishTool: Tool<*, OutputTransformed>,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        responseProcessor: ResponseProcessor? = null
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

    /**
     * Executes a subtask within the larger context of an AI agent's functional operation.
     * This method allows defining a specific task to be performed with the given input, tools, and optional configuration.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param input The input data required for the subtask execution.
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature.
     * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat.
     * @return The result of the subtask execution.
     */
    @OptIn(InternalAgentToolsApi::class)
    public suspend inline fun <reified Output : Any> subtask(
        taskDescription: String,
        tools: List<Tool<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax: Int? = null,
        responseProcessor: ResponseProcessor? = null
    ): Output {
        return subtask(
            taskDescription = taskDescription,
            outputClass = Output::class,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            runMode = runMode,
            assistantResponseRepeatMax = assistantResponseRepeatMax,
            responseProcessor = responseProcessor,
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

                    if (toolResult.tool == finishTool.descriptor.name) {
                        return toolResult.toSafeResult(finishTool, config.serializer).asSuccessful().result
                    }

                    response = sendToolResult(toolResult)
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

                    toolResults.firstOrNull { it.tool == finishTool.descriptor.name }
                        ?.let { finishResult ->
                            return finishResult.toSafeResult(finishTool, config.serializer).asSuccessful().result
                        }

                    responses = sendMultipleToolResults(toolResults)
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
