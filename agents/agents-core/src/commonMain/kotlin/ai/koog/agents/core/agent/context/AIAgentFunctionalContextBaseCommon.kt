package ai.koog.agents.core.agent.context

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
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.CriticResultFromLLM
import ai.koog.agents.ext.agent.FinishTool
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.ext.agent.executeFinishTool
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.jvm.JvmSynthetic
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

    @Deprecated("Use context.storage.set() instead", level = DeprecationLevel.WARNING)
    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        storeMap[key] = value
    }

    @Deprecated("Use context.storage.get() instead", level = DeprecationLevel.WARNING)
    override fun <T> get(key: AIAgentStorageKey<*>): T? = storeMap[key] as T?

    @Deprecated("Use context.storage.remove() instead", level = DeprecationLevel.WARNING)
    override fun remove(key: AIAgentStorageKey<*>): Boolean = storeMap.remove(key) != null

    /**
     * Appends messages to the current LLM prompt without making an LLM request.
     * Corresponds to [nodeAppendPrompt].
     *
     * @param body Lambda to modify the prompt using [PromptBuilder].
     */
    @JvmSynthetic
    public suspend fun appendPrompt(body: PromptBuilder.() -> Unit) {
        llm.writeSession { appendPrompt { body() } }
    }

    // ================
    // LLM Request string nodes
    // ================

    //region LLMRequest

    /**
     * Sends a message to a Large Language Model (LLM) and optionally allows the use of tools during the LLM interaction.
     * The message becomes part of the current prompt, and the LLM's response is processed accordingly,
     * either with or without tool integrations based on the provided parameters.
     * Corresponds to [nodeLLMRequestWithUserText].
     *
     * @param message The content of the message to be sent to the LLM.
     */
    @JvmSynthetic
    public suspend fun requestLLM(
        message: String,
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLM()
        }
    }

    /**
     * Sends a string message to the LLM without tool calls.
     * Corresponds to [nodeLLMRequestWithoutToolsWithUserText].
     *
     * @param message The content of the message to be sent to the LLM.
     */
    @JvmSynthetic
    public suspend fun requestLLMWithoutTools(message: String): Message.Assistant {
        return llm.writeSession {
            appendPrompt { user(message) }
            requestLLMWithoutTools()
        }
    }

    /**
     * Sends a string message to the LLM without tool calls.
     * Corresponds to [nodeLLMRequestOnlyCallingToolsWithUserText].
     *
     * @param message The content of the message to be sent to the LLM.
     */
    @JvmSynthetic
    public suspend fun requestLLMOnlyCallingTools(message: String): Message.Assistant {
        return llm.writeSession {
            appendPrompt { user(message) }
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
    @JvmSynthetic
    public suspend fun requestLLMForceOneTool(
        message: String,
        tool: ToolDescriptor
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
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
    @JvmSynthetic
    public suspend fun requestLLMForceOneTool(
        message: String,
        tool: Tool<*, *>
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMForceOneTool(tool.descriptor)
        }
    }

    // Region Streaming

    /**
     * Sends a message to a Large Language Model (LLM) and streams the LLM response.
     * The message becomes part of the current prompt, and the LLM's response is streamed as it's generated.
     *
     * @param message The content of the message to be sent to the LLM.
     * @param structureDefinition Optional structure to guide the LLM response.
     * @return A flow of [StreamFrame] objects from the LLM response.
     */
    @JvmSynthetic
    public suspend fun requestLLMStreaming(
        message: String,
        structureDefinition: StructureDefinition? = null
    ): Flow<StreamFrame> {
        return llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMStreaming(structureDefinition)
        }
    }

    // Region Structured

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
            appendPrompt {
                user(message)
            }

            requestLLMStructured(
                serializer,
                examples,
                fixingParser
            )
        }
    }

    // ================
    // LLM Request User nodes
    // ================

    //region LLMRequest

    /**
     * Sends a [Message.User] to the LLM and returns the response.
     * Corresponds to [nodeLLMRequest].
     */
    @JvmSynthetic
    public suspend fun requestLLM(message: Message.User): Message.Assistant {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLM()
        }
    }

    /**
     * Sends a [Message.User] to the LLM, restricting it to only calling tools.
     * Corresponds to [nodeLLMRequestOnlyCallingTools].
     */
    @JvmSynthetic
    public suspend fun requestLLMOnlyCallingTools(message: Message.User): Message.Assistant {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMOnlyCallingTools()
        }
    }

    /**
     * Sends a [Message.User] to the LLM without allowing tool calls.
     * Corresponds to [nodeLLMRequestWithoutTools].
     */
    @JvmSynthetic
    public suspend fun requestLLMWithoutTools(message: Message.User): Message.Assistant {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMWithoutTools()
        }
    }

    /**
     * Sends a [Message.User] to the LLM and forces it to use a specific tool.
     * Corresponds to [nodeLLMRequestForceOneTool].
     */
    @JvmSynthetic
    public suspend fun requestLLMForceOneTool(message: Message.User, tool: ToolDescriptor): Message.Assistant {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMForceOneTool(tool)
        }
    }

    /**
     * Sends a [Message.User] to the LLM and forces it to use a specific tool.
     * Corresponds to [nodeLLMRequestForceOneTool].
     */
    @JvmSynthetic
    public suspend fun requestLLMForceOneTool(message: Message.User, tool: Tool<*, *>): Message.Assistant {
        return requestLLMForceOneTool(message, tool.descriptor)
    }

    /**
     * Sends a string message to the LLM and returns multiple response choices.
     * Corresponds to [nodeLLMRequestMultipleChoicesWithUserText].
     */
    @JvmSynthetic
    public suspend fun requestLLMMultipleChoices(message: String): LLMChoice {
        return llm.writeSession {
            appendPrompt { user(message) }
            requestLLMMultipleChoices()
        }
    }

    /**
     * Sends a [Message.User] to the LLM and returns multiple response choices.
     * Corresponds to [nodeLLMRequestMultipleChoices].
     */
    @JvmSynthetic
    public suspend fun requestLLMMultipleChoices(message: Message.User): LLMChoice {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMMultipleChoices()
        }
    }

    // Region Streaming

    /**
     * Sends a [Message.User] to the LLM and streams the response.
     * Corresponds to [nodeLLMRequestStreaming].
     */
    @JvmSynthetic
    public suspend fun requestLLMStreaming(
        message: Message.User,
        structureDefinition: StructureDefinition? = null
    ): Flow<StreamFrame> {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMStreaming(structureDefinition)
        }
    }

    // Region Structured

    /**
     * Sends a [Message.User] to the LLM and returns a structured response.
     * Corresponds to [nodeLLMRequestStructured].
     */
    @JvmSynthetic
    public suspend inline fun <reified T> requestLLMStructured(
        message: Message.User,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(message, serializer<T>(), examples, fixingParser)

    @PublishedApi
    internal suspend fun <T> requestLLMStructured(
        message: Message.User,
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMStructured(serializer, examples, fixingParser)
        }
    }

    /**
     * Sends a string message to the LLM and returns a structured response using a [StructuredRequestConfig].
     * Corresponds to [nodeLLMRequestStructuredWithUserText].
     */
    @JvmSynthetic
    public suspend fun <T> requestLLMStructured(
        message: String,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return llm.writeSession {
            appendPrompt { user(message) }
            requestLLMStructured(config, fixingParser)
        }
    }

    /**
     * Sends a [Message.User] to the LLM and returns a structured response using a [StructuredRequestConfig].
     * Corresponds to [nodeLLMRequestStructured].
     */
    @JvmSynthetic
    public suspend fun <T> requestLLMStructured(
        message: Message.User,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return llm.writeSession {
            appendPrompt { message(message) }
            requestLLMStructured(config, fixingParser)
        }
    }

    // Region Moderate

    /**
     * Moderates a message using the LLM.
     * Corresponds to [nodeLLMModerateMessage].
     *
     * @param message The message to moderate.
     * @param moderatingModel Optional model to use for moderation instead of the current one.
     * @param includeCurrentPrompt Whether to include the current conversation prompt in the moderation context.
     */
    @OptIn(DetachedPromptExecutorAPI::class)
    @JvmSynthetic
    public suspend fun moderateMessage(
        message: Message,
        moderatingModel: LLModel? = null,
        includeCurrentPrompt: Boolean = false
    ): ModerationResult {
        val currentPrompt = llm.readSession { prompt }
        val currentModel = llm.readSession { model }
        val moderationPrompt = if (includeCurrentPrompt) {
            prompt(currentPrompt) { message(message) }
        } else {
            prompt("single-message-moderation") { message(message) }
        }
        return llm.promptExecutor.moderate(moderationPrompt, moderatingModel ?: currentModel)
    }

    // ================
    // Execute Tool nodes
    // ================

    /**
     * Executes a tool call and returns the result.
     *
     * @param toolCall The tool call to execute.
     * @return The result of the tool execution.
     */
    @JvmSynthetic
    public suspend fun executeTool(toolCall: MessagePart.Tool.Call): ReceivedToolResult {
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
    @JvmSynthetic
    public suspend fun executeTools(
        toolCalls: List<MessagePart.Tool.Call>,
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
    @JvmSynthetic
    public suspend fun sendToolResult(toolResult: ReceivedToolResult): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user {
                    toolResult(toolResult.toMessagePart())
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
    @JvmSynthetic
    public suspend fun sendToolResults(
        results: List<ReceivedToolResult>
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user {
                    results.forEach { toolResult(it.toMessagePart()) }
                }
            }

            requestLLM()
        }
    }

    /**
     * Executes all tool calls from a [Message.Assistant] and returns their results.
     * Corresponds to [nodeExecuteToolsAndGetReceivedResults].
     *
     * @param message The assistant message containing tool calls to execute.
     * @param parallelTools Whether to execute tools in parallel.
     */
    @JvmSynthetic
    public suspend fun executeTools(
        message: Message.Assistant,
        parallelTools: Boolean = false
    ): List<ReceivedToolResult> {
        val toolCalls = message.parts.filterIsInstance<MessagePart.Tool.Call>()
        return executeTools(toolCalls, parallelTools)
    }

    /**
     * Sends tool results to the LLM, restricting it to only calling tools.
     * Corresponds to [nodeSendToolReceivedResultsOnlyCallingTools].
     */
    @JvmSynthetic
    public suspend fun sendToolResultsOnlyCallingTools(
        results: List<ReceivedToolResult>
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user { results.forEach { toolResult(it.toMessagePart()) } }
            }
            requestLLMOnlyCallingTools()
        }
    }

    /**
     * Sends tool results to the LLM without allowing further tool calls.
     * Corresponds to [nodeSendToolReceivedResultsWithoutTools].
     */
    @JvmSynthetic
    public suspend fun sendToolResultsWithoutTools(
        results: List<ReceivedToolResult>
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user { results.forEach { toolResult(it.toMessagePart()) } }
            }
            requestLLMWithoutTools()
        }
    }

    /**
     * Sends tool results to the LLM and forces it to use a specific tool.
     * Corresponds to [nodeSendToolReceivedResultsForceOneTool].
     */
    @JvmSynthetic
    public suspend fun sendToolResultsForceOneTool(
        results: List<ReceivedToolResult>,
        tool: ToolDescriptor
    ): Message.Assistant {
        return llm.writeSession {
            appendPrompt {
                user { results.forEach { toolResult(it.toMessagePart()) } }
            }
            requestLLMForceOneTool(tool)
        }
    }

    /**
     * Sends tool results to the LLM and returns multiple response choices.
     * Corresponds to [nodeSendToolReceivedResultsMultipleChoices].
     */
    @JvmSynthetic
    public suspend fun sendToolResultsMultipleChoices(
        results: List<ReceivedToolResult>
    ): LLMChoice {
        return llm.writeSession {
            appendPrompt {
                user { results.forEach { toolResult(it.toMessagePart()) } }
            }
            requestLLMMultipleChoices()
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
    @JvmSynthetic
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
    @JvmSynthetic
    public suspend fun compressHistory(
        strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory: Boolean = true
    ) {
        llm.writeSession {
            replaceHistoryWithTLDR(strategy, preserveMemory)
        }
    }

    // ================
    // Conditions
    // ================

    /**
     * Creates a [Message.User] with the provided text.
     * @param text Text of the user message.
     */
    @JvmSynthetic
    public suspend fun asUserMessage(text: String): Message.User {
        return llm.writeSession {
            userMessage(text)
        }
    }

    /**
     * Creates a [Message.User] with the provided tool results.
     * @param results List of the tool results of the user message.
     */
    @JvmSynthetic
    public suspend fun asUserMessage(results: List<ReceivedToolResult>): Message.User {
        return llm.writeSession {
            userMessage(results.map { it.toMessagePart() })
        }
    }

    /**
     * Executes the provided action if the given response is of type [Message.Assistant].
     *
     * @param response The response message to evaluate, which may or may not be of type [Message.Assistant].
     * @param action A lambda function to execute if the response is an instance of [Message.Assistant].
     */
    public fun onTextMessage(
        response: Message.Assistant,
        action: (List<MessagePart.Text>) -> Unit
    ) {
        getTextParts(response).takeIf { it.isNotEmpty() }?.let { action(it) }
    }

    /**
     * Invokes the provided action when multiple tool call messages are found within a given list of response messages.
     * Filters the list of responses to include only instances of [MessagePart.Tool.Call] and executes the action on the
     * filtered list if it is not empty.
     *
     * @param response A list of response messages to be checked for tool call messages.
     * @param action A lambda function to be executed with the list of filtered tool call messages, if any exist.
     */
    public fun onToolCalls(
        response: Message.Assistant,
        action: (List<MessagePart.Tool.Call>) -> Unit
    ) {
        getToolCalls(response).takeIf { it.isNotEmpty() }?.let { action(it) }
    }

    /**
     * Extracts a list of tool call messages from a given list of response messages.
     *
     * @param response A list of response messages to filter, potentially containing various types of responses.
     * @return A list of messages specifically representing tool calls, which are instances of [MessagePart.Tool.Call].
     */
    public fun getToolCalls(
        response: Message.Assistant
    ): List<MessagePart.Tool.Call> = response.parts.filterIsInstance<MessagePart.Tool.Call>()

    /**
     * Extracts a list of tool call messages from a given list of response messages.
     *
     * @param response A list of response messages to filter, potentially containing various types of responses.
     * @return A list of messages specifically representing tool calls, which are instances of [MessagePart.Tool.Call].
     */
    public fun getTextParts(
        response: Message.Assistant
    ): List<MessagePart.Text> = response.parts.filterIsInstance<MessagePart.Text>()

    /**
     * Retrieves the latest token usage from the prompt within the LLM session.
     *
     * @return The latest token usage information as an integer.
     */
    public suspend fun latestTokenUsage(): Int {
        return llm.readSession { prompt.latestTokenUsage }
    }

    /**
     * Executes a subtask with validation and verification of the results.
     * The method defines a subtask for the AI agent using the provided input
     * and additional parameters and ensures that the output is evaluated
     * based on its correctness and feedback.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param tools An optional list of tools that can be used during the execution of the subtask.
     * @param llmModel An optional parameter specifying the LLM model to be used for the subtask.
     * @param llmParams Optional configuration parameters for the LLM, such as temperature and token limits.
     * @param parallelTools The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax An optional parameter specifying the maximum number of retries for getting valid
     * assistant responses.
     * @return A [CriticResult] object containing the verification status, feedback, and the original input for the subtask.
     */
    public suspend fun subtaskWithVerification(
        taskDescription: String,
        tools: List<ToolBase<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        parallelTools: Boolean = false,
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
            parallelTools = parallelTools,
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
     * @param outputClass The output type expected from the subtask.
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature.
     * @param parallelTools The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat.
     * @return The result of the subtask execution.
     */
    public suspend fun <Output : Any> subtask(
        taskDescription: String,
        outputClass: KClass<Output>,
        tools: List<ToolBase<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        parallelTools: Boolean = false,
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
            parallelTools,
            assistantResponseRepeatMax,
            responseProcessor

        )
    }

    /**
     * Executes a subtask within the AI agent's functional context.
     * This method enables the use of tools to achieve a specific task based on the input provided.
     *
     * @param taskDescription The subtask to be executed by AIAgent.
     * @param tools An optional list of tools that can be used to achieve the task, excluding the finishing tool.
     * @param finishTool A mandatory tool that determines the final result of the subtask by producing and transforming output.
     * @param llmModel An optional specific LLM to use for executing the subtask.
     * @param llmParams Optional parameters for configuring the behavior of the LLM during subtask execution.
     * @param parallelTools The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of feedback attempts allowed from the language model if the
     * subtask is not completed.
     * @return The transformed final result of executing the finishing tool to complete the subtask.
     */
    public suspend fun <OutputTransformed> subtask(
        taskDescription: String,
        tools: List<ToolBase<*, *>>? = null,
        finishTool: Tool<*, OutputTransformed>,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        parallelTools: Boolean = false,
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

        val result = subtaskWithMultiToolMode(
            taskDescription,
            finishTool,
            parallelTools,
            maxAssistantResponses
        )

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
     * @param tools A list of tools available for use within the subtask.
     * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
     * @param llmParams The configuration parameters for the large language model, such as temperature.
     * @param parallelTools The mode in which tools should be executed, either sequentially or in parallel.
     * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat.
     * @return The result of the subtask execution.
     */
    @OptIn(InternalAgentToolsApi::class)
    public suspend inline fun <reified Output : Any> subtask(
        taskDescription: String,
        tools: List<ToolBase<*, *>>? = null,
        llmModel: LLModel? = null,
        llmParams: LLMParams? = null,
        parallelTools: Boolean = false,
        assistantResponseRepeatMax: Int? = null,
        responseProcessor: ResponseProcessor? = null
    ): Output {
        return subtask(
            taskDescription = taskDescription,
            outputClass = Output::class,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            parallelTools = parallelTools,
            assistantResponseRepeatMax = assistantResponseRepeatMax,
            responseProcessor = responseProcessor,
        )
    }

    @OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
    @PublishedApi
    internal suspend fun <Output, OutputTransformed> executeMultipleToolsHacked(
        toolCalls: List<MessagePart.Tool.Call>,
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
        toolCall: MessagePart.Tool.Call,
        finishTool: Tool<Output, OutputTransformed>
    ): ReceivedToolResult = executeMultipleToolsHacked(listOf(toolCall), finishTool).first()

    @PublishedApi
    @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    internal suspend fun <Output, OutputTransformed> subtaskWithMultiToolMode(
        task: String,
        finishTool: Tool<Output, OutputTransformed>,
        parallelTools: Boolean,
        maxAssistantResponses: Int
    ): OutputTransformed {
        var feedbacksCount = 0
        var response = requestLLM(task)
        while (true) {
            when {
                response.parts.any { it is MessagePart.Tool.Call } -> {
                    val toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
                    val toolResults =
                        executeMultipleToolsHacked(toolCalls, finishTool, parallelTools = parallelTools)

                    toolResults.firstOrNull { it.tool == finishTool.descriptor.name && it.resultKind is ToolResultKind.Success }
                        ?.let { finishResult ->
                            // Prompt must contain all tool results
                            llm.writeSession {
                                appendPrompt {
                                    user {
                                        toolResults.forEach { toolResult(it.toMessagePart()) }
                                    }
                                }
                            }

                            return finishResult
                                .toSafeResult(finishTool, config.serializer)
                                .asSuccessful().result
                        }

                    response = sendToolResults(toolResults)
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
}
