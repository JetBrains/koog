package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.*
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The result which subgraphs can return.
 */
public interface SubgraphResult : ToolArgs, ToolResult

/**
 * The result which subgraphs can return.
 */
public interface SerializableSubgraphResult<T : SerializableSubgraphResult<T>> : ToolArgs, ToolResult.JSONSerializable<T>

/**
 * Represents the result of a verified subgraph execution.
 *
 * This class is used to encapsulate whether the subgraph execution was successful and
 * provides a message describing the result, including potential issues or errors.
 *
 * @property correct Indicates whether the subgraph execution was successful.
 * @property message Describes the outcome of the execution. If the execution was unsuccessful,
 * this property provides information on what went wrong.
 */
@Serializable
public data class VerifiedSubgraphResult(
    val correct: Boolean,
    val message: String,
) : SubgraphResult {
    /**
     * Returns the string representation of this object in JSON format.
     *
     * @return JSON string representation of the object.
     */
    override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
}

/**
 * Represents the result of a subgraph operation encapsulated as a string.
 *
 * This class is used to serialize and pass string-based results of subgraph executions in the agent framework.
 * Implements the `SubgraphResult` interface, enabling compatibility with subgraphs and tooling mechanisms.
 *
 * @property result The string representation of the execution result produced by the subgraph.
 */
@Serializable
public data class StringSubgraphResult(public val result: String) : SubgraphResult {
    /**
     * Converts the current object to its JSON string representation using the default serializer.
     *
     * @return The JSON string representation of the object.
     */
    override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
}

/**
 * Represents an abstract result type for subgraph provisioning tools.
 *
 * `ProvideSubgraphResult` defines functionality for specific tools that work
 * on subgraphs and produce a `SubgraphResult` as both input and output.
 * It serves as a specialization of the generic `Tool` framework.
 *
 * @param FinalResult The type of the final result, which must extend `SubgraphResult`.
 */
public abstract class ProvideSubgraphResult<FinalResult : SubgraphResult> : Tool<FinalResult, FinalResult>()

/**
 * Represents a concrete implementation of [ProvideSubgraphResult] specialized to handle verified subgraph results.
 *
 * `ProvideVerifiedSubgraphResult` invokes and validates the execution of a task represented by a subgraph, and returns
 * a `VerifiedSubgraphResult` object containing the results of the verification. It provides metadata and parameter
 * definitions required for using the tool.
 *
 * This object is designed to ensure a subgraph task has been executed correctly and to capture any potential issues
 * with the execution. The verification result attributes include:
 * - Whether the execution was successful (`correct`)
 * - A descriptive message explaining the result or issues (`message`)
 *
 * The tool metadata is encapsulated through a `ToolDescriptor`, defining the tool name, purpose, and required
 * parameters ("correct" and "message").
 *
 * It serializes and deserializes `VerifiedSubgraphResult` instances for input and output using the appropriate `KSerializer`.
 *
 * Functions:
 * - `execute`: Processes and returns the received `VerifiedSubgraphResult` object as the output without modification.
 *
 * Properties:
 * - `argsSerializer`: The serializer used to process the `VerifiedSubgraphResult`.
 * - `descriptor`: The metadata describing this tool, including its name, functionality, and parameters.
 */
public object ProvideVerifiedSubgraphResult : ProvideSubgraphResult<VerifiedSubgraphResult>() {
    override val argsSerializer: KSerializer<VerifiedSubgraphResult> = VerifiedSubgraphResult.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "finish_task_execution",
        description = "Please call this tool after you are sure that the task is completed. Verify if the task was completed correctly and provide additional information if there are problems.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "correct",
                description = "Verification result. True if task is executed correctly, false if incorrect",
                type = ToolParameterType.Boolean
            ),
            ToolParameterDescriptor(
                name = "message",
                description = "Summary of the task verification. Please provide a brief description of all the problems in this project if the task was failed",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: VerifiedSubgraphResult): VerifiedSubgraphResult {
        return args
    }
}

/**
 * Provides an implementation of `ProvideSubgraphResult` for handling `StringSubgraphResult`.
 *
 * This object is designed to facilitate the process of supplying subgraph results where the outcome
 * is encapsulated as a string. It is used to verify and finalize task execution while providing any necessary
 * additional information or error details related to the execution of the task.
 *
 * Key functionality includes:
 * - Serialization support for `StringSubgraphResult` using the `argsSerializer` property.
 * - Specification and description of subgraph tooling behavior via the `descriptor` property.
 * - Execution logic that returns the input `StringSubgraphResult` as output without transformation.
 *
 * Features:
 * - The `descriptor` describes the tool as "finish_task_execution" and requires a parameter named "result"
 *   representing the task result.
 * - The `execute` method processes the input argument and returns the result.
 */
public object ProvideStringSubgraphResult : ProvideSubgraphResult<StringSubgraphResult>() {
    override val argsSerializer: KSerializer<StringSubgraphResult> = StringSubgraphResult.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "finish_task_execution",
        description = "Please call this tool after you are sure that the task is completed. Verify if the task was completed correctly and provide additional information if there are problems.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "result",
                description = "Result of the given task",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: StringSubgraphResult): StringSubgraphResult {
        return args
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
@AIAgentBuilderDslMarker
public inline fun <reified Input, reified ProvidedResult : SubgraphResult> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, ProvidedResult> = subgraph(
    toolSelectionStrategy = toolSelectionStrategy,
    llmModel = llmModel,
    llmParams = llmParams,
) {
    val setupTask by node<Input, String> { input ->
        llm.writeSession {

            // Append finish tool to tools if it's not present yet
            if (finishTool.descriptor !in tools) {
                this.tools = tools + finishTool.descriptor
            }

            // Model must always call tools in the loop until it decides (via finish tool) that the exit condition is reached
            setToolChoiceRequired()
        }

        // Output task description
        defineTask(input)
    }

    val finalizeTask by node<ReceivedToolResult, ProvidedResult> { input ->
        llm.writeSession {
            // Append final tool call result to the prompt for further LLM calls to see it (otherwise they would fail)
            updatePrompt {
                tool {
                    result(input)
                }
            }

            // Remove finish tool from tools
            tools = tools - finishTool.descriptor
        }

        input.toSafeResult<ProvidedResult>().asSuccessful().result
    }

    // Helper node to overcome problems of the current api and repeat less code when writing routing conditions
    val nodeDecide by node<Message.Response, Message.Response> { it }

    val nodeCallLLM by nodeLLMRequest()
    val callTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()

    nodeStart then setupTask then nodeCallLLM then nodeDecide

    edge(nodeDecide forwardTo callTool onToolCall { true } )
    // throw to terminate the agent early with exception
    edge(
        nodeDecide forwardTo nodeFinish
            transformed {
                throw IllegalStateException(
                    "Subgraph with task must always call tools, but no tool call was generated, got instead: $it"
                )
            }
    )

    edge(callTool forwardTo finalizeTask onCondition { it.tool == finishTool.name })
    edge(callTool forwardTo sendToolResult)

    edge(sendToolResult forwardTo nodeDecide)

    edge(finalizeTask forwardTo nodeFinish)
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
public inline fun <reified Input, reified ProvidedResult : SubgraphResult> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, ProvidedResult> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    finishTool = finishTool,
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

/**
 * [subgraphWithTask] with [StringSubgraphResult] result.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, StringSubgraphResult> = subgraphWithTask(
    toolSelectionStrategy = toolSelectionStrategy,
    finishTool = ProvideStringSubgraphResult,
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

/**
 * Creates a subgraph with a predefined task definition using the provided tools, model, and parameters.
 *
 * This function allows you to define a subgraph where a specific task is executed as part of the
 * AI agent's strategy graph. The task is determined based on the provided task definition logic,
 * which is executed in the given context.
 *
 * @param tools A list of tools available for use within the subgraph.
 * @param llmModel An optional language model to be used within the subgraph. Defaults to `null`.
 * @param llmParams Optional parameters for the language model. Defaults to `null`.
 * @param defineTask A suspendable function that defines the task for the subgraph, given an input in the context.
 * @return A delegate representing the constructed subgraph with task execution capabilities.
 */
@Suppress("unused")
@AIAgentBuilderDslMarker
public inline fun <reified Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, StringSubgraphResult> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)

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
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithTask(
    finishTool = ProvideVerifiedSubgraphResult,
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
    noinline defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithVerification(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    llmModel = llmModel,
    llmParams = llmParams,
    defineTask = defineTask
)
