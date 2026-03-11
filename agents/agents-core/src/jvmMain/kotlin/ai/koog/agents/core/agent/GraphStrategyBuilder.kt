@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.SubtaskBuilderWithInputAndOutput.OutputOption
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
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
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.serialization.typeToken
import kotlinx.coroutines.jdk9.asFlow
import kotlinx.coroutines.jdk9.asPublisher
import java.util.concurrent.Flow.Publisher
import kotlin.reflect.KClass

/**
 * A builder class used for constructing strategies related to graph processing.
 * This serves as the entry point for configuring a graph strategy, allowing you
 * to define the input type for the graph.
 *
 * @param strategyName The name of the strategy being built.
 */
@JavaAPI
public class GraphStrategyBuilder(private val agentConfig: AIAgentConfig, private val strategyName: String) {
    /**
     * Configures the builder to use the specified input type for the graph strategy.
     *
     * @param clazz The Java class representing the input type.
     * @return A new instance of GraphStrategyBuilderWithInput configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): GraphStrategyBuilderWithInput<Input> =
        GraphStrategyBuilderWithInput(
            agentConfig,
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
    private val agentConfig: AIAgentConfig,
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
            agentConfig,
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
    internal val agentConfig: AIAgentConfig,
    private val strategyName: String,
    private val inputClass: KClass<Input>,
    private val outputClass: KClass<Output>,
    private var toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    internal var builder: AIAgentGraphStrategyBuilder<Input, Output> = AIAgentGraphStrategyBuilder(
        strategyName,
        typeToken(inputClass),
        typeToken(outputClass),
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
                typeToken(inputClass),
                typeToken(outputClass),
                toolSelectionStrategy
            )
        }

    /**
     * Creates and returns a new [AIAgentNodeBuilder] for constructing an AI agent node
     * with the specified name in the context of the current graph strategy builder.
     *
     * @param name The name of the AI agent node to create, or null if unspecified.
     * @return A new instance of [AIAgentNodeBuilder] for further configuration of the AI agent node.
     */
    @JvmOverloads
    public fun node(name: String? = null): AIAgentNodeBuilder = AIAgentNodeBuilder(name, this)

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
    public val nodeStart: StartNode<Input>
        @JvmName("nodeStart")
        get() = builder.nodeStart

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
    public val nodeFinish: FinishNode<Output>
        @JvmName("nodeFinish")
        get() = builder.nodeFinish

    /**
     * Creates and returns a new [AgentSubgraphBuilder] for constructing a subgraph
     * with the specified name in the context of the current graph strategy builder.
     *
     * @param name The name of the subgraph to create, or null if unspecified.
     * @return A new instance of [AgentSubgraphBuilder] for further configuration of the subgraph.
     */
    @JvmOverloads
    public fun subgraph(name: String? = null): AgentSubgraphBuilder<*> = AgentSubgraphBuilder(name, this)

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
    protected val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
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
            strategyBuilder,
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
    strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    protected val inputClass: Class<Input>
) : AgentSubgraphBuilder<SubgraphBuilder>(
    name,
    strategyBuilder,
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
            strategyBuilder,
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
            strategyBuilder,
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
            strategyBuilder,
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
            strategyBuilder,
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
 * @property strategyBuilder The graph strategy builder used to configure the subgraph.
 * @property toolSelectionStrategy The strategy for selecting tools to be used in the subgraph.
 * @property llmModel The optional machine learning model to be used within the subgraph.
 * @property llmParams The optional parameters for configuring the machine learning model.
 * @property responseProcessor The optional processor used to handle responses from tasks.
 * @property inputClass The class type of the input entity for the subgraph.
 * @property finishTool The tool that finalizes or transforms the output of the subgraph.
 */
public class SubgraphWithFinishToolBuilder<Input : Any, Output : Any, OutputTransformed : Any>(
    private val name: String?,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
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
            strategyBuilder,
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
            strategyBuilder,
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
    strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    protected val inputClass: Class<Input>,
    protected val outputOption: OutputOption<Output>,
) : AgentSubgraphBuilder<SubgraphBuilder>(
    name,
    strategyBuilder,
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
    strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    responseProcessor: ResponseProcessor? = null,
    inputClass: Class<Input>,
    outputClass: Class<Output>,
) : TypedAIAgentSubgraphBuilderBase<Input, Output, TypedAIAgentSubgraphBuilder<Input, Output>>(
    name,
    strategyBuilder,
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
            agentConfig = strategyBuilder.agentConfig,
            strategyName = name ?: "subgraph-${strategyBuilder.nodeCounter++}"
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
            strategyBuilder,
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
            strategyBuilder,
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
    strategyBuilder: TypedGraphStrategyBuilder<*, *>,
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
    strategyBuilder,
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
            val subgraph by strategyBuilder.builder.subgraphWithTask<Input, Output>(
                name = name,
                inputType = typeToken(inputClass),
                outputType = typeToken(outputOption.outputClass),
                toolSelectionStrategy = toolSelectionStrategy,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                strategyBuilder.agentConfig.submitToMainDispatcher {
                    defineTask.execute(input, ctx)
                }
            }

            return subgraph
        }

        is OutputOption.ByFinishTool<Output> -> {
            val subgraph by strategyBuilder.builder.subgraphWithTask<Input, Output>(
                name = name,
                inputType = typeToken(inputClass),
                toolSelectionStrategy = toolSelectionStrategy,
                finishTool = outputOption.finishTool,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                strategyBuilder.agentConfig.submitToMainDispatcher {
                    defineTask.execute(input, ctx)
                }
            }

            return subgraph
        }

        is OutputOption.Verification<*> -> {
            val subgraph by strategyBuilder.builder.subgraphWithVerification<Input>(
                name = name,
                inputType = typeToken(inputClass),
                toolSelectionStrategy = toolSelectionStrategy,
                llmModel = llmModel,
                llmParams = llmParams,
                runMode = runMode,
                assistantResponseRepeatMax = assistantResponseRepeatMax,
                responseProcessor = responseProcessor,
            ) { input ->
                val ctx = this
                strategyBuilder.agentConfig.submitToMainDispatcher {
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

/**
 * A Java builder class for creating [AIAgentNode] with a specified name.
 * This allows the configuration of the node's input type.
 *
 * @constructor Initializes the builder with the specified name.
 * @param name The name of the [AIAgentNode], or null if unspecified.
 */
@JavaAPI
public class AIAgentNodeBuilder(
    private val name: String?,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>
) {
    /**
     * Specifies the input type for building an [AIAgentNode].
     *
     * @param clazz the `Class` instance representing the input type.
     * @return an instance of `AIAgentNodeBuilderWithInput` configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): AIAgentNodeBuilderWithInput<Input> =
        AIAgentNodeBuilderWithInput(name, strategyBuilder, clazz)

    /**
     * Creates an AI agent node for handling language model requests.
     *
     * @param name The optional name of the node. If null, the name will be automatically generated.
     * @param allowToolCalls Indicates whether the node is allowed to make tool calls during execution. Defaults to true.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String] and output of type [Message.Response].
     */
    @JvmOverloads
    public fun llmRequest(
        name: String? = null,
        allowToolCalls: Boolean = true,
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMRequest(name, allowToolCalls)
        return node
    }

    /**
     * A utility method that performs no modifications or operations on its input
     * and simply returns it as output. Useful as a pass-through or identity transformation.
     *
     * @param clazz the `Class` instance representing the type of the input and output.
     * @param name an optional name for the node. If null, a default name is used.
     * @return an instance of [AIAgentNodeBase] configured to return the input of type [T] as the output without modification.
     */
    @JvmOverloads
    public fun <T : Any> doNothing(
        clazz: Class<T>,
        name: String? = null
    ): AIAgentNodeBase<T, T> = withInput(clazz).withOutput(clazz).withAction { input, _ -> input }

    /**
     * Creates an AI agent node that processes language model requests while exclusively enabling tool calls during execution.
     *
     * @param name An optional name for the node. If null, the name will be automatically generated.
     * @return An instance of [AIAgentNodeBase] configured to handle language model requests with input of type [String]
     *         and output of type [Message.Response], where only tool calls are permitted.
     */
    @JvmOverloads
    public fun llmRequestOnlyCallingTools(
        name: String? = null
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMRequestOnlyCallingTools(name)
        return node
    }

    /**
     * Creates an AI agent node configured to handle language model requests that only involve tool calls.
     * This method is deprecated and replaced by [llmRequestOnlyCallingTools].
     *
     * @param name An optional name for the node. If null, a name will be automatically generated.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
     *         and output of type [Message.Response].
     */
    @JvmOverloads
    @Deprecated("Use llmRequestOnlyCallingTools instead")
    public fun llmSendMessageOnlyCallingTools(
        name: String? = null
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMSendMessageOnlyCallingTools(name)
        return node
    }

    /**
     * Creates an AI agent node designed to handle language model requests where only tool calls
     * are executed, and multiple responses are returned.
     *
     * @param name An optional name for the node. If null, a default name will be generated automatically.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with
     *         input of type [String] and output of type [List<Message.Response>].
     */
    @JvmOverloads
    public fun llmRequestMultipleOnlyCallingTools(
        name: String? = null
    ): AIAgentNodeBase<String, List<Message.Response>> {
        val node by strategyBuilder.builder.nodeLLMRequestMultipleOnlyCallingTools(name)
        return node
    }

    /**
     * Creates an AI agent node that processes a language model request while forcefully utilizing a specific tool.
     *
     * @param name The optional name of the node. If null, the name will be automatically generated.
     * @param tool A descriptor of the tool that must be utilized during the request execution.
     * @return An instance of [AIAgentNodeBase] configured to process the language model request with input of type [String] and output of type [Message.Response], ensuring the specified
     *  tool is used.
     */
    @JvmOverloads
    public fun llmRequestForceOneTool(
        name: String? = null,
        tool: ToolDescriptor
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMRequestForceOneTool(name, tool)
        return node
    }

    /**
     * Creates an AI agent node that forces the use of a specified tool during the handling of a language model request.
     *
     * @param name An optional name for the node. If null, a name will be automatically generated.
     * @param tool The descriptor of the tool that must be used during the node's execution.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
     * and output of type [Message.Response].
     * @deprecated Use [llmRequestForceOneTool] instead, as it provides the same functionality with updated naming conventions.
     */
    @JvmOverloads
    @Deprecated("Use llmRequestForceOneTool instead")
    public fun llmSendMessageForceOneTool(
        name: String? = null,
        tool: ToolDescriptor
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMSendMessageForceOneTool(name, tool)
        return node
    }

    /**
     * Creates an AI agent node that forces the execution of a single specified tool during the handling
     * of a language model request.
     *
     * @param name An optional name for the node. If null, a default name will be generated.
     * @param tool The tool to be used during request execution. This tool is mandatory for the node's operation.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with input
     *         of type [String] and output of type [Message.Response].
     */
    @JvmOverloads
    public fun llmRequestForceOneTool(
        name: String? = null,
        tool: Tool<*, *>
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMRequestForceOneTool(name, tool)
        return node
    }

    /**
     * Creates an AI agent node for handling a language model request that forcibly uses a single tool.
     *
     * @param name An optional name for the node. If null, the name will be automatically generated.
     * @param tool The tool to be forcibly used during the request processing.
     * @return An instance of [AIAgentNodeBase] configured to process language model requests with input of type [String]
     * and output of type [Message.Response].
     * @throws IllegalStateException if the node could not be created due to invalid configurations.
     */
    @JvmOverloads
    @Deprecated("Use llmRequestForceOneTool instead")
    public fun llmSendMessageForceOneTool(
        name: String? = null,
        tool: Tool<*, *>
    ): AIAgentNodeBase<String, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMSendMessageForceOneTool(name, tool)
        return node
    }

    /**
     * Creates an AI agent node for moderating messages using a specified language model.
     *
     * @param name The optional name for the node. If null, the name will be generated automatically.
     * @param moderatingModel The language model to be used for moderation. If null, a default model will be used.
     * @param includeCurrentPrompt Indicates whether the current prompt should be included in the moderation process. Defaults to false.
     * @return An instance of [AIAgentNodeBase] configured to process input messages of type [Message] and produce moderated messages of type [ModeratedMessage].
     */
    @JvmOverloads
    public fun llmModerateMessage(
        name: String? = null,
        moderatingModel: LLModel? = null,
        includeCurrentPrompt: Boolean = false
    ): AIAgentNodeBase<Message, ModeratedMessage> {
        val node by strategyBuilder.builder.nodeLLMModerateMessage(name, moderatingModel, includeCurrentPrompt)
        return node
    }

    /**
     * Performs a streaming request to an LLM (Language Model) with an optional name and structure definition,
     * allowing for transformation of the stream data.
     *
     * @param name An optional name for the streaming request. If null, no name will be associated with the request.
     * @param structureDefinition An optional structure definition to define the shape or format of the streaming data.
     *                            If null, no specific structure is assumed.
     * @param transformStreamData A function to transform the stream data. Takes a `Publisher` of `StreamFrame` as input
     *                            and returns a `Publisher` of the transformed data of type `T`.
     * @return An instance of `AIAgentNodeBase` with an input type of `String` and an output type of `Publisher` of any type.
     */
    @JvmOverloads
    public fun <T : Any> llmRequestStreaming(
        name: String? = null,
        structureDefinition: StructureDefinition? = null,
        transformStreamData: (Publisher<StreamFrame>) -> Publisher<T>
    ): AIAgentNodeBase<String, Publisher<*>> =
        this // TODO: @EugeneTheDev: change to Publisher<T> once type tokens are ready
            .withInput(String::class.java)
            .withOutput(Publisher::class.java)
            .executeOnLLMDispatcher { input ->
                requestStreamingImpl(input, structureDefinition) { streamFrameFlow ->
                    transformStreamData(streamFrameFlow.asPublisher()).asFlow()
                }.asPublisher()
            }

    /**
     * Creates an AI agent node configured for processing streaming requests to a language model.
     *
     * @param name An optional name for the node. If null, the name will be automatically generated.
     * @param structureDefinition An optional structure definition for customizing the content generated by the node.
     * @return An instance of [AIAgentNodeBase], configured to process language model requests with input of type [String]
     * and output as a stream of [StreamFrame].
     */
    @JvmOverloads
    public fun llmRequestStreaming(
        name: String? = null,
        structureDefinition: StructureDefinition? = null
    ): AIAgentNodeBase<String, Publisher<StreamFrame>> {
        val node by strategyBuilder.builder.nodeLLMRequestStreaming(name, structureDefinition)
            .transform { it.asPublisher() }

        return node
    }

    /**
     * Creates an AI agent node that processes multiple responses from a language model request.
     *
     * @param name Optional name for the node. If null, an auto-generated name will be used.
     * @return An instance of [AIAgentNodeBase] configured to handle language model requests with input of type [String] and output of type [List] of [Message.Response].
     */
    @JvmOverloads
    public fun llmRequestMultiple(
        name: String? = null
    ): AIAgentNodeBase<String, List<Message.Response>> {
        val node by strategyBuilder.builder.nodeLLMRequestMultiple(name)
        return node
    }

    /**
     * Executes a tool and returns an AI agent node configured for tool execution.
     *
     * @param name An optional name for the tool execution node. If null, a default name is generated.
     * @return An instance of [AIAgentNodeBase] that handles tool execution with input of type [Message.Tool.Call]
     *         and output of type [ReceivedToolResult].
     */
    @JvmOverloads
    public fun executeTool(
        name: String? = null
    ): AIAgentNodeBase<Message.Tool.Call, ReceivedToolResult> {
        val node by strategyBuilder.builder.nodeExecuteTool(name)
        return node
    }

    /**
     * Creates an AI agent node for sending a single tool result to the language model.
     *
     * @param name An optional name for the node. If not specified, a default name is automatically assigned.
     * @return An instance of [AIAgentNodeBase] configured to process input of type [ReceivedToolResult]
     *         and produce output of type [Message.Response].
     */
    @JvmOverloads
    public fun llmSendToolResult(
        name: String? = null
    ): AIAgentNodeBase<ReceivedToolResult, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMSendToolResult(name)
        return node
    }

    /**
     * Creates an AI agent node that processes a list of tool execution results and generates a response,
     * limited to nodes that make tool calls during execution.
     *
     * @param name The optional name of the node. If null, the name will be automatically generated.
     * @return An instance of [AIAgentNodeBase] configured to process input of type [List<ReceivedToolResult>]
     *         and output a single [Message.Response].
     */
    @JvmOverloads
    public fun llmSendToolResultOnlyCallingTools(
        name: String? = null
    ): AIAgentNodeBase<List<ReceivedToolResult>, Message.Response> {
        val node by strategyBuilder.builder.nodeLLMSendToolResultOnlyCallingTools(name)
        return node
    }

    /**
     * Executes multiple tools as part of an AI agent's processing node.
     *
     * @param name An optional name for the node. If not provided, a default name will be generated.
     * @param parallelTools A flag indicating whether the tools should be executed in parallel. Defaults to `false`.
     * @return A node configured to handle execution of a list of tools (`List<Message.Tool.Call>`)
     *         with the results being a list of received tool results (`List<ReceivedToolResult>`).
     */
    @JvmOverloads
    public fun executeMultipleTools(
        name: String? = null,
        parallelTools: Boolean = false
    ): AIAgentNodeBase<List<Message.Tool.Call>, List<ReceivedToolResult>> {
        val node by strategyBuilder.builder.nodeExecuteMultipleTools(name, parallelTools)
        return node
    }

    /**
     * Executes multiple tools and sends their aggregated results as responses.
     *
     * @param name An optional name for the node. If null, a default name will be generated.
     * @param parallelTools Indicates whether the tools should be executed in parallel. Defaults to false.
     * @return An instance of [AIAgentNodeBase] that performs the execution of multiple tools with an input type of [List] of [Message.Tool.Call]
     *         and outputs a [List] of [Message.Response].
     */
    @JvmOverloads
    public fun executeMultipleToolsAndSendResults(
        name: String? = null,
        parallelTools: Boolean = false
    ): AIAgentNodeBase<List<Message.Tool.Call>, List<Message.Response>> {
        val node by strategyBuilder.builder.nodeExecuteMultipleToolsAndSendResults(name, parallelTools)
        return node
    }

    /**
     * Creates an AI agent node for sending multiple tool results to a language model.
     *
     * @param name An optional name for the node. If null, a name will be automatically generated.
     * @return An instance of [AIAgentNodeBase] configured to process input of type [List] of [ReceivedToolResult]
     *         and generate output of type [List] of [Message.Response].
     */
    @JvmOverloads
    public fun llmSendMultipleToolResults(
        name: String? = null
    ): AIAgentNodeBase<List<ReceivedToolResult>, List<Message.Response>> {
        val node by strategyBuilder.builder.nodeLLMSendMultipleToolResults(name)
        return node
    }

    /**
     * Creates an AI agent node designed to process multiple tool result responses while restricting interactions
     * to only the tools that were explicitly invoked. This is useful for controlled scenarios where tool response
     * management is confined to specific tools.
     *
     * @param name An optional name for the node. If null, the name will be automatically generated.
     * @return An instance of [AIAgentNodeBase] configured to handle a list of [ReceivedToolResult] as input and
     *         generate a list of [Message.Response] as output, ensuring only called tools are involved.
     */
    @JvmOverloads
    public fun llmSendMultipleToolResultsOnlyCallingTools(
        name: String? = null
    ): AIAgentNodeBase<List<ReceivedToolResult>, List<Message.Response>> {
        val node by strategyBuilder.builder.nodeLLMSendMultipleToolResultsOnlyCallingTools(name)
        return node
    }

    /**
     * Sends a structured request to a language model and returns a corresponding response node.
     *
     * @param name An optional name for the request node. Defaults to `null`.
     * @param config The configuration for the structured request that defines the input and expected output types.
     * @param fixingParser An optional parser for fixing or refining the structure of the response. Defaults to `null`.
     * @return A node that represents the result of the structured language model request, containing a string input
     *         and a structured response of type `Result<StructuredResponse<T>>`.
     */
    @JvmOverloads
    public fun <T : Any> llmRequestStructured(
        name: String? = null,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): AIAgentNodeBase<String, Result<StructuredResponse<T>>> {
        val node by strategyBuilder.builder.nodeLLMRequestStructured(name, config, fixingParser)
        return node
    }

    /**
     * Creates a new instance of `CompressHistoryNodeBuilder` with an optional custom name.
     * If no name is specified, a default name will be generated using the current node counter.
     *
     * @param name An optional string to specify a custom name for the `CompressHistoryNodeBuilder`.
     *             If null, a default name is generated in the format "compress-history-{counter}".
     * @return A new instance of `CompressHistoryNodeBuilder` initialized with the provided or generated name.
     */
    @JvmOverloads
    public fun llmCompressHistory(name: String? = null): CompressHistoryNodeBuilder =
        CompressHistoryNodeBuilder(name ?: "compress-history-${strategyBuilder.nodeCounter++}", strategyBuilder)

    /**
     * Creates an AI agent node that performs a structured request using the given tool.
     *
     * @param name Optional name of the request. Can be used to identify or label the request.
     * @param tool The tool used to perform the structured request. It contains argument and result type information.
     * @param doAppendPrompt Indicates whether to append the prompt during execution. Defaults to true.
     * @return An AI agent node that is configured to use the specified tool and performs structured requests.
     */
    @JvmOverloads
    public fun <ToolArg, TResult> llmRequestStructured(
        name: String? = null,
        tool: Tool<ToolArg, TResult>,
        doAppendPrompt: Boolean = true
    ): AIAgentNodeBase<ToolArg, SafeTool.Result<TResult>> {
        // TODO: @EugeneTheDev: once type tokens are done
        return TODO("withInput(tool.argsToken).withOutput(tool.resultToken).executeOnStrategyDispatcher { toolArgs -> executeSingleToolImpl(tool, toolArgs, doAppendPrompt) }")
    }
}

/**
 * Builder class for creating a compress history node in an AI agent graph strategy.
 *
 * @param name The name of the node being created.
 * @param strategyBuilder An instance of [TypedGraphStrategyBuilder] used to construct and configure the graph strategy that includes this node.
 */
@JavaAPI
public class CompressHistoryNodeBuilder(
    private val name: String,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
) {
    /**
     * Configures the current `CompressHistoryNodeBuilder` with a specific input type,
     * returning a new `TypedCompressHistoryNodeBuilder` specialized for the provided type.
     *
     * @param Input The type of input to be associated with the resulting `TypedCompressHistoryNodeBuilder`.
     *              It must be a non-nullable type.
     * @param clazz The `Class` object representing the input type to associate with the builder.
     * @return A new instance of `TypedCompressHistoryNodeBuilder` configured with the specified input type.
     */
    public fun <Input : Any> withInput(clazz: Class<Input>): TypedCompressHistoryNodeBuilder<Input> =
        TypedCompressHistoryNodeBuilder(
            name,
            strategyBuilder,
            clazz
        )
}

/**
 * A builder class for configuring and creating a typed compression history node within an AI agent graph.
 *
 * @param Input The type of the input data for the node.
 * @property name The name of the node to be created.
 * @property strategyBuilder The graph strategy builder associated with this node builder.
 * @property inputClass The Kotlin class type of the input data.
 * @property retrievalModel An optional large language model (LLM) used for retrieval purposes.
 * @property strategy The strategy for compressing historical data, which determines how history is managed.
 * @property preserveMemory A flag indicating whether to prioritize preserving memory during history compression.
 */
@JavaAPI
public class TypedCompressHistoryNodeBuilder<Input : Any>(
    private val name: String,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    private val inputClass: Class<Input>,
    private var retrievalModel: LLModel? = null,
    private var strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    private var preserveMemory: Boolean = true,
) {

    /**
     * Configures the node builder with a specific retrieval model.
     *
     * @param model The retrieval model to be used in the configured node. An instance of [LLModel] representing the model to apply.
     * @return This builder instance with the specified retrieval model configured.
     */
    public fun withRetrievalModel(model: LLModel): TypedCompressHistoryNodeBuilder<Input> = this.apply {
        this.retrievalModel = model
    }

    /**
     * Sets the history compression strategy to be used for this builder.
     *
     * @param strategy The history compression strategy to apply.
     * @return This builder instance with the specified compression strategy.
     */
    public fun compressionStrategy(strategy: HistoryCompressionStrategy): TypedCompressHistoryNodeBuilder<Input> = this.apply {
        this.strategy = strategy
    }

    /**
     * Sets whether memory preservation is enabled for the node being built.
     *
     * @param preserveMemory A boolean indicating whether memory should be preserved.
     * @return This builder instance with the updated memory preservation setting.
     */
    public fun preserveMemory(preserveMemory: Boolean): TypedCompressHistoryNodeBuilder<Input> = this.apply {
        this.preserveMemory = preserveMemory
    }

    /**
     * Builds and returns an instance of [AIAgentNodeBase] configured for compressing history
     * in the AI agent strategy graph. The resulting node is bound to the current configuration
     * parameters, including the retrieval model, compression strategy, and memory preservation settings.
     *
     * @return An [AIAgentNodeBase] instance responsible for compressing history based on the
     * specified inputs and configuration within the strategy graph.
     */
    public fun build(): AIAgentNodeBase<Input, Input> = strategyBuilder
        .node(name)
        .withInput(inputClass)
        .withOutput(inputClass)
        .executeOnLLMDispatcher { input ->
            llmCompressHistoryImpl(input, retrievalModel, strategy, preserveMemory)
        }
}

/**
 * A Java builder class for creating [AIAgentNode] with a specified input type.
 *
 * @param Input The type of input data the [AIAgentNode] will process.
 * @property name An optional name for the agent node.
 * @property inputClass The class representation of the input type.
 */
@JavaAPI
public class AIAgentNodeBuilderWithInput<Input : Any>(
    private val name: String?,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    private val inputClass: Class<Input>
) {
    /**
     * Specifies the output type for the [AIAgentNode] and returns a builder for creating a typed [AIAgentNode].
     *
     * @param clazz The class representing the output type of the node.
     * @return A builder for creating a typed [AIAgentNode] configured with the specified output type.
     */
    public fun <Output : Any> withOutput(clazz: Class<Output>): TypedAIAgentNodeBuilder<Input, Output> =
        TypedAIAgentNodeBuilder(name, strategyBuilder, inputClass, clazz)

    /**
     * Appends a prompt to the AI agent node configuration.
     *
     * The prompt is constructed using the provided `body` lambda, which operates on a `PromptBuilder`.
     * Optionally, a `name` can be provided to identify the prompt configuration node.
     *
     * @param name An optional name to identify the configuration node. Defaults to `null` if not specified.
     * @param body A lambda function that defines the prompt using the `PromptBuilder`.
     * @return An instance of `AIAgentNodeBase` configured with the specified prompt.
     */
    public fun appendPrompt(
        promptUpdate: PromptBuilderAction
    ): AIAgentNodeBase<Input, Input> = this
        .withOutput(inputClass)
        .executeOnLLMDispatcher { input ->
            appendPromptImpl(input) {
                promptUpdate.build(this)
            }
        }

    /**
     * Represents an action that defines how a [PromptBuilder] is configured.
     *
     * This functional interface is primarily used in the context of building prompt-related configurations
     * for AI agent nodes. Implementations of this interface customize a [PromptBuilder] instance, which
     * facilitates the creation of structured or dynamic prompts.
     *
     * The interface is annotated with [JavaAPI], indicating it is designed to support interoperability
     * with Java code and follows conventions favorable for Java environments.
     */
    @JavaAPI
    public fun interface PromptBuilderAction {
        /**
         * Executes the provided action on the given PromptBuilder instance.
         *
         * @param promptBuilder The PromptBuilder instance to be configured or modified.
         */
        public fun build(promptBuilder: PromptBuilder)
    }

    /**
     * Sends a streaming request to the Large Language Model (LLM) and processes the results, optionally using
     * a specified structure definition for content customization.
     *
     * @param structureDefinition An optional [StructureDefinition] instance that defines the structure of
     * textual content for the LLM request. If `null`, the default behavior is used without structured customization.
     * @return An instance of [AIAgentNodeBase] with the input type [Input] and output type as a list of unspecified elements.
     */
    public fun llmRequestStreamingAndSendResults(
        structureDefinition: StructureDefinition? = null
    ): AIAgentNodeBase<Input, List<*>> =
        this // TODO: @EugeneTheDev change to List<Message.Response> once type tokens are merged
            .withOutput(List::class.java)
            .executeOnLLMDispatcher { input ->
                requestStreamingAndSendResultsImpl(structureDefinition)
            }

    /**
     * Configures an AI agent node to evaluate and critique input data as a simulated "judge" using a specified task
     * and an optional Large Language Model (LLM).
     *
     * @param task The task or criteria used by the AI agent to evaluate input data.
     * @param llmModel An optional instance of [LLModel] representing the Large Language Model to be used. Defaults to `null`.
     * @return An instance of [AIAgentNodeBase] configured to process input of type [Input] and generate output of type [CriticResult].
     */
    @JvmOverloads
    public fun llmAsAJudge(
        task: String,
        llmModel: LLModel? = null
    ): AIAgentNodeBase<Input, CriticResult<Input>> {
        val node by strategyBuilder.builder.node<Input, CriticResult<Input>>(
            inputType = typeToken(inputClass),
            outputType = typeToken(CriticResult::class),
        ) { input ->
            setupLLMAsAJudge(task, llmModel, input)
        }

        return node
    }

    /**
     * Configures the node to produce a structured output based on the specified configuration.
     *
     * This method allows setting up structured output behavior for an AI agent node by defining
     * how content should be structured when requests are processed. The structure is determined
     * by the specified `StructuredRequestConfig`, which provides options for different providers
     * and fallback behaviors.
     *
     * @param config The configuration specifying how structured output should be handled, including
     *               provider-specific definitions and default fallback options.
     * @return An instance of `AIAgentNodeBase` with the input type [Input] and output type [Input],
     *         updated with the configured structured output setup.
     */
    @JvmOverloads
    public fun <T : Any> setStructuredOutput(
        config: StructuredRequestConfig<T>,
    ): AIAgentNodeBase<Input, Input> = this
        .withOutput(inputClass)
        .executeOnStrategyDispatcher { message ->
            setStructuredOutputImpl(config, message)
        }
}

/**
 * A Java builder class for creating instances of `AIAgentNode` with strongly typed input and output data.
 *
 * @param Input The type of the input data the node will process.
 * @param Output The type of the output data the node will produce.
 * @property name The name of the node, used for identification and debugging purposes.
 * @property inputClass The class representing the type of the input data.
 * @property outputClass The class representing the type of the output data.
 */
@JavaAPI
public class TypedAIAgentNodeBuilder<Input : Any, Output : Any>(
    private val name: String?,
    private val strategyBuilder: TypedGraphStrategyBuilder<*, *>,
    private val inputClass: Class<Input>,
    private val outputClass: Class<Output>
) {
    /**
     * Creates and returns an instance of [AIAgentNode] that encapsulates the provided execution logic.
     *
     * This method binds a specified action to an AI agent node, enabling the node to process input of type [Input]
     * and generate output of type [Output] within the context of an [AIAgentGraphContextBase].
     *
     * @param nodeAction A lambda function that represents the processing logic. It takes two parameters:
     * - [Input]: The input data for the node.
     * - [AIAgentGraphContextBase]: The execution context in which the action is performed.
     * The function returns a result of type [Output].
     *
     * @return A new instance of [AIAgentNode] configured with the provided processing logic, ready for execution within an AI graph.
     */
    public fun withAction(nodeAction: ContextualAction<Input, Output>): AIAgentNode<Input, Output> {
        return AIAgentNode(
            name = name ?: "node-${strategyBuilder.nodeCounter++}",
            inputType = typeToken(inputClass),
            outputType = typeToken(outputClass),
        ) { input ->
            strategyBuilder.agentConfig.runOnStrategyDispatcher {
                nodeAction.execute(input, this)
            }
        }
    }

    internal fun executeOnLLMDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        strategyBuilder.agentConfig.runOnLLMDispatcher {
            ctx.asyncAction(input)
        }
    }

    @OptIn(InternalAgentsApi::class)
    internal fun executeOnStrategyDispatcher(
        asyncAction: suspend AIAgentGraphContextBase.(Input) -> Output
    ): AIAgentNode<Input, Output> = withAction { input, ctx ->
        strategyBuilder.agentConfig.runOnStrategyDispatcher {
            ctx.asyncAction(input)
        }
    }
}
