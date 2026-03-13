@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.utils.Option

/**
 * Represents a directed edge connecting two nodes in the graph of an AI agent strategy.
 * This edge facilitates the transmission of data between a source node and a destination node,
 * allowing transformation or filtering of output values from the source node before they reach the destination node.
 *
 * @param IncomingOutput The type of output produced by the source node connected to this edge.
 * @param OutgoingInput The type of input accepted by the destination node connected to this edge.
 * @property toNode The destination node that this edge connects to.
 * @property forwardOutput A suspending function used to process the output from the source node
 * before forwarding it to the destination node. This function can transform or filter the data
 * and returns an optional value to determine whether to propagate it further.
 */
public expect class AIAgentEdge<IncomingOutput, OutgoingInput> internal constructor(
    fromNode: AIAgentNodeBase<*, IncomingOutput>,
    toNode: AIAgentNodeBase<OutgoingInput, *>,
    forwardOutput: suspend (context: AIAgentGraphContextBase, output: IncomingOutput) -> Option<OutgoingInput>,
) {
    /**
     * The source node in the AI agent strategy graph which this edge connects.
     */
    public val fromNode: AIAgentNodeBase<*, IncomingOutput>

    /**
     * The destination node in the AI agent strategy graph to which this edge connects.
     */
    public val toNode: AIAgentNodeBase<OutgoingInput, *>

    internal val forwardOutput: suspend (context: AIAgentGraphContextBase, output: IncomingOutput) -> Option<OutgoingInput>

    @Suppress("UNCHECKED_CAST")
    internal suspend fun forwardOutputUnsafe(output: Any?, context: AIAgentGraphContextBase): Option<OutgoingInput>
}
