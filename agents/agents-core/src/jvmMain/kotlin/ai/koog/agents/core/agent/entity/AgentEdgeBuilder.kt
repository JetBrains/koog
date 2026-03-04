@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilder
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate
import ai.koog.agents.core.utils.Option
import ai.koog.agents.core.utils.Some
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.agents.core.utils.submitToMainDispatcher

/**
 * A builder class for creating and managing edges between AI agent nodes in a graph.
 * This class serves as an entry point to initiate the construction of directed edges
 * by associating a starting node with subsequent interconnected nodes.
 *
 * The builder facilitates a step-by-step process for defining the relationships and
 * data flows between nodes in the agent strategy graph. Starting from a defined node,
 * it returns a `PartialAgentEdgeBuilder` to further specify the destination node and
 * finalize the edge configuration.
 *
 * This is specifically designed for Java interoperability.
 */
@JavaAPI
public class AgentEdgeBuilder {
    /**
     * Initializes the construction of a partial edge in the AI agent strategy graph, starting
     * from the specified node. The partial edge allows further specification of the destination node
     * and the transformation logic for data flowing between nodes.
     *
     * @param node The starting node for the edge being built. This node represents the source
     * of the data that will flow through the constructed edge.
     * @return A [PartialAgentEdgeBuilder] instance that provides methods to further define
     * the edge, such as specifying the destination node and additional data transformations.
     */
    public fun <Input, Output> from(node: AIAgentNodeBase<Input, Output>): PartialAgentEdgeBuilder<Input, Output> =
        PartialAgentEdgeBuilder(node)
}

/**
 * A builder class used to define a transitional directed edge in an AI agent strategy graph.
 *
 * This class allows the creation of a partial edge connection from a specified node to its target
 * node, enabling data flow from the source node's output to the target node's input. The actual
 * configuration of the edge (e.g., data transformations, flow conditions) is finalized using the
 * next step in the builder chain.
 *
 * @param IncomingInput The type of input that the source node processes.
 * @param IncomingOutput The type of output that the source node produces.
 * @property fromNode The source node from which the partial edge originates.
 */
@JavaAPI
public class PartialAgentEdgeBuilder<IncomingInput, IncomingOutput>(
    private val fromNode: AIAgentNodeBase<IncomingInput, IncomingOutput>
) {
    /**
     * Creates a directed edge from the current node to the specified node, enabling the flow of
     * data between them in the AI agent strategy graph. This method connects the current node's
     * output directly to the input of the specified node without applying transformations.
     *
     * @param toNode The destination node to which the current node's output will be forwarded.
     *               The type of input this node processes is represented by `OutgoingInput`,
     *               and the type of output it produces is represented by `OutgoingOutput`.
     * @return A `FullAgentEdgeBuilder` instance that allows further customization of the edge
     *         and its data flow properties. This builder establishes the connection between the
     *         current node's output and the specified node's input.
     */
    public fun <OutgoingInput, OutgoingOutput> to(
        toNode: AIAgentNodeBase<OutgoingInput, OutgoingOutput>
    ): FullAgentEdgeBuilder<IncomingOutput, IncomingOutput, OutgoingInput> = FullAgentEdgeBuilder(
        fromNode = fromNode,
        toNode = toNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}

/**
 * A builder class for constructing a specialized edge in an AI agent strategy graph.
 * This edge enables data flow between two nodes while supporting a composition of
 * the output transformation logic.
 *
 * @param IncomingOutput The type of input data from the `fromNode`.
 * @param IntermediateOutput The type of processed data output by the `forwardOutputComposition` function.
 * @param OutgoingInput The type of input data expected by the `toNode`.
 * @constructor Creates a `FullAgentEdgeBuilder` that connects a source node (`fromNode`) to a destination node (`toNode`)
 * with the ability to define intermediate data transformation using `forwardOutputComposition`.
 * @param fromNode The source node in the strategy graph. This node produces the input data.
 * @param toNode The destination node in the strategy graph. This node consumes the transformed data.
 * @param forwardOutputComposition A suspendable function that transforms the output of the `fromNode`
 * into an intermediate format before passing it to the `toNode`. The transformation output is represented
 * as an `Option` type, allowing for optional chaining and flexibility in data flow.
 */
@JavaAPI
public open class FullAgentEdgeBuilder<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    protected val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    protected val toNode: AIAgentNodeBase<OutgoingInput, *>,
    protected val forwardOutputComposition: suspend (AIAgentGraphContextBase, IncomingOutput) -> Option<IntermediateOutput>
) {
    /**
     * Applies a contextual condition to filter the output being processed and forwarded within the graph.
     *
     * This method attaches a condition that evaluates whether a given intermediate output
     * should be propagated to the destination node. The condition is evaluated based on the output
     * and the current AI agent graph context.
     *
     * @param condition A condition to be evaluated for each intermediate output. It takes the output
     *                  and the context as arguments and returns a boolean indicating whether to propagate
     *                  the output (`true`) or filter it out (`false`).
     * @return A builder instance that allows further configuration or chaining of processing steps.
     */
    public fun onCondition(
        condition: ContextualCondition<IntermediateOutput>
    ): FullAgentEdgeBuilder<IncomingOutput, IntermediateOutput, OutgoingInput> = FullAgentEdgeBuilder(
        fromNode, toNode, forwardOutputComposition = { ctx, output ->
            with(forwardOutputComposition(ctx, output)) {
                ctx.config.submitToMainDispatcher {
                    filter { transOutput ->
                        condition.invoke(transOutput, ctx)
                    }
                }
            }
        }
    )

    /**
     * Attaches a condition that filters intermediate outputs before they are propagated to the
     * target node within the agent's processing graph.
     *
     * This method applies a simple, stateless condition to determine whether an intermediate
     * output should be forwarded downstream. The condition is evaluated based on the output value.
     *
     * @param condition A simple condition to evaluate for each intermediate output. It receives an
     *                  output object as input and returns `true` if the output should be forwarded,
     *                  or `false` if it should be filtered out.
     * @return A builder instance to allow further configuration or chaining of processing steps.
     */
    public fun onCondition(
        condition: SimpleCondition<IntermediateOutput>
    ): FullAgentEdgeBuilder<IncomingOutput, IntermediateOutput, OutgoingInput> = FullAgentEdgeBuilder(
        fromNode, toNode, forwardOutputComposition = { ctx, output ->
            with(forwardOutputComposition(ctx, output)) {
                ctx.config.submitToMainDispatcher {
                    filter { transOutput ->
                        condition.invoke(transOutput)
                    }
                }
            }
        }
    )


    public fun <CompatibleOutput : OutgoingInput> transformed(
        transformation: ContextualTransformation<IntermediateOutput, CompatibleOutput>
    ) = FullAgentEdgeBuilder(
        fromNode, toNode, forwardOutputComposition = { ctx, output ->
            with(forwardOutputComposition(ctx, output)) {
                ctx.config.submitToMainDispatcher {
                    filter { transOutput ->
                        condition.invoke(transOutput, ctx)
                    }
                }
            }
        }
    )
}

@JavaAPI
public open class CompatibleFullAgentEdgeBuilder<IncomingOutput, CompatibleOutput : OutgoingInput, OutgoingInput> internal constructor(
    fromNode: AIAgentNodeBase<*, IncomingOutput>,
    toNode: AIAgentNodeBase<OutgoingInput, *>,
    forwardOutputComposition: suspend (AIAgentGraphContextBase, IncomingOutput) -> Option<CompatibleOutput>
): FullAgentEdgeBuilder<IncomingOutput, CompatibleOutput, OutgoingInput>(
    fromNode,
    toNode,
    forwardOutputComposition
) {
    public fun build(): AIAgentEdge<IncomingOutput, OutgoingInput> {
        val intermediate = AIAgentEdgeBuilderIntermediate(fromNode, toNode, forwardOutputComposition)
        return AIAgentEdgeBuilder(intermediate).build()
    }
}

/**
 * ContextualCondition is a functional interface designed to evaluate a condition based on an output value
 * and the given AI agent graph context.
 *
 * @param Output The type of the output value that the condition evaluates.
 */
@JavaAPI
public fun interface ContextualCondition<Output> {
    /**
     * Invokes the contextual condition with the given output and context to evaluate its result.
     *
     * @param output The output data passed to the condition for evaluation.
     * @param context The contextual information required for evaluating the condition,
     *                provided as an implementation of the `AIAgentGraphContextBase` interface.
     * @return A boolean value indicating the result of the condition evaluation.
     *         Returns `true` if the condition is met, otherwise `false`.
     */
    public operator fun invoke(output: Output, context: AIAgentGraphContextBase): Boolean
}

/**
 * Represents a simple, stateless functional interface that evaluates a condition on a given output.
 * This is particularly tailored for Java compatibility, enabling usage in contexts where Java code interacts with Kotlin.
 *
 * @param Output The type of the input based on which the condition is evaluated.
 */
@JavaAPI
public fun interface SimpleCondition<Output> {
    /**
     * Invokes the condition with the given output object and evaluates whether it satisfies the condition.
     *
     * @param output The output object to be evaluated against the condition.
     * @return `true` if the given output satisfies the condition, `false` otherwise.
     */
    public operator fun invoke(output: Output): Boolean
}

/**
 * A functional interface representing a transformation operation that processes an input `output` of type [Output]
 * in the context of [AIAgentGraphContextBase] and produces a transformed result of type [NewOutput].
 *
 * The presence of the [JavaAPI] annotation indicates that this interface is optimized for interoperability with Java.
 */
@JavaAPI
public fun interface ContextualTransformation<Output, NewOutput> {
    /**
     * Transforms the given output using the provided AI agent graph context and returns a new output.
     *
     * @param output The original output to be transformed.
     * @param context The context providing the necessary information and functionality
     *                for transformation within the AI agent graph.
     * @return A new output resulting from the transformation.
     */
    public operator fun invoke(output: Output, context: AIAgentGraphContextBase): NewOutput
}

/**
 * A functional interface representing a transformation operation from a given input type to a new output type.
 *
 * @param Output The type of the input that the transformation operates on.
 * @param NewOutput The type of the output produced by the transformation.
 */
@JavaAPI
public fun interface SimpleTransformation<Output, NewOutput> {
    /**
     * Transforms the given input of type [Output] into an instance of type [NewOutput].
     *
     * @param output The input to be transformed.
     * @return The transformed output of type [NewOutput].
     */
    public operator fun invoke(output: Output): NewOutput
}

