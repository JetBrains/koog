@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
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
public actual class AIAgentEdge<IncomingOutput, OutgoingInput> internal actual constructor(
    public actual val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    public actual val toNode: AIAgentNodeBase<OutgoingInput, *>,
    internal actual val forwardOutput: suspend (context: AIAgentGraphContextBase, output: IncomingOutput) -> Option<OutgoingInput>,
) {

    @Suppress("UNCHECKED_CAST")
    internal actual suspend fun forwardOutputUnsafe(
        output: Any?,
        context: AIAgentGraphContextBase
    ): Option<OutgoingInput> =
        forwardOutput(context, output as IncomingOutput)

    /**
     * Provides companion functionality for the `AIAgentEdge` class. This includes utility methods
     * for constructing instances of `AIAgentEdge` via a builder.
     *
     * The `AIAgentEdge` represents a directed edge in an AI agent's graph, connecting a source node
     * to a target node and facilitating the flow of data through potential transformation or filtering.
     */
    public companion object {
        /**
         * Creates a new instance of the [AgentEdgeBuilder], which is a utility class for constructing
         * instances of [AIAgentEdge].
         *
         * @return A new instance of `AgentEdgeBuilder` to facilitate the creation of `AIAgentEdge` objects.
         */
        @JavaAPI
        @JvmStatic
        public fun builder(): AgentEdgeBuilder = AgentEdgeBuilder()
    }
}
