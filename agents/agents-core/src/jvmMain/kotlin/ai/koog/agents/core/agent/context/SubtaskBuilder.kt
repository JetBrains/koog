package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.context.SubtaskBuilderWithInputAndOutput.OutputOption.Verification
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
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
    public val context: AIAgentFunctionalContext,
    public val taskDescription: String
) {
    /**
     * Associates an input with the subtask being built.
     *
     * @param input The input data to be used for creating or configuring the subtask.
     * @return A SubtaskBuilderWithInput instance that includes the provided input, enabling further configuration or execution of the subtask.
     */
    public fun <Input> withInput(input: Input): SubtaskBuilderWithInput<Input> =
        SubtaskBuilderWithInput(context, taskDescription, input)
}

/**
 * A builder class designed for constructing subtasks with input data in the context of an AI agent's functional framework.
 *
 * This class facilitates the creation of subtasks by providing the context, task description, and input data to be used.
 * It enables chaining methods to specify additional configurations, such as output types or finish tools.
 *
 * @param Input The type of the input data to be utilized by the subtask.
 * @property context The functional context associated with the AI agent, used for task execution.
 * @property taskDescription A textual description of the subtask being created.
 * @property input The input data provided to the subtask.
 */
public class SubtaskBuilderWithInput<Input>(
    public val context: AIAgentFunctionalContext,
    public val taskDescription: String,
    public val input: Input
) {

    /**
     * Specifies the output type for a subtask to be built.
     *
     * @param outputClass The class representing the type of the output for the subtask.
     * @return A new instance of SubtaskBuilderWithInputAndOutput configured with the specified input and output types.
     */
    public fun <Output : Any> withOutput(outputClass: Class<Output>): SubtaskBuilderWithInputAndOutput<Input, Output> =
        SubtaskBuilderWithInputAndOutput(context, taskDescription, input, outputClass)

    /**
     * Associates a finishing tool with the subtask builder, allowing the subtask to produce an output of the specified type.
     *
     * @param finishTool The tool that defines how the subtask's output will be produced and processed.
     * @return A subtask builder configured with an input and the specified output type.
     */
    public fun <Output : Any> withFinishTool(finishTool: Tool<*, Output>): SubtaskBuilderWithInputAndOutput<Input, Output> =
        SubtaskBuilderWithInputAndOutput(context, taskDescription, input, finishTool)

    /**
     * Configures the subtask builder to include a verification step in the task pipeline.
     *
     * @return A new instance of SubtaskBuilderWithInputAndOutput, which incorporates
     * the verification step to process the input and produce a CriticResult for the given input type.
     */
    public fun withVerification(): SubtaskBuilderWithInputAndOutput<Input, CriticResult<Input>> =
        SubtaskBuilderWithInputAndOutput(context, taskDescription, input, Verification())
}

/**
 * Builder class to create and configure a subtask with specified input and output types.
 *
 * @param Input The type of the input required for the subtask.
 * @param Output The type of the output produced by the subtask.
 * @param context The functional context associated with the AI agent.
 * @param taskDescription The description of the task that this subtask represents.
 * @param input The input data required for the subtask.
 * @param output Specifies the output of the subtask either by its class or through a finish tool.
 * @param tools Optional list of tools that can be used during the subtask execution.
 * @param llmModel Optional language model to be used for the subtask.
 * @param llmParams Optional parameters for the language model configuration.
 * @param responseProcessor Optional processor for post-processing LLM responses.
 * @param runMode Specifies the mode in which tools should be called (e.g., sequentially).
 * @param assistantResponseRepeatMax Optional maximum number of response repetitions allowed for the assistant.
 * @param executorService Optional executor service for managing asynchronous operations.
 */
public class SubtaskBuilderWithInputAndOutput<Input, Output : Any>(
    public val context: AIAgentFunctionalContext,
    public val taskDescription: String,
    public val input: Input,
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
     * Represents a configuration option for determining the output type in a subtask builder process.
     * This sealed interface allows specifying the output either by its class type or by using a tool
     * that generates the required output.
     */
    public sealed interface OutputOption<Output : Any> {
        /**
         * Represents an output option that specifies the desired output type
         * using a `Class` object.
         *
         * This class is a concrete implementation of the `OutputOption` interface
         * and is used to define the expected type of output for a given operation.
         *
         * @param Output The type of the output.
         * @property outputClass The `Class` object representing the desired output type.
         */
        public class ByClass<Output : Any>(public val outputClass: Class<Output>) : OutputOption<Output>

        /**
         * Represents an output option determined by a specific tool that provides the output.
         *
         * @param Output The type of output produced by the associated tool.
         * @property finishTool The tool responsible for producing the output of the specified type.
         */
        public class ByFinishTool<Output : Any>(public val finishTool: Tool<*, Output>) : OutputOption<Output>

        /**
         * Represents a verification process applied to an input and produces a result
         * containing feedback and a success status.
         *
         * This class is a specialization of the `OutputOption` interface designed to
         * encapsulate the process of critiquing or verifying an `Input` and providing
         * a `CriticResult` as output. It can be used in scenarios where input validation
         * or assessment is required as part of a larger workflow.
         *
         * @param Input The type of the input to be verified.
         */
        public class Verification<Input> : OutputOption<CriticResult<Input>>
    }

    /**
     * Constructs a new instance of SubtaskBuilderWithInputAndOutput. This constructor allows specifying
     * the context, task description, input, and the output class type, which will be used to configure
     * the subtask builder for tasks requiring both input and output processing.
     *
     * @param context The functional context required to set up the subtask builder.
     * @param taskDescription A textual description of the task being built.
     * @param input The input data required for the task execution.
     * @param outputClass The class type of the output expected from the task execution.
     */
    public constructor(
        context: AIAgentFunctionalContext,
        taskDescription: String,
        input: Input,
        outputClass: Class<Output>
    ) : this(context, taskDescription, input, OutputOption.ByClass(outputClass))

    /**
     * Secondary constructor for initializing an instance of SubtaskBuilderWithInputAndOutput with
     * a specific context, task description, input, and a finalizing tool for output processing.
     *
     * @param context The functional context in which the subtask is executed.
     * @param taskDescription Description of the task being performed by the subtask.
     * @param input The input required for the execution of the subtask.
     * @param finishTool A tool used to produce the final output for the subtask.
     */
    public constructor(
        context: AIAgentFunctionalContext,
        taskDescription: String,
        input: Input,
        finishTool: Tool<*, Output>
    ) : this(context, taskDescription, input, OutputOption.ByFinishTool(finishTool))

    /**
     * Sets the tools to be used for the subtask configuration.
     *
     * @param tools A list of tools, each represented as an instance of `Tool<*, *>`,
     *              to be utilized for the execution of the subtask.
     * @return An updated instance of `SubtaskBuilderWithInputAndOutput` reflecting the applied tools.
     */
    public fun withTools(tools: List<Tool<*, *>>): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.tools = tools }

    /**
     * Configures the builder to use the specified Large Language Model (LLM) for subsequent tasks.
     *
     * @param llmModel The Large Language Model (LLM) to be used, represented as an instance of [LLModel].
     * @return The updated instance of [SubtaskBuilderWithInputAndOutput] with the specified LLM configured.
     */
    public fun useLLM(llmModel: LLModel): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.llmModel = llmModel }

    /**
     * Sets the parameters for the language model (LLM) to be used in the subtask.
     *
     * @param llmParams The parameters to configure the behavior of the language model.
     * @return The current instance of [SubtaskBuilderWithInputAndOutput] with the updated LLM parameters.
     */
    public fun withParams(llmParams: LLMParams): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.llmParams = llmParams }

    /**
     * Sets the response processor to be used for post-processing LLM responses during task execution.
     *
     * @param responseProcessor The instance of [ResponseProcessor] to handle and modify LLM responses during task execution.
     * @return An updated instance of [SubtaskBuilderWithInputAndOutput] with the specified response processor applied.
     */
    public fun withResponseProcessor(responseProcessor: ResponseProcessor): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.responseProcessor = responseProcessor }

    /**
     * Sets the execution mode for the AI agent's task execution.
     *
     * @param runMode Specifies the mode in which tool calls are executed. The available modes are:
     * - `SEQUENTIAL`: Executes multiple tool calls sequentially.
     * - `PARALLEL`: Executes tool calls in parallel.
     * - `SINGLE_RUN_SEQUENTIAL`: Allows only a single tool call to be executed.
     * @return The current instance of `SubtaskBuilderWithInputAndOutput` to allow for method chaining when configuring the subtask builder.
     */
    public fun runMode(runMode: ToolCalls): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.runMode = runMode }

    /**
     * Sets the maximum number of times the assistant's response can be repeated.
     *
     * @param max The maximum number of repetitions allowed for the assistant's response.
     *            Must be a non-negative integer.
     * @return The current instance of [SubtaskBuilderWithInputAndOutput], allowing for method chaining.
     */
    public fun assistantResponseRepeatMax(max: Int): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.assistantResponseRepeatMax = max }

    /**
     * Configures the subtask builder to use the specified ExecutorService for task execution.
     *
     * @param service the ExecutorService to be used for managing task execution.
     * @return the current builder instance configured with the given ExecutorService.
     */
    public fun withExecutorService(service: ExecutorService): SubtaskBuilderWithInputAndOutput<Input, Output> =
        apply { this.executorService = service }

    /**
     * Executes the defined task within the configured context and returns the output.
     * The method handles different output options (`OutputOption.ByClass` and `OutputOption.ByFinishTool`)
     * and executes subtasks using the provided input, tools, and configuration parameters.
     *
     * @return The result of the subtask execution, which is of type `Output`.
     */
    @OptIn(InternalAgentsApi::class)
    public fun run(): Output = context.config.runOnStrategyDispatcher(executorService) {
        @Suppress("UNCHECKED_CAST")
        when (output) {
            is OutputOption.ByClass<Output> -> {
                context.subtask(
                    taskDescription,
                    input = input,
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
                input = input,
                finishTool = output.finishTool,
                tools = tools,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor
            )
            is Verification<*> -> context.subtaskWithVerification(
                taskDescription,
                input = input,
                tools = tools,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor
            ) as Output // Output === CriticResult<Input> in this case
        }
    }
}
