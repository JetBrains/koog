package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.OutputOption
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import java.util.concurrent.ExecutorService

/**
 * A builder class responsible for creating and managing subtasks within the context of an AI agent's functional operations.
 *
 * @constructor Initializes the SubtaskBuilder with a specific AI agent functional context and a description of the task.
 * @param context The functional context associated with the AI agent, providing the necessary environment for the subtask.
 * @param taskDescription A textual description of the task to be handled by the subtask.
 */
public class SubtaskBuilder(
    public val context: AIAgentFunctionalContextBase<*>,
    public val taskDescription: String
) {
    /**
     * Specifies the output type for a subtask to be built.
     *
     * @param outputClass The class representing the type of the output for the subtask.
     */
    public fun <Output : Any> withOutput(outputClass: Class<Output>): SubtaskBuilderWithOutput<Output> =
        SubtaskBuilderWithOutput(context = context, taskDescription = taskDescription, output = OutputOption.ByClass(outputClass))

    /**
     * Associates a finishing tool with the subtask builder, allowing the subtask to produce an output of the specified type.
     *
     * @param finishTool The tool that defines how the subtask's output will be produced and processed.
     */
    public fun <Output : Any> withFinishTool(finishTool: Tool<*, Output>): SubtaskBuilderWithOutput<Output> =
        SubtaskBuilderWithOutput(context = context, taskDescription = taskDescription, output = OutputOption.ByFinishTool(finishTool))

    /**
     * Configures the subtask builder to include a verification step in the task pipeline.
     */
    public fun withVerification(): SubtaskBuilderWithOutput<CriticResult<String>> =
        SubtaskBuilderWithOutput(context, taskDescription, OutputOption.Verification())
}

/**
 * Builder class to create and configure a subtask with specified input and output types.
 *
 * @param Output The type of the output produced by the subtask.
 * @param context The functional context associated with the AI agent.
 * @param taskDescription The description of the task that this subtask represents.
 * @param output Specifies the output of the subtask either by its class or through a finish tool.
 * @param tools Optional list of tools that can be used during the subtask execution.
 * @param llmModel Optional language model to be used for the subtask.
 * @param llmParams Optional parameters for the language model configuration.
 * @param responseProcessor Optional processor for post-processing LLM responses.
 * @param runMode Specifies the mode in which tools should be called (e.g., sequentially).
 * @param assistantResponseRepeatMax Optional maximum number of response repetitions allowed for the assistant.
 * @param executorService Optional executor service for managing asynchronous operations.
 */
public class SubtaskBuilderWithOutput<Output : Any>(
    public val context: AIAgentFunctionalContextBase<*>,
    public val taskDescription: String,
    public val output: OutputOption<Output>,
    public var tools: List<Tool<*, *>>? = null,
    public var llmModel: LLModel? = null,
    public var llmParams: LLMParams? = null,
    public var responseProcessor: ResponseProcessor? = null,
    public var runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    public var assistantResponseRepeatMax: Int? = null,
    public var executorService: ExecutorService? = null,
) {

    /**
     * Constructs a new instance of SubtaskBuilderWithInputAndOutput. This constructor allows specifying
     * the context, task description, input, and the output class type, which will be used to configure
     * the subtask builder for tasks requiring both input and output processing.
     *
     * @param context The functional context required to set up the subtask builder.
     * @param taskDescription A textual description of the task being built.
     * @param outputClass The class type of the output expected from the task execution.
     */
    public constructor(
        context: AIAgentFunctionalContextBase<*>,
        taskDescription: String,
        outputClass: Class<Output>
    ) : this(context, taskDescription, OutputOption.ByClass(outputClass))

    /**
     * Secondary constructor for initializing an instance of SubtaskBuilderWithInputAndOutput with
     * a specific context, task description, input, and a finalizing tool for output processing.
     *
     * @param context The functional context in which the subtask is executed.
     * @param taskDescription Description of the task being performed by the subtask.
     * @param finishTool A tool used to produce the final output for the subtask.
     */
    public constructor(
        context: AIAgentFunctionalContextBase<*>,
        taskDescription: String,
        finishTool: Tool<*, Output>
    ) : this(context, taskDescription, OutputOption.ByFinishTool(finishTool))

    /**
     * Sets the tools to be used for the subtask configuration.
     *
     * @param tools A list of tools, each represented as an instance of `Tool<*, *>`,
     *              to be utilized for the execution of the subtask.
     */
    public fun withTools(tools: List<Tool<*, *>>): SubtaskBuilderWithOutput<Output> =
        apply { this.tools = tools }

    /**
     * Adds the specified sets of tools to the subtask configuration.
     *
     * @param toolSets A variable number of instances of [ToolSet], each representing a group of tools
     *                 that can be used for the execution of the subtask. Each [ToolSet] will be
     *                 converted into a list of tools using its `asTools` method.
     */
    public fun withTools(vararg toolSets: ToolSet): SubtaskBuilderWithOutput<Output> =
        apply { this.tools = toolSets.flatMap { it.asTools() } }

    /**
     * Configures the builder to use the specified Large Language Model (LLM) for subsequent tasks.
     *
     * @param llmModel The Large Language Model (LLM) to be used, represented as an instance of [LLModel].
     */
    public fun useLLM(llmModel: LLModel): SubtaskBuilderWithOutput<Output> =
        apply { this.llmModel = llmModel }

    /**
     * Sets the parameters for the language model (LLM) to be used in the subtask.
     *
     * @param llmParams The parameters to configure the behavior of the language model.
     */
    public fun withParams(llmParams: LLMParams): SubtaskBuilderWithOutput<Output> =
        apply { this.llmParams = llmParams }

    /**
     * Sets the response processor to be used for post-processing LLM responses during task execution.
     *
     * @param responseProcessor The instance of [ResponseProcessor] to handle and modify LLM responses during task execution.
     */
    public fun withResponseProcessor(responseProcessor: ResponseProcessor): SubtaskBuilderWithOutput<Output> =
        apply { this.responseProcessor = responseProcessor }

    /**
     * Sets the execution mode for the AI agent's task execution.
     *
     * @param runMode Specifies the mode in which tool calls are executed. The available modes are:
     * - `SEQUENTIAL`: Executes multiple tool calls sequentially.
     * - `PARALLEL`: Executes tool calls in parallel.
     * - `SINGLE_RUN_SEQUENTIAL`: Allows only a single tool call to be executed.
     */
    public fun runMode(runMode: ToolCalls): SubtaskBuilderWithOutput<Output> =
        apply { this.runMode = runMode }

    /**
     * Sets the maximum number of times the assistant's response can be repeated.
     *
     * @param max The maximum number of repetitions allowed for the assistant's response.
     *            Must be a non-negative integer.
     */
    public fun assistantResponseRepeatMax(max: Int): SubtaskBuilderWithOutput<Output> =
        apply { this.assistantResponseRepeatMax = max }

    /**
     * Configures the subtask builder to use the specified ExecutorService for task execution.
     *
     * @param service the ExecutorService to be used for managing task execution.
     */
    public fun withExecutorService(service: ExecutorService): SubtaskBuilderWithOutput<Output> =
        apply { this.executorService = service }

    /**
     * Executes the defined task within the configured context and returns the output.
     * The method handles different output options (`OutputOption.ByClass` and `OutputOption.ByFinishTool`)
     * and executes subtasks using the provided input, tools, and configuration parameters.
     */
    @OptIn(InternalAgentsApi::class)
    public fun run(): Output = context.config.runOnStrategyDispatcher(executorService) {
        @Suppress("UNCHECKED_CAST")
        when (output) {
            is OutputOption.ByClass<Output> -> {
                context.subtask(
                    taskDescription,
                    outputClass = output.outputClass.kotlin,
                    tools = tools,
                    llmModel = llmModel,
                    llmParams = llmParams,
                    runMode = runMode,
                    assistantResponseRepeatMax = assistantResponseRepeatMax,
                    responseProcessor = responseProcessor
                )
            }

            is OutputOption.ByFinishTool<Output> -> context.subtask(
                taskDescription,
                finishTool = output.finishTool,
                tools = tools,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor
            )

            is OutputOption.Verification<*> -> context.subtaskWithVerification(
                taskDescription,
                tools = tools,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor
            ) as Output // Output === CriticResult<String> in this case
        }
    }
}
