package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.containsToolCalls
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.asToolDescriptor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Utility object providing tools and methods for working with subgraphs and tasks in a controlled
 * and structured way. These utilities are designed to help finalize subgraph-related tasks and
 * encapsulate result handling within tool constructs.
 */
public object SubgraphWithTaskUtils {

    /**
     * Represents the name of the internal tool used for finalizing subgraph task results
     * within an AI agent's execution flow. This constant is primarily intended for internal
     * use in the implementation of tools and agents.
     *
     * Usage of this tool name is subject to the constraints and opt-in requirements
     * specified by the `InternalAgentToolsApi` annotation, indicating potential instability
     * and the possibility of breaking changes in future updates.
     *
     * Value: "finalize_task_result".
     */
    @InternalAgentToolsApi
    public const val FINALIZE_SUBGRAPH_TOOL_NAME: String = "finalize_task_result"

    /**
     * A constant string describing the purpose and usage of a tool within the agent framework.
     *
     * This constant is intended to represent the action of finalizing a subgraph process and providing
     * the final result. It is specifically used internally to indicate when a process is considered
     * complete and the output of that process should be returned.
     *
     * Marked with `@InternalAgentToolsApi`, this value is primarily designed for internal use within
     * the agent tools API and subject to change in future releases. Its usage in external
     * implementations should be approached with caution.
     */
    @InternalAgentToolsApi
    public const val FINALIZE_SUBGRAPH_TOOL_DESCRIPTION: String = "Call this tool when finish and provide final result"

    /**
     * Creates and returns a `Tool` instance with serializers and a descriptor for processing.
     *
     * @return A `Tool` instance where the input arguments and results share the same type `T`. The tool uses serializers and a descriptor based on the generic type `T`.
     */
    @OptIn(InternalAgentToolsApi::class)
    public inline fun <reified T> finishTool(): Tool<T, T> = object : Tool<T, T>() {

        /**
         * Provides a serializer for the argument type of the tool.
         *
         * This property is used to serialize the data to be passed as arguments for the tool's execution.
         * The generic type `T` represents the type of the arguments, and its serializer is resolved at runtime.
         *
         * It ensures that the arguments can be properly encoded and decoded, facilitating communication
         * between different components or systems handling the serialized data.
         */
        override val argsSerializer: KSerializer<T> = serializer()

        /**
         * Serializer used to encode and decode the results of the tool's execution.
         * This property defines how the result type `T` should be serialized, enabling the transfer
         * and persistence of the execution output in a structured and type-safe manner.
         *
         * It leverages Kotlin serialization features to automatically provide a mechanism
         * for converting the result into a serializable format and reconstructing it
         * during deserialization.
         */
        override val resultSerializer: KSerializer<T> = serializer()

        /**
         * The descriptor for the tool, derived from the serializer's [kotlinx.serialization.descriptors.SerialDescriptor],
         * and converted to a [ToolDescriptor] using the provided tool name.
         *
         * This property defines the metadata of the tool, such as its name and associated parameters,
         * and leverages the `asToolDescriptor` function for the conversion process.
         *
         * The tool name used for this descriptor is defined as `FINALIZE_SUBGRAPH_TOOL_NAME`.
         */
        override val descriptor: ToolDescriptor =
            serializer<T>().descriptor.asToolDescriptor(toolName = FINALIZE_SUBGRAPH_TOOL_NAME)

        override val name: String get() = descriptor.name
        override val description: String get() = descriptor.description

        /**
         * Executes the given argument and returns it as the result. This is a simple pass-through
         * implementation that processes input and directly returns it without modification.
         *
         * @param args The input argument of type [T] to be processed.
         * @return The same input argument [args] of type [T] as the result.
         */
        override suspend fun execute(args: T): T = args
    }

    /**
     * The maximum number of times an assistant is allowed to repeat responses within an interaction session,
     * up to a maximum of 3 times, by default.
     *
     * This constant serves as a limit to control the repetition of responses
     * provided by an assistant within interaction sessions. It can be used
     * to prevent redundancy in responses and ensure conciseness in communication.
     */
    public const val ASSISTANT_RESPONSE_REPEAT_MAX: Int = 3
}

/**
 * Creates an identity tool which performs no transformations, returning the input as the output.
 *
 * @return An instance of a Tool that processes input of type Output and returns the same input as output.
 */
@PublishedApi
@OptIn(InternalAgentToolsApi::class)
internal inline fun <reified Output> identityTool(): Tool<Output, Output> = object : Tool<Output, Output>() {
    override val argsSerializer: KSerializer<Output> = serializer()
    override val resultSerializer: KSerializer<Output> = serializer()
    override val name: String = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME
    override val description: String = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_DESCRIPTION
    override suspend fun execute(args: Output): Output = args
}

//region Subgraph With Task

/**
 * Creates a subgraph, which performs one specific task, defined by [defineTask],
 * using the tools defined by [toolSelectionStrategy].
 *
 * Use this function if you need the agent to perform a single task which outputs a structured result.
 *
 * @param Input The input type for the task to be defined in the subgraph.
 * @param Output The output type for the subgraph's finalized result.
 * @param toolSelectionStrategy The strategy used to select tools for the subgraph operations.
 * @param llmModel Optional language model to be used within the subgraph. Defaults to null.
 * @param llmParams Optional parameters for configuring the language model behavior. Defaults to null.
 * @param runMode The mode in which tools are executed. Defaults to sequential execution.
 * @param defineTask A suspending lambda function that defines the task for the subgraph, taking the input as a parameter.
 * @return A delegate that represents the created subgraph, allowing input and output operations.
 */
@OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraph(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
) {
    val finishTool = identityTool<Output>()

    setupSubgraphWithTask<Input, Output, Output>(
        finishTool = finishTool,
        runMode = runMode,
        assistantResponseRepeatMax = assistantResponseRepeatMax,
        defineTask = defineTask
    )
}

/**
 * Creates a subgraph with a task definition and specified tools. The subgraph uses the provided tools to process
 * input and execute the defined task, eventually producing a result through the provided finish tool.
 *
 * @param tools The list of tools that are available for use within the subgraph.
 * @param llmModel An optional language model to be used in the subgraph. If not specified, a default model may be used.
 * @param llmParams Optional parameters to customize the behavior of the language model in the subgraph.
 * @param runMode The mode in which tools are executed. Defaults to sequential execution.
 * @param defineTask A suspend function that defines the task to be executed by the subgraph based on the given input.
 * @return A delegate representing the subgraph that processes the input and produces a result through the finish tool.
 */
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
    runMode = runMode,
    assistantResponseRepeatMax = assistantResponseRepeatMax,
    defineTask = defineTask
)

/**
 * Defines a subgraph with a specific task to be performed by an AI agent.
 *
 * @param Input The input type provided to the subgraph.
 * @param Output The output type returned by the subgraph.
 * @param OutputTransformed The transformed output type after finishing the task.
 * @param toolSelectionStrategy The strategy to be used for selecting tools within the subgraph.
 * @param finishTool The tool responsible for finalizing the task and producing the transformed output.
 * @param llmModel The optional language model to be used in the subgraph for processing requests.
 * @param llmParams The optional parameters to customize the behavior of the language model.
 * @param runMode The mode in which tools are executed. Defaults to sequential execution.
 * @param assistantResponseRepeatMax The maximum number of assistant responses allowed before determining that the task cannot be completed.
 * @param defineTask A lambda function to define the task logic, which accepts the input and returns a task description.
 * @return A delegate object representing the constructed subgraph for the specified task.
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishTool: Tool<Output, OutputTransformed>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, OutputTransformed> = subgraph(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
) {
    setupSubgraphWithTask<Input, Output, OutputTransformed>(
        finishTool = finishTool,
        runMode = runMode,
        assistantResponseRepeatMax = assistantResponseRepeatMax,
        defineTask = defineTask
    )
}

/**
 * Creates a subgraph with a specified task definition, a list of tools, and a finish tool to transform output.
 *
 * @param Input The type of the input for the subgraph task.
 * @param Output The type of the raw output produced by the finish tool.
 * @param OutputTransformed The transformed type of the output after applying the finish tool.
 * @param tools A list of tools to be used within the subgraph.
 * @param finishTool The tool responsible for transforming the output of the subgraph.
 * @param llmModel The language model to be used within the subgraph. Defaults to null if not provided.
 * @param llmParams Optional parameters to customize the behavior of the language model. Defaults to null if not provided.
 * @param runMode The mode in which tools are executed. Defaults to sequential execution.
 * @param assistantResponseRepeatMax The maximum number of assistant responses allowed before determining that the task cannot be completed.
 * @param defineTask A suspend function that defines the task to be executed in the subgraph, based on the provided input.
 * @return A subgraph delegate that handles the input and produces the transformed output for the defined task.
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishTool: Tool<Output, OutputTransformed>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, OutputTransformed> = subgraph(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
) {
    setupSubgraphWithTask<Input, Output, OutputTransformed>(
        finishTool = finishTool,
        runMode = runMode,
        assistantResponseRepeatMax = assistantResponseRepeatMax,
        defineTask = defineTask
    )
}

//endregion Subgraph With Task

//region Subgraph With Verification

/**
 * [subgraphWithTask] with [CriticResult] result.
 * It verifies if the task was performed correctly or not, and describes the problems if any.
 */
@OptIn(InternalAgentsApi::class)
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input : Any> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, CriticResult<Input>> = subgraph {
    val inputKey = createStorageKey<Input>("subgraphWithVerification-input-key")

    val saveInput by node<Input, Input> { input ->
        storage.set(inputKey, input)

        input
    }

    val verifyTask by subgraphWithTask<Input, CriticResultFromLLM>(
        toolSelectionStrategy = toolSelectionStrategy,
        llmModel = llmModel,
        llmParams = llmParams,
        runMode = runMode,
        assistantResponseRepeatMax = assistantResponseRepeatMax,
        defineTask = defineTask
    )

    val provideResult by node<CriticResultFromLLM, CriticResult<Input>> { result ->
        CriticResult(
            successful = result.isCorrect,
            feedback = result.feedback,
            input = storage.get(inputKey)!!
        )
    }

    nodeStart then saveInput then verifyTask then provideResult then nodeFinish
}

/**
 * Constructs a subgraph within an AI agent's strategy graph with additional verification capabilities.
 *
 * This method defines a subgraph using a given list of tools, an optional language model,
 * and optional language model parameters. It also allows specifying whether to summarize
 * the interaction history and defines the task to be executed in the subgraph.
 *
 * @param Input The input type accepted by the subgraph.
 * @param tools A list of tools available to the subgraph.
 * @param llmModel Optional language model to be used within the subgraph.
 * @param llmParams Optional parameters to configure the language model's behavior.
 * @param defineTask A suspendable function defining the task that the subgraph will execute,
 *                   which takes an input and produces a string-based task description.
 * @return A delegate representing the constructed subgraph with input type `Input` and output type
 *         as a verified subgraph result `CriticResult`.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input : Any> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    tools: List<Tool<*, *>>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, CriticResult<Input>> = subgraphWithVerification(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
    runMode = runMode,
    assistantResponseRepeatMax = assistantResponseRepeatMax,
    defineTask = defineTask
)

//endregion Subgraph With Verification

/**
 * Configures a subgraph within the AI agent framework, associating it with required tasks and operations.
 *
 * FOR INTERNAL USAGE ONLY!
 *
 * @param finishTool A descriptor for the tool that determines the condition to finalize the subgraph's operation.
 * @param defineTask A suspending lambda that defines the main task of the subgraph, producing a task description based on the input.
 */
@Suppress("UNCHECKED_CAST")
@OptIn(InternalAgentToolsApi::class)
@Deprecated(
    message = "Use setupSubgraphWithTask API that receive a runMode parameter instead.",
    replaceWith = ReplaceWith(
        expression = "setupSubgraphWithTask(finishTool, assistantResponseRepeatMax, runMode, defineTask)"
    )
)
@InternalAgentsApi
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<Input, OutputTransformed>.setupSubgraphWithTask(
    finishTool: Tool<Output, OutputTransformed>,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(Input) -> String
) {
    return setupSubgraphWithTask(
        finishTool = finishTool,
        runMode = ToolCalls.SEQUENTIAL,
        assistantResponseRepeatMax = assistantResponseRepeatMax,
        defineTask = defineTask,
    )
}

/**
 * Configures and sets up a subgraph with task handling, including tool execution operations,
 * assistant response management, and task finalization logic.
 *
 * @param Input the type of input data for the subgraph.
 * @param Output the type of output data from the finish tool.
 * @param OutputTransformed the transformed type of the output data after processing by the finish tool.
 * @param finishTool the tool used to signify task completion and process task finalization.
 * @param runMode the mode in which tools are executed, e.g., parallel or sequential execution.
 * @param assistantResponseRepeatMax the maximum number of assistant responses allowed before
 *        determining that the task cannot be completed. If not provided, a default is used.
 * @param defineTask a suspend function defining the task description, executed within the
 *        context of an AI agent graph and based on the given input data.
 */
@InternalAgentsApi
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<Input, OutputTransformed>.setupSubgraphWithTask(
    finishTool: Tool<Output, OutputTransformed>,
    runMode: ToolCalls,
    assistantResponseRepeatMax: Int? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(Input) -> String
) {
    val originalToolsKey = createStorageKey<List<ToolDescriptor>>("all-available-tools")
    val askAssistantToFinishCounterKey = createStorageKey<Int>("ask-assistant-to-finish-counter")

    val maxAssistantResponses = assistantResponseRepeatMax ?: SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX

    val setupTask by node<Input, String> { input ->
        llm.writeSession {
            // Save tools to restore after the subgraph is finished
            storage.set(originalToolsKey, tools)

            // Append finish tool to tools if it's not present yet
            if (finishTool.descriptor !in tools) {
                this.tools += finishTool.descriptor
            }

            // Model must always call tools in the loop until it decides (via finish tool)
            // that the exit condition is reached
            setToolChoiceRequired()
        }

        // Output task description
        defineTask(input)
    }

    val finalizeTask by node<ReceivedToolResult, OutputTransformed> { toolResult ->
        llm.writeSession {
            // Restore original tools
            tools = storage.get(originalToolsKey)!!
        }

        toolResult.toSafeResult(finishTool).asSuccessful().result
    }

    // Helper node to overcome problems of the current api and repeat less code when writing routing conditions
    val nodeDecide by node<List<Message.Response>, List<Message.Response>> { it }

    val nodeCallLLM by nodeLLMRequestMultiple()

    val callToolsHacked by node<List<Message.Tool.Call>, List<ReceivedToolResult>> { toolCalls ->
        val (finishToolCalls, regularToolCalls) = toolCalls.partition { it.tool == finishTool.name }

        // Execute finish tool
        val finishToolResult = finishToolCalls.firstOrNull()?.let { toolCall ->
            executeFinishTool<Output, OutputTransformed>(toolCall, finishTool)
        }

        // Execute regular tools
        val regularToolsResults = when (runMode) {
            ToolCalls.PARALLEL -> {
                environment.executeTools(regularToolCalls)
            }
            ToolCalls.SEQUENTIAL,
            ToolCalls.SINGLE_RUN_SEQUENTIAL -> {
                regularToolCalls.map { toolCall ->
                    environment.executeTool(toolCall)
                }
            }
        }

        buildList {
            finishToolResult?.let { add(it) }
            addAll(regularToolsResults)
        }
    }

    val sendToolsResults by nodeLLMSendMultipleToolResults()

    @OptIn(DetachedPromptExecutorAPI::class)
    val handleAssistantMessage by node<Message.Assistant, List<Message.Response>> { response ->
        if (llm.model.capabilities.contains(LLMCapability.ToolChoice)) {
            error(
                "Subgraph with task must always call tools, but no ${Message.Tool.Call::class.simpleName} was generated, " +
                    "got instead: ${response::class.simpleName}"
            )
        }

        val currentAskAssistantToFinishCounter = storage.get(askAssistantToFinishCounterKey) ?: 1
        storage.set(askAssistantToFinishCounterKey, currentAskAssistantToFinishCounter + 1)

        if (currentAskAssistantToFinishCounter > maxAssistantResponses) {
            error(
                "Unable to finish subgraph with task. Reason: the model '${llm.model.id}' does not support tool choice, " +
                    "and was not able to call `${finishTool.name}` tool after " +
                    "<$maxAssistantResponses> attempts."
            )
        }

        llm.writeSession {
            // append a new message to the history with feedback:
            appendPrompt {
                user {
                    markdown {
                        h1("DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.")
                        h2("IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!")
                    }
                }
            }

            requestLLMMultiple()
        }
    }

    nodeStart then setupTask then nodeCallLLM then nodeDecide

    edge(
        nodeDecide forwardTo callToolsHacked
            onCondition { responses -> responses.containsToolCalls() }
            transformed { responses -> responses.filterIsInstance<Message.Tool.Call>() }
    )

    edge(
        nodeDecide forwardTo handleAssistantMessage
            onCondition { responses -> responses.filterIsInstance<Message.Assistant>().isNotEmpty() }
            transformed { responses -> responses.first() as Message.Assistant }
    )

    edge(handleAssistantMessage forwardTo nodeDecide)

    // throw to terminate the agent early with exception
    edge(
        nodeDecide forwardTo nodeFinish transformed {
            throw IllegalStateException(
                "Unhandled response from LLM. Subgraph with task must always call tools, " +
                    "but no ${Message.Tool.Call::class.simpleName} was generated, got instead: $it"
            )
        }
    )

    edge(
        callToolsHacked forwardTo finalizeTask
            onCondition { toolResults -> toolResults.firstOrNull()?.let { it.tool == finishTool.name } == true }
            transformed { toolsResults -> toolsResults.first() }
    )

    edge(callToolsHacked forwardTo sendToolsResults)

    edge(sendToolsResults forwardTo nodeDecide)

    edge(finalizeTask forwardTo nodeFinish)
}

@PublishedApi
@InternalAgentsApi
internal suspend inline fun <reified Output, reified OutputTransformed> AIAgentContext.executeFinishTool(
    toolCall: Message.Tool.Call,
    finishTool: Tool<Output, OutputTransformed>,
): ReceivedToolResult {
    // Execute Finish tool directly and get a result
    val args = finishTool.decodeArgs(toolCall.contentJson)
    val toolResult = finishTool.execute(args = args)

    val encodedResult = finishTool.encodeResult(toolResult)
    // Append a final tool call result to the prompt for further LLM calls
    // to see it (otherwise they would fail)
    llm.writeSession {
        appendPrompt {
            tool {
                result(toolCall.id, toolCall.tool, toolCall.content)
            }
        }
    }

    return ReceivedToolResult(
        id = toolCall.id,
        tool = finishTool.name,
        content = toolCall.content,
        result = encodedResult
    )
}
