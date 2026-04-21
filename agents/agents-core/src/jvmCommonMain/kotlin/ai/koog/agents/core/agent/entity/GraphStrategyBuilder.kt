@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.serialization.typeToken
import kotlin.reflect.KClass

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
     * Adds an edge to the graph strategy using the provided [AIAgentEdge].
     *
     * @param edge The directed edge to be added to the graph strategy. It describes the source and
     * destination nodes, along with the mechanism for handling data transmission between them.
     * @return An instance of [TypedGraphStrategyBuilder] with the specified edge added to the strategy.
     */
    public fun <IncomingOutput, OutgoingInput> edge(
        edge: AIAgentEdge<IncomingOutput, OutgoingInput>
    ): TypedGraphStrategyBuilder<Input, Output> = apply {
        edgeBuilders += {
            this.edge(edge)
        }
    }

    /**
     * Adds an edge to the graph strategy, connecting two nodes with a compatible data flow between them.
     *
     * @param from The source node of the edge. This node produces output data of a type that must be compatible with the input type expected by the destination node.
     * @param to The destination node of the edge. This node receives input data from the source node.
     * @return An instance of [TypedGraphStrategyBuilder] with the specified edge added to the strategy.
     */
    public fun <OutgoingInput, CompatibleOutput : OutgoingInput> edge(
        from: AIAgentNodeBase<*, CompatibleOutput>,
        to: AIAgentNodeBase<OutgoingInput, *>,
    ): TypedGraphStrategyBuilder<Input, Output> = edge(
        AIAgentEdge.builder()
            .from(from)
            .to(to)
            .build()
    )

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
