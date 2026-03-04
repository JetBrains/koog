@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.ModeratedMessage
import ai.koog.agents.core.dsl.extension.appendPromptImpl
import ai.koog.agents.core.dsl.extension.llmCompressHistoryImpl
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleToolsAndSendResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMModerateMessage
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestForceOneTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultipleOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendMessageForceOneTool
import ai.koog.agents.core.dsl.extension.nodeLLMSendMessageOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResultsOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResultOnlyCallingTools
import ai.koog.agents.core.dsl.extension.requestStreamingAndSendResultsImpl
import ai.koog.agents.core.dsl.extension.requestStreamingImpl
import ai.koog.agents.core.dsl.extension.setStructuredOutputImpl
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.utils.runOnLLMDispatcher
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.agents.core.utils.submitToMainDispatcher
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.setupLLMAsAJudge
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.agents.ext.llm.choice.ChoiceSelectionStrategy
import ai.koog.agents.ext.llm.choice.nodeLLMSendResultsMultipleChoices
import ai.koog.agents.ext.llm.choice.nodeSelectLLMChoice
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.defaultType

/**
 * A builder class used for constructing strategies related to graph processing.
 * This serves as the entry point for configuring a graph strategy, allowing you
 * to define the input type for the graph.
 *
 * @param strategyName The name of the strategy being built.
 */
@JavaAPI
public class GraphStrategyBuilder(private val strategyName: String) {
    /**
     * Configures the builder to use the specified input type for the graph strategy.
     *
     * @param clazz The Java class representing the input type.
     * @return A new instance of GraphStrategyBuilderWithInput configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): GraphStrategyBuilderWithInput<Input> =
        GraphStrategyBuilderWithInput(
            strategyName,
            clazz.kotlin
        )
}

/**
 * A builder class for constructing graph strategies that start with a specific input type.
 *
 * This class is used to define the input type of a graph and allows chaining to specify the output type,
 * enabling the creation of a strongly-typed graph strategy.
 *
 * @param strategyName The name of the strategy being built.
 * @param Input The type of the input that the graph will utilize.
 * @property inputClass The KClass representation of the input type.
 */
@JavaAPI
public class GraphStrategyBuilderWithInput<Input : Any>(
    private val strategyName: String,
    private val inputClass: KClass<Input>
) {
    /**
     * Specifies the output type for the graph strategy and returns a builder configured with the input and output types.
     *
     * @param clazz The Java class object representing the desired output type.
     * @return A `TypedGraphStrategyBuilder` instance configured with the current input type and the specified output type.
     */
    public fun <Output : Any> withOutput(clazz: Class<Output>): TypedGraphStrategyBuilder<Input, Output> =
        TypedGraphStrategyBuilder(
            strategyName,
            inputClass,
            clazz.kotlin
        )
}

/**
 * Builder class used for constructing and configuring an [AIAgentGraphStrategy].
 *
 * @param strategyName The name of the strategy being built.
 * @param Input The type of the input entity.
 * @param Output The type of the output entity.
 * @property inputClass The class type of the input entity.
 * @property outputClass The class type of the output entity.
 */
@JavaAPI
public class TypedGraphStrategyBuilder<Input : Any, Output : Any>(
    private val strategyName: String,
    private val inputClass: KClass<Input>,
    private val outputClass: KClass<Output>,
    private var toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    internal var builder: AIAgentGraphStrategyBuilder<Input, Output> = AIAgentGraphStrategyBuilder(
        strategyName,
        inputClass.defaultType,
        outputClass.defaultType,
        toolSelectionStrategy
    ),
    private val edgeBuilders: MutableList<AIAgentGraphStrategyBuilder<Input, Output>.() -> Unit> = mutableListOf()
) {

    internal var nodeCounter = 0

    /**
     * Configures the strategy for selecting tools to be used in the current graph strategy builder.
     *
     * @param strategy The tool selection strategy to apply. This specifies how tools are selected
     * or filtered for utilization in the resulting graph strategy. Examples include using all tools,
     * no tools, or a custom subset of tools.
     * @return A new instance of [TypedGraphStrategyBuilder] with the updated tool selection strategy applied.
     */
    public fun withToolSelectionStrategy(strategy: ToolSelectionStrategy): TypedGraphStrategyBuilder<Input, Output> =
        this.apply {
            this.toolSelectionStrategy = strategy
            this.builder = AIAgentGraphStrategyBuilder(
                strategyName,
                inputClass.defaultType,
                outputClass.defaultType,
                toolSelectionStrategy
            )
        }

    /**
     * Provides access to the starting node of the graph strategy being constructed.
     *
     * This property represents the entry point of the graph, defined by the underlying
     * [StartNode], which serves as the initial node in the strategy. It is primarily used to
     * begin data flow or transformations within the constructed AI agent graph.
     *
     * The node automatically passes its input data as-is to subsequent nodes, making it
     * suitable as a handoff point for initializing the graph's execution pipeline.
     *
     * This property is derived from the builder and is essential for defining
     * connections or transitions to other nodes in the graph strategy.
     */
    @JvmField
    public val nodeStart: StartNode<Input> = builder.nodeStart

    /**
     * Provides access to the "finish" node of the strategy graph being constructed.
     *
     * This property represents an instance of [FinishNode], marking the endpoint of a graph or subgraph
     * within the strategy setup. The finish node directly passes its input to its output without modification
     * and acts as a terminal node by disallowing any outgoing edges.
     *
     * The `nodeFinish` property is lazily retrieved from the builder and reflects the finalized configuration
     * of the graph strategy. It serves as a key structural component for defining the completion behavior
     * within the graph execution flow.
     *
     * @return The [FinishNode] that terminates the graph or subgraph.
     */
    @JvmField
    public val nodeFinish: FinishNode<Output> = builder.nodeFinish

    /**
     * Creates and returns a new [AgentSubgraphBuilder] for constructing a subgraph
     * with the specified name in the context of the current graph strategy builder.
     *
     * @param name The name of the subgraph to create, or null if unspecified.
     * @return A new instance of [AgentSubgraphBuilder] for further configuration of the subgraph.
     */
    @JvmOverloads
    public fun subgraph(name: String? = null): AgentSubgraphBuilder<*> = AgentSubgraphBuilder(name)

    /**
     * Adds a directed edge to the strategy graph by configuring intermediate transformations
     * or filters for data flow between nodes using the specified edge builder.
     *
     * @param edgeIntermediate An intermediate edge builder that defines the source and destination nodes
     * along with transformation logic, filtering conditions, and data flow constraints for the edge.
     * @return The updated instance of [TypedGraphStrategyBuilder] that includes the configured edge.
     */
    public fun <IncomingOutput, OutgoingInput, CompatibleOutput : OutgoingInput> edge(
        edgeIntermediate: AIAgentEdgeBuilderIntermediate<IncomingOutput, CompatibleOutput, OutgoingInput>
    ): TypedGraphStrategyBuilder<Input, Output> = this.apply {
        edgeBuilders += {
            this.edge(edgeIntermediate)
        }
    }

    /**
     * Builds and returns an instance of [AIAgentGraphStrategy] configured with the
     * specified parameters, input/output types, and edge builders.
     *
     * @return The constructed [AIAgentGraphStrategy] instance.
     */
    public fun build(): AIAgentGraphStrategy<Input, Output> {
        edgeBuilders.forEach { builder.it() }

        return builder.build()
    }
}

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
)

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
     * Defines and builds a subgraph for an AI agent using the provided graph-building logic.
     *
     * @param buildSubgraph The logic to build the subgraph, represented as a `GraphBuilderAction`.
     * It provides the graph instance to be configured by the caller.
     * @return An instance of `AIAgentSubgraph` representing the configured subgraph.
     */
    public fun define(buildSubgraph: GraphBuilderAction<Input, Output>): AIAgentSubgraph<Input, Output> {
        val graph = GraphStrategyBuilder(
            strategyName = name ?: "subgraph-${Random.nextInt()}",
        )
            .withInput(inputClass)
            .withOutput(outputClass)

        buildSubgraph.build(graph)

        return graph.build()
    }

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
                inputType = inputClass.kotlin.defaultType,
                outputType = outputOption.outputClass.kotlin.defaultType,
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
                inputType = inputClass.kotlin.defaultType,
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
                inputType = inputClass.kotlin.defaultType,
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
