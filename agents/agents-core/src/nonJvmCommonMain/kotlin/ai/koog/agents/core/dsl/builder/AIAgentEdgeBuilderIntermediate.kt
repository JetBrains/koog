@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.utils.Option

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
public actual open class AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal actual constructor(
    fromNode: AIAgentNodeBase<*, IncomingOutput>,
    toNode: AIAgentNodeBase<OutgoingInput, *>,
    forwardOutputComposition: suspend (AIAgentGraphContextBase, IncomingOutput) -> Option<IntermediateOutput>
) : AIAgentEdgeBuilderIntermediateBase<IncomingOutput, IntermediateOutput, OutgoingInput>(
    fromNode,
    toNode,
    forwardOutputComposition
)
