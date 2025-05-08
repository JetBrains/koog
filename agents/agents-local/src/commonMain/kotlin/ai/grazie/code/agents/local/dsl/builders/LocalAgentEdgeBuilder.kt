package ai.grazie.code.agents.local.dsl.builders

import ai.grazie.code.agents.local.utils.Option
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.local.graph.LocalAgentEdge
import ai.grazie.code.agents.local.graph.LocalAgentNode

class LocalAgentEdgeBuilder<IncomingOutput, OutgoingInput> internal constructor(
    private val edgeIntermediateBuilder: LocalAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>,
) : BaseBuilder<LocalAgentEdge<IncomingOutput, OutgoingInput>> {
    override fun build(): LocalAgentEdge<IncomingOutput, OutgoingInput> {
        return LocalAgentEdge(
            toNode = edgeIntermediateBuilder.toNode,
            forwardOutput = edgeIntermediateBuilder.forwardOutputComposition
        )
    }
}

class LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    internal val fromNode: LocalAgentNode<*, IncomingOutput>,
    internal val toNode: LocalAgentNode<OutgoingInput, *>,
    internal val forwardOutputComposition: suspend (LocalAgentStageContext, IncomingOutput) -> Option<IntermediateOutput>
) {
    infix fun onCondition(
        block: suspend LocalAgentStageContext.(output: IntermediateOutput) -> Boolean
    ): LocalAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> {
        return LocalAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .filter { transOutput -> ctx.block(transOutput) }
           },
        )
    }

    infix fun <NewIntermediateOutput> transformed(
        block: suspend LocalAgentStageContext.(IntermediateOutput) -> NewIntermediateOutput
    ): LocalAgentEdgeBuilderIntermediate<IncomingOutput, NewIntermediateOutput, OutgoingInput> {
        return LocalAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                forwardOutputComposition(ctx, output)
                    .map { ctx.block(it) }
            }
        )
    }
}
