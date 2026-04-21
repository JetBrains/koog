@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.OutputOption
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.utils.submitToMainDispatcher
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.serialization.TypeToken
import kotlin.random.Random

/**
 * A builder class for configuring and constructing subgraphs in an AI agent graph strategy.
 *
 * This class provides methods to configure the subgraph's properties such as tool selection strategy,
 * LLM (Language Model) parameters,*/
@JavaAPI
public open class AgentSubgraphBuilder<SubgraphBuilder : AgentSubgraphBuilder<SubgraphBuilder>>(
    protected val name: String?,
    protected var toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    protected var llmModel: LLModel? = null,
    protected var llmParams: LLMParams? = null,
    protected var responseProcessor: ResponseProcessor? = null,
) {
    private fun self(): SubgraphBuilder = this as SubgraphBuilder

    /**
     * Sets the tool selection strategy for the subgraph builder and returns the updated builder instance.
     *
     * The tool selection strategy determines how tools are selected for use in the subgraph. This method
     * allows specifying a custom strategy to override the default behavior.
     *
     * @param strategy The tool selection strategy to apply. It defines the subset of tools to be included
     *                 or excluded during subgraph execution.
     **/
    public fun withToolSelectionStrategy(strategy: ToolSelectionStrategy): SubgraphBuilder = self().apply {
        toolSelectionStrategy = strategy
    }

    /**
     * Configures the builder to use a specific list of tools for the AI agent's subgraph.
     *
     * @param tools A list of tools to be used, each represented by its descriptor.
     * @return The current instance of [AgentSubgraphBuilder] for chaining further configurations.
     */
    public fun limitedTools(tools: List<Tool<*, *>>): SubgraphBuilder = self().apply {
        toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor })
    }

    /**
     * Configures the builder with a selection of tools defined by the provided tool sets.
     * The tools will be extracted from each `ToolSet` and applied to the builder's
     * tool selection strategy.
     *
     * @param toolSets One or more `ToolSet` instances, each representing a collection of tools to be added
     *                 to the builder's tool selection strategy.
     * @return The current instance of*/
    public fun limitedTools(vararg toolSets: ToolSet): SubgraphBuilder = self().apply {
        toolSelectionStrategy = ToolSelectionStrategy.Tools(toolSets.flatMap { it.asTools().map { it.descriptor } })
    }

    /**
     * Sets the specified Large Language Model (LLM) for the agent subgraph builder.
     *
     * @param llmModel The LLM instance to be associated with the agent subgraph builder.
     * @return The current instance of [AgentSubgraphBuilder] with the specified LLM model applied.
     */
    public fun usingLLM(llmModel: LLModel): SubgraphBuilder = self().apply {
        this.llmModel = llmModel
    }

    /**
     * Sets the parameters for the Language Learning Model (LLM) in the current builder.
     *
     * @param llmParams The parameters to configure the LLM behavior.
     * @return The updated instance of the AIAgentSubgraphBuilder.
     */
    public fun withLLMParams(llmParams: LLMParams): SubgraphBuilder = self().apply {
        this.llmParams = llmParams
    }

    /**
     * Sets the specified response processor to handle and modify LLM responses.
     *
     * @param responseProcessor the response processor to handle responses during*/
    public fun withResponseProcessor(responseProcessor: ResponseProcessor): SubgraphBuilder = self().apply {
        this.responseProcessor = responseProcessor
    }

    /**
     * Configures the builder with the specified input type and returns a new instance of
     * AIAgentSubgraphBuilderWithInput, allowing further configuration for the specified input type.
     *
     * @param outputClass the class type of the input to be used in the subgraph*/
    public fun <Input : Any> withInput(outputClass: Class<Input>): AIAgentSubgraphBuilderWithInput<Input, *> =
        AIAgentSubgraphBuilderWithInput(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            outputClass
        )
}

/**
 * A builder class for constructing AI agent subgraphs with a specified input type.
 *
 * This class extends [AgentSubgraphBuilder] and provides additional functionality
 * to define an input type for the subgraph, enabling the creation of typed subgraphs
 * where the input to the graph is explicitly defined.
 *
 */
@JavaAPI
public open class AIAgentSubgraphBuilderWithInput<Input : Any, SubgraphBuilder : AgentSubgraphBuilder<SubgraphBuilder>>(
    name: String?,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    protected val inputClass: Class<Input>
) : AgentSubgraphBuilder<SubgraphBuilder>(
    name,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor,
) {
    /**
     * Specifies the output type for the subgraph and transitions to a builder capable of handling
     * the provided input and output types. This method returns a new instance of the builder, with
     * the output type defined as the given class.
     *
     * @param outputClass The output class type that the subgraph is expected to handle.
     * @return A builder instance with the specified input and output types configured.
     */
    public fun <Output : Any> withOutput(outputClass: Class<Output>): TypedAIAgentSubgraphBuilder<Input, Output> =
        TypedAIAgentSubgraphBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            outputClass
        )

    /**
     * Responsible for building a subgraph that can perform verification tasks within the AI agent graph.
     * Resulting subgraph woult take an instance of [Input] and produce an instance of [CriticResult]<[Input]>
     *
     * @param defineVerificationTask A contextual action defining the verification task.
     *        It processes the input of type [Input] and produces a [String] as output
     *        within the AI agent graph context.
     * @return A builder instance of [SubgraphWithTaskBuilder] configured to handle the
     *         input type [Input] and output type [CriticResult<Input>], incorporating the
     *         specified verification behavior.
     */
    public fun withVerification(defineVerificationTask: ContextualAction<Input, String>): SubgraphWithTaskBuilder<Input, CriticResult<Input>> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            OutputOption.Verification(),
            defineVerificationTask
        )

    /**
     * Responsible for building a subgraph that can perform verification tasks within the AI agent graph.
     * Resulting subgraph woult take an instance of [Input] and produce an instance of [CriticResult]<[Input]>
     *
     * @param defineVerificationTask An action defining the verification task.
     *        It processes the input of type [Input] and produces a [String] as output
     *        within the AI agent graph context.
     * @return A builder instance of [SubgraphWithTaskBuilder] configured to handle the
     *         input type [Input] and output type [CriticResult<Input>], incorporating the
     *         specified verification behavior.
     */
    public fun withVerification(defineVerificationTask: SimpleAction<Input, String>): SubgraphWithTaskBuilder<Input, CriticResult<Input>> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            OutputOption.Verification(),
            defineTask = { input, _ -> defineVerificationTask.execute(input) }
        )

    /**
     * Configures the subgraph with a specified finish tool to process the output.
     * This allows the subgraph to conclude by transforming the output using the provided tool.
     *
     * @param finishTool The tool responsible for transforming the output of type [Output]
     *        into a new type [OutputTransformed] before finalizing the subgraph processing.
     * @return A builder instance of [SubgraphWithFinishToolBuilder] configured to handle
     *         the input type [Input] and the transformed output type [OutputTransformed].
     */
    public fun <Output : Any, OutputTransformed : Any> withFinishTool(finishTool: Tool<Output, OutputTransformed>): SubgraphWithFinishToolBuilder<Input, Output, OutputTransformed> =
        SubgraphWithFinishToolBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            finishTool
        )
}

/**
 * Builder class for constructing a subgraph with a finish tool in a graph strategy.
 *
 * @param Input The type of the input entity.
 * @param Output The type of the output entity before transformation.
 * @param OutputTransformed The type of the output entity after transformation.
 * @property name The optional name of the subgraph being constructed.
 * @property toolSelectionStrategy The strategy for selecting tools to be used in the subgraph.
 * @property llmModel The optional machine learning model to be used within the subgraph.
 * @property llmParams The optional parameters for configuring the machine learning model.
 * @property responseProcessor The optional processor used to handle responses from tasks.
 * @property inputClass The class type of the input entity for the subgraph.
 * @property finishTool The tool that finalizes or transforms the output of the subgraph.
 */
public class SubgraphWithFinishToolBuilder<Input : Any, Output : Any, OutputTransformed : Any>(
    private val name: String?,
    private val toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    private val llmModel: LLModel? = null,
    private val llmParams: LLMParams? = null,
    private val responseProcessor: ResponseProcessor? = null,
    private val inputClass: Class<Input>,
    private val finishTool: Tool<Output, OutputTransformed>,
) {
    /**
     * Configures a task to be executed as part of the subgraph.
     *
     * @param defineTask The task defined as a contextual action that takes an input and a graph context,
     *                   and produces a string output.
     * @return A builder instance for configuring the subgraph with the defined task.
     */
    public fun withTask(defineTask: ContextualAction<Input, String>): SubgraphWithTaskBuilder<Input, OutputTransformed> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            OutputOption.ByFinishTool(finishTool),
            defineTask
        )

    /**
     * Defines a task within the subgraph using the provided task implementation.
     *
     * @param defineTask The task implementation represented by a `SimpleAction` that takes an input of type `Input`
     *                   and returns a `String` output after task execution.
     * @return A `SubgraphWithTaskBuilder` instance configured with the specified task.
     */
    public fun withTask(defineTask: SimpleAction<Input, String>): SubgraphWithTaskBuilder<Input, OutputTransformed> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            OutputOption.ByFinishTool(finishTool),
            defineTask = { input, _ -> defineTask.execute(input) }
        )
}

/**
 * A base class for constructing a typed AI agent subgraph builder with strongly defined input and output types.
 * This class is designed for creating subgraphs within an AI agent graph structure, enabling the configuration
 * of node interactions, tool usage, and the integration of language models (LLMs).
 *
 * @param Input The type of the input data handled by the sub*/
@JavaAPI
public abstract class TypedAIAgentSubgraphBuilderBase<Input : Any, Output : Any, SubgraphBuilder : TypedAIAgentSubgraphBuilderBase<Input, Output, SubgraphBuilder>>(
    name: String?,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    protected val inputClass: Class<Input>,
    protected val outputOption: OutputOption<Output>,
) : AgentSubgraphBuilder<SubgraphBuilder>(
    name,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor
) {
    protected val inputTypeToken: TypeToken = TypeToken.of(inputClass)
}

/**
 * Builder class for creating and configuring a typed AI agent subgraph.
 *
 * This class facilitates the construction of a subgraph within an AI agent graph strategy
 * by providing methods to define graph structures,*/
@JavaAPI
public class TypedAIAgentSubgraphBuilder<Input : Any, Output : Any>(
    name: String?,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    inputClass: Class<Input>,
    outputClass: Class<Output>,
) : TypedAIAgentSubgraphBuilderBase<Input, Output, TypedAIAgentSubgraphBuilder<Input, Output>>(
    name,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor,
    inputClass,
    OutputOption.ByClass(outputClass)
) {
    private val outputClass: Class<Output> = (outputOption as OutputOption.ByClass<Output>).outputClass

    /**
     * Defines an AI Agent subgraph by applying the provided graph building action.
     *
     * @param buildSubgraph The action that builds and configures the subgraph using
     *                      a `GraphBuilderAction` implementation. It allows customizing
     *                      the graph strategy for the specified input and output types.
     * @return An instance of `DefinedAIAgentSubgraphBuilder` configured with the specified
     *         subgraph logic and type information.
     */
    public fun define(buildSubgraph: GraphBuilderAction<Input, Output>): DefinedAIAgentSubgraphBuilder<Input, Output> =
        DefinedAIAgentSubgraphBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            outputClass,
            buildSubgraph
        )

    /**
     * Configures a task to be executed as part of the subgraph.
     *
     * @param defineTask The task defined as a contextual action that takes an input and a graph context,
     *                   and produces a string output.
     * @return A builder instance for configuring the subgraph with the defined task.
     */
    public fun withTask(defineTask: ContextualAction<Input, String>): SubgraphWithTaskBuilder<Input, Output> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            outputOption,
            defineTask
        )

    /**
     * Defines a task within the subgraph using the provided task implementation.
     *
     * @param defineTask The task implementation represented by a `SimpleAction` that takes an input of type `Input`
     *                   and returns a `String` output after task execution.
     * @return A `SubgraphWithTaskBuilder` instance configured with the specified task.
     */
    public fun withTask(defineTask: SimpleAction<Input, String>): SubgraphWithTaskBuilder<Input, Output> =
        SubgraphWithTaskBuilder(
            name,
            toolSelectionStrategy,
            llmModel,
            llmParams,
            responseProcessor,
            inputClass,
            outputOption,
            defineTask = { input, _ -> defineTask.execute(input) }
        )
}

/**
 * Builder class for creating a defined AI Agent subgraph with specific input and output types.
 *
 * This class allows customization of subgraph configurations, such as the selection strategy for tools,
 * language model parameters, and response processing. The subgraph's internal logic is defined
 * using the `buildSubgraph` action passed to the constructor.
 *
 * @param Input The type of the input data for the subgraph.
 * @param Output The type of the output data for the subgraph.
 * @constructor Initializes the builder with a name, tool selection strategy, LLM configuration,
 * response processor, input/output types, and a subgraph build action.
 * @param name The name of the subgraph.
 * @param toolSelectionStrategy Strategy to determine which tools are included in this subgraph.
 * Defaults to using all tools.
 * @param llmModel The Large Language Model (LLM) to use for generating responses, or null if no model is provided.
 * @param llmParams Optional parameters for configuring the behavior of the LLM, such as temperature or context length.
 * @param responseProcessor A processor for handling and modifying the response after the subgraph's execution.
 * @param inputClass The class representing the input type for the subgraph.
 * @param outputClass The class representing the output type for the subgraph.
 * @param buildSubgraph The action that defines the logic for constructing the subgraph.
 */
@JavaAPI
public class DefinedAIAgentSubgraphBuilder<Input : Any, Output : Any>(
    name: String?,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    inputClass: Class<Input>,
    outputClass: Class<Output>,
    private val buildSubgraph: GraphBuilderAction<Input, Output>
) : TypedAIAgentSubgraphBuilderBase<Input, Output, TypedAIAgentSubgraphBuilder<Input, Output>>(
    name,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor,
    inputClass,
    OutputOption.ByClass(outputClass)
) {
    /**
     * Holds the `Class` object representing the output type expected from the operation.
     *
     * This property is initialized by extracting the `outputClass` field from the `OutputOption.ByClass`
     * instance associated with the builder. It ensures that the defined output type is carried through
     * the subgraph construction process, enabling type safety and proper resolution of the output type
     * in the AI agent graph.
     *
     * @property outputClass The `Class` instance representing the type of the output.
     */
    private val outputClass: Class<Output> = (outputOption as OutputOption.ByClass<Output>).outputClass

    /**
     * Builds and returns an instance of AIAgentSubgraphBase configured with the specified input and output types.
     *
     * The method initializes a graph strategy with a name, input class, and output class.
     * It leverages the provided `buildSubgraph` to define the logic for the subgraph creation.
     * The resulting graph is finalized and returned as an AIAgentSubgraphBase instance.
     *
     * @return A configured instance of AIAgentSubgraphBase with the specified input and output types.
     */
    public fun build(): AIAgentSubgraphBase<Input, Output> {
        val graph = GraphStrategyBuilder(
            strategyName = name ?: "subgraph-${Random.nextInt()}",
        )
            .withInput(inputClass)
            .withOutput(outputClass)

        buildSubgraph.build(graph)

        return graph.build()
    }
}

/**
 * A builder class for creating an AI agent subgraph that incorporates task definition
 * as part of its configuration. This builder allows customizing the construction of a
 * subgraph while defining how tasks are specified and executed within the subgraph.
 *
 * The class is designed for Java interoperability and simplifies the process of building
 * subgraphs with task-specific logic, including specifying input/output types, tool selection
 */
@JavaAPI
public class SubgraphWithTaskBuilder<Input : Any, Output : Any>(
    name: String?,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    inputClass: Class<Input>,
    outputOption: OutputOption<Output>,
    private val defineTask: ContextualAction<Input, String>,
    private var runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    private var assistantResponseRepeatMax: Int? = null,
) : TypedAIAgentSubgraphBuilderBase<Input, Output, SubgraphWithTaskBuilder<Input, Output>>(
    name,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor,
    inputClass,
    outputOption
) {
    /**
     * Configures the run mode for*/
    public fun runMode(runMode: ToolCalls): SubgraphWithTaskBuilder<Input, Output> = this.apply {
        this.runMode = runMode
    }

    /**
     * Sets the maximum number of times the assistant's response can be repeated.
     *
     * @param assistantResponseRepeatMax The maximum number of repeats allowed for the assistant's response.
     * @return The current instance of [SubgraphWithTaskBuilder] for method chaining.
     */
    public fun assistantResponseRepeatMax(assistantResponseRepeatMax: Int): SubgraphWithTaskBuilder<Input, Output> =
        this.apply {
            this.assistantResponseRepeatMax = assistantResponseRepeatMax
        }

    /**
     * Builds and returns an instance of `AIAgentSubgraph` configured with the specified parameters.
     *
     * The method creates a subgraph by*/
    public fun build(): AIAgentSubgraph<Input, Output> = when (outputOption) {
        is OutputOption.ByClass<Output> -> {
            val subgraph by subgraphWithTask<Input, Output>(
                name = name,
                inputType = inputTypeToken,
                outputType = outputOption.outputTypeToken,
                toolSelectionStrategy = toolSelectionStrategy,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                ctx.config.submitToMainDispatcher {
                    defineTask.execute(input, ctx)
                }
            }

            return subgraph
        }

        is OutputOption.ByFinishTool<Output> -> {
            val subgraph by subgraphWithTask<Input, Output>(
                name = name,
                inputType = inputTypeToken,
                toolSelectionStrategy = toolSelectionStrategy,
                finishTool = outputOption.finishTool,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                ctx.config.submitToMainDispatcher {
                    defineTask.execute(input, ctx)
                }
            }

            return subgraph
        }

        is OutputOption.Verification<*> -> {
            val subgraph by subgraphWithVerification<Input>(
                name = name,
                inputType = inputTypeToken,
                toolSelectionStrategy = toolSelectionStrategy,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                ctx.config.submitToMainDispatcher {
                    defineTask.execute(input, ctx)
                }
            }

            return subgraph as AIAgentSubgraph<Input, Output> // Output == CriticResult<Input>
        }
    }
}

/**
 * Functional interface representing an action that builds a graph using a provided
 * [TypedGraphStrategyBuilder]. This action allows the customization and configuration
 * of a graph strategy based on specific requirements for input and output types.
 *
 * The interface is annotated with [JavaAPI], indicating it is designed for compatibility
 * with Java code.
 *
 * @param Input The type of the input entities for the graph strategy.
 */
@JavaAPI
public fun interface GraphBuilderAction<Input : Any, Output : Any> {
    /**
     * Builds and configures a graph*/
    public fun build(graph: TypedGraphStrategyBuilder<Input, Output>)
}

/**
 * Represents a functional interface that defines a contextual action for processing an input
 * and producing an output within a specific AI agent graph context.
 *
 * This functional interface is designed*/
@JavaAPI
public fun interface ContextualAction<Input, Output> {
    /**
     * Executes an action within the given context using the provided input and returns the corresponding output.
     *
     * @param input The input data required for executing the action.
     * @param ctx The context in which the action is performed, providing necessary resources, configurations, and state management.
     * @return The output produced as a result of executing the action.
     */
    public fun execute(input: Input, ctx: AIAgentGraphContextBase): Output
}

/**
 * Represents a functional interface designed for performing a simple action
 * that takes an input of type [Input] and produces an output of type [Output].
 *
 * This interface is specifically optimized for interoperability with Java.
 *
 * @param Input The type of the input parameter for the action.
 * @param Output The type of the output produced by the action.
 */
@JavaAPI
public fun interface SimpleAction<Input, Output> {
    /**
     * Executes the action with the provided input and produces an output.
     *
     * @param input the input value to process
     * @return the result of processing the input
     */
    public fun execute(input: Input): Output
}
