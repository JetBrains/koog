package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Option
import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext
import ai.grazie.code.agents.core.agent.entity.AgentEdge
import ai.grazie.code.agents.core.agent.entity.AgentNode

class AgentEdgeBuilder<IncomingOutput, OutgoingInput> internal constructor(
    private val edgeIntermediateBuilder: AgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>,
) : BaseBuilder<AgentEdge<IncomingOutput, OutgoingInput>> {
    override fun build(): AgentEdge<IncomingOutput, OutgoingInput> {
        return AgentEdge(
            toNode = edgeIntermediateBuilder.toNode,
            forwardOutput = edgeIntermediateBuilder.forwardOutputComposition
        )
    }
}

class AgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    internal val fromNode: AgentNode<*, IncomingOutput>,
    internal val toNode: AgentNode<OutgoingInput, *>,
    internal val forwardOutputComposition: suspend (AgentStageContext, IncomingOutput) -> Option<IntermediateOutput>
) {
    infix fun onCondition(
        block: suspend AgentStageContext.(output: IntermediateOutput) -> Boolean
    ): AgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> {
        return AgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .filter { transOutput -> ctx.block(transOutput) }
           },
        )
    }

    infix fun <NewIntermediateOutput> transformed(
        block: suspend AgentStageContext.(IntermediateOutput) -> NewIntermediateOutput
    ): AgentEdgeBuilderIntermediate<IncomingOutput, NewIntermediateOutput, OutgoingInput> {
        return AgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .map { ctx.block(it) }
            }
        )
    }
}
