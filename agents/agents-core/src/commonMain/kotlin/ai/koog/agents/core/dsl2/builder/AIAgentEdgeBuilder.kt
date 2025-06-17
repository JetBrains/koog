package ai.koog.agents.core.dsl2.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.utils.Option
import ai.koog.agents.core.agent.entity.AIAgentEdge
import ai.koog.agents.core.agent.entity.AIAgentNodeBase

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
public abstract class AIAgentEdgeBuilder<SourceOutput, TargetInput> internal constructor(
    internal val forwardOutput: suspend (context: AIAgentContextBase, output: SourceOutput) -> Option<TargetInput>,
) {
    internal abstract val sourceNode: AIAgentNodeBase<*, SourceOutput>
}

public infix fun <SourceInput, SourceOutput, TargetOutput> AIAgentEdgeBuilder<SourceInput, SourceOutput>.forwardTo(
    target: AIAgentNodeBase<SourceOutput, TargetOutput>,
): AIAgentNodeBase<SourceOutput, TargetOutput> {
    sourceNode.addEdge(AIAgentEdge(target, forwardOutput))
    return target
}

/**
 * Filters the intermediate outputs of the [ai.koog.agents.core.agent.entity.AIAgentNode] based on a specified condition.
 *
 * @param block A suspending lambda function that takes the AI agent's context and an intermediate output as parameters.
 *              It returns `true` if the given intermediate output satisfies the condition, and `false` otherwise.
 * @return A new instance of `AIAgentEdgeBuilderIntermediate` that includes only the filtered intermediate outputs
 *         satisfying the specified condition.
 */
public infix fun <SourceOutput, TargetInput> AIAgentEdgeBuilder<SourceOutput, TargetInput>.onCondition(
    block: suspend AIAgentContextBase.(output: TargetInput) -> Boolean,
): AIAgentEdgeBuilder<SourceOutput, TargetInput> {
    return object : AIAgentEdgeBuilder<SourceOutput, TargetInput>(
        forwardOutput = { ctx, output -> forwardOutput(ctx, output).filter { ctx.block(it) } },
    ) {
        override val sourceNode: AIAgentNodeBase<*, SourceOutput> get() = this@onCondition.sourceNode
    }
}

/**
 * Transforms the intermediate output of the [ai.koog.agents.core.agent.entity.AIAgentNode] by applying a given transformation block.
 *
 * @param block A suspending lambda that defines the transformation to be applied to the intermediate output.
 *              It takes the AI agent's context and the intermediate output as parameters and returns a new intermediate output.
 * @return A new instance of `AIAgentEdgeBuilderIntermediate` with the transformed intermediate output type.
 */
public infix fun <SourceOutput, TargetInput, TransformedInput> AIAgentEdgeBuilder<SourceOutput, TargetInput>.transformed(
    block: suspend AIAgentContextBase.(TargetInput) -> TransformedInput,
): AIAgentEdgeBuilder<SourceOutput, TransformedInput> {
    return object : AIAgentEdgeBuilder<SourceOutput, TransformedInput>(
        forwardOutput = { ctx, output -> forwardOutput(ctx, output).map { ctx.block(it) } },
    ) {
        override val sourceNode: AIAgentNodeBase<*, SourceOutput> get() = this@transformed.sourceNode
    }
}
