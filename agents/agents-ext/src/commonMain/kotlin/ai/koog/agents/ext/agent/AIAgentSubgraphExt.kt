package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.asToolDescriptor
import ai.koog.agents.core.tools.asToolDescriptorDeserializer
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Represents the result of a verification process for a subgraph.
 *
 * @property correct Indicates whether the subgraph verification was successful.
 * @property message A message providing details about the verification outcome.
 */
@Serializable
public data class VerifiedSubgraphResult(
    val correct: Boolean,
    val message: String,
)

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
         * The descriptor for the tool, derived from the serializer's [SerialDescriptor],
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
}

/**
 * Creates a subgraph, which performs one specific task, defined by [defineTask],
 * using the tools defined by [toolSelectionStrategy].
 * When LLM believes that the task is finished, it will call [finishTool], generating [ProvidedResult] as its argument.
 * The generated [ProvidedResult] is the result of this subgraph.
 * The subgraph returns a wrapper [SafeTool.Result] to handle cases when the model didn't reach the finish condition
 * or didn't generate a final [ProvidedResult] due to an error (reported as [SafeTool.Result.Failure])
 *
 *
 * Use this function if you need the agent to perform a single task which outputs a structured result.
 *
 * @property toolSelectionStrategy Strategy to select tools available to the LLM during this task
 * @property finishTool The tool which LLM must call in order to complete the task.
 * The tool interface here is used as a descriptor of the structured result that LLM must produce.
 * The tool itself is never called.
 * @property llmModel LLM used for this task
 * @property llmParams Specific LLM parameters for this task
 * @property defineTask A block which defines the task. It may just return a system prompt for the task,
 * but may also alter agent context, prompt, storage, etc.
 */
@OptIn(InternalAgentToolsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraph(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
) {
    val finishToolDescriptor =
        serializer<Output>().descriptor.asToolDescriptor(toolName = FINALIZE_SUBGRAPH_TOOL_NAME)

    setupSubgraphWithTask<Input, Output, Output>(finishToolDescriptor, defineTask)
}

/**
 * Creates a subgraph with a task definition and specified tools. The subgraph uses the provided tools to process
 * input and execute the defined task, eventually producing a result through the provided finish tool.
 *
 * @param tools The list of tools that are available for use within the subgraph.
 * @param finishTool The tool responsible for producing the final result of the subgraph.
 * @param llmModel An optional language model to be used in the subgraph. If not specified, a default model may be used.
 * @param llmParams Optional parameters to customize the behavior of the language model in the subgraph.
 * @param defineTask A suspend function that defines the task to be executed by the subgraph based on the given input.
 * @return A delegate representing the subgraph that processes the input and produces a result through the finish tool.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, Output> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
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
 * @param defineTask A lambda function to define the task logic, which accepts the input and returns a task description.
 * @return A delegate object representing the constructed subgraph for the specified task.
 */
@OptIn(InternalAgentToolsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishTool: Tool<Output, OutputTransformed>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, OutputTransformed> = subgraph(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
) {
    val finishToolDescriptor = finishTool.descriptor
    setupSubgraphWithTask<Input, Output, OutputTransformed>(finishToolDescriptor, defineTask)
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
 * @param defineTask A suspend function that defines the task to be executed in the subgraph, based on the provided input.
 * @return A subgraph delegate that handles the input and produces the transformed output for the defined task.
 */
@OptIn(InternalAgentToolsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishTool: Tool<Output, OutputTransformed>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, OutputTransformed> = subgraph(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
) {
    val finishToolDescriptor = finishTool.descriptor
    setupSubgraphWithTask<Input, Output, OutputTransformed>(finishToolDescriptor, defineTask)
}

/**
 * [subgraphWithTask] with [VerifiedSubgraphResult] result.
 * It verifies if the task was performed correctly or not, and describes the problems if any.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithTask(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

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
 *         as a verified subgraph result `VerifiedSubgraphResult`.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    tools: List<Tool<*, *>>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithVerification(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

/**
 * Configures a subgraph within the AI agent framework, associating it with required tasks and operations.
 *
 * FOR INTERNAL USAGE ONLY!
 *
 * @param finishToolDescriptor A descriptor for the tool that determines the condition to finalize the subgraph's operation.
 * @param defineTask A suspending lambda that defines the main task of the subgraph, producing a task description based on the input.
 */
@InternalAgentToolsApi
@OptIn(InternalAgentToolsApi::class)
public inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentSubgraphBuilderBase<Input, OutputTransformed>.setupSubgraphWithTask(
    finishToolDescriptor: ToolDescriptor,
    noinline defineTask: suspend AIAgentGraphContextBase.(Input) -> String
) {
    val originalToolsKey = createStorageKey<List<ToolDescriptor>>("all-available-tools")

    val setupTask by node<Input, String> { input ->
        llm.writeSession {
            // Save tools to restore after subgraph is finished
            storage.set(originalToolsKey, tools)

            // Apped finish tool to tools if it's not present yet
            if (finishToolDescriptor !in tools) {
                this.tools = tools + finishToolDescriptor
            }

            // Model must always call tools in the loop until it decides (via finish tool) that the exit condition is reached
            setToolChoiceRequired()
        }

        // Output task description
        defineTask(input)
    }

    val finalizeTask by node<ReceivedToolResult, OutputTransformed> { input ->
        llm.writeSession {
            // Restore original tools
            tools = storage.get(originalToolsKey)!!
        }

        input.toSafeResult<OutputTransformed>().asSuccessful().result
    }

    // Helper node to overcome problems of the current api and repeat less code when writing routing conditions
    val nodeDecide by node<Message.Response, Message.Response> { it }

    val nodeCallLLM by nodeLLMRequest()

    /**
     * Works like a normal `nodeExecuteTool` but a bit hacked: if LLM decides to call the fake "finaize_result" tool,
     * it doesn't execute it.
     * */
    val callToolHacked by node<Message.Tool.Call, ReceivedToolResult> { toolCall ->
        if (toolCall.tool == FINALIZE_SUBGRAPH_TOOL_NAME) {
            val toolResult =
                Json.decodeFromString(serializer<Output>().asToolDescriptorDeserializer(), toolCall.content)

            // Append final tool call result to the prompt for further LLM calls to see it (otherwise they would fail)
            llm.writeSession {
                updatePrompt {
                    tool {
                        result(toolCall.id, toolCall.tool, toolCall.content)
                    }
                }
            }

            ReceivedToolResult(
                id = toolCall.id,
                tool = FINALIZE_SUBGRAPH_TOOL_NAME,
                content = toolCall.content,
                result = toolResult
            )
        } else {
            environment.executeTool(toolCall)
        }
    }
    val sendToolResult by nodeLLMSendToolResult()

    nodeStart then setupTask then nodeCallLLM then nodeDecide

    edge(nodeDecide forwardTo callToolHacked onToolCall { true })
    // throw to terminate the agent early with exception
    edge(
        nodeDecide forwardTo nodeFinish transformed {
            throw IllegalStateException(
                "Subgraph with task must always call tools, but no tool call was generated, got instead: $it"
            )
        }
    )

    edge(callToolHacked forwardTo finalizeTask onCondition { it.tool == finishToolDescriptor.name })
    edge(callToolHacked forwardTo sendToolResult)

    edge(sendToolResult forwardTo nodeDecide)

    edge(finalizeTask forwardTo nodeFinish)
}
