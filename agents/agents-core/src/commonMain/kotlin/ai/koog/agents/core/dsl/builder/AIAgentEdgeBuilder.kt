package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentEdge
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.utils.Option

/**
 * Marks a function as a transformation specific to edges within the AI agent's DSL
 * to ensure its proper highlighting.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class EdgeTransformationDslMarker

/**
 * A builder class for constructing an `AIAgentEdge` instance, which represents a directed edge
 * connecting two nodes in a graph of an AI agent's processing pipeline. This edge defines
 * the flow of data from a source node to a target node, enabling transformation or filtering
 * of the source node's output before passing it to the target node.
 *
 * @param IncomingOutput The type of output produced by the source node connected to this edge.
 * @param OutgoingInput The type of input accepted by the target node connected to this edge.
 * @constructor This builder should only be constructed internally using intermediate configuration.
 *
 * @property edgeIntermediateBuilder The intermediate configuration used for building the edge. It includes
 * the source and target nodes, as well as the functionality for processing the output of the source node.
 */
public class AIAgentEdgeBuilder<IncomingOutput, OutgoingInput, CompatibleOutput : OutgoingInput> internal constructor(
    private val edgeIntermediateBuilder: AIAgentEdgeBuilderIntermediate<IncomingOutput, CompatibleOutput, OutgoingInput>,
) : BaseBuilder<AIAgentEdge<IncomingOutput, OutgoingInput>> {
    override fun build(): AIAgentEdge<IncomingOutput, OutgoingInput> {
        return AIAgentEdge(
            toNode = edgeIntermediateBuilder.toNode,
            forwardOutput = edgeIntermediateBuilder.forwardOutputComposition
        )
    }
}

/**
 * Represents an intermediate stage in the construction of a directed edge between two nodes
 * in an AI agent strategy graph. This class provides mechanisms to define conditions
 * and transformations that dictate how data flows and is processed between the nodes.
 *
 * @param IncomingOutput The type of the output data produced by the originating node.
 * @param IntermediateOutput The type of intermediate data produced after transformation or filtering.
 * @param OutgoingInput The type of input data that the destination node expects.
 * @constructor Creates an intermediate edge builder, defining the source and destination nodes
 * along with the transformation logic for the data flow between them.
 *
 * @property fromNode The originating node in the directed edge.
 * @property toNode The destination node in the directed edge.
 * @property forwardOutputComposition A suspending lambda function responsible for transforming
 * the originating node's output into an intermediate representation
 * or filtering the flow based on specific conditions.
 */
@EdgeTransformationDslMarker
public class AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    internal val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    internal val toNode: AIAgentNodeBase<OutgoingInput, *>,
    internal val forwardOutputComposition: suspend (AIAgentGraphContextBase, IncomingOutput) -> Option<IntermediateOutput>
) {
    /**
     * Filters the intermediate outputs of the [ai.koog.agents.core.agent.entity.AIAgentNode] based on a specified condition.
     *
     * @param block A suspending lambda function that takes the AI agent's context and an intermediate output as parameters.
     *              It returns `true` if the given intermediate output satisfies the condition, and `false` otherwise.
     * @return A new instance of `AIAgentEdgeBuilderIntermediate` that includes only the filtered intermediate outputs
     *         satisfying the specified condition.
     */
    @EdgeTransformationDslMarker
    public infix fun onCondition(
        block: suspend AIAgentGraphContextBase.(output: IntermediateOutput) -> Boolean
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> {
        return AIAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .filter { transOutput -> ctx.block(transOutput) }
            },
        )
    }

    /**
     * Transforms the intermediate output of the [ai.koog.agents.core.agent.entity.AIAgentNode] by applying a given transformation block.
     *
     * @param block A suspending lambda that defines the transformation to be applied to the intermediate output.
     *              It takes the AI agent's context and the intermediate output as parameters and returns a new intermediate output.
     * @return A new instance of `AIAgentEdgeBuilderIntermediate` with the transformed intermediate output type.
     */
    @EdgeTransformationDslMarker
    public infix fun <NewIntermediateOutput> transformed(
        block: suspend AIAgentGraphContextBase.(IntermediateOutput) -> NewIntermediateOutput
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, NewIntermediateOutput, OutgoingInput> {
        return AIAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .map { ctx.block(it) }
            }
        )
    }
}
