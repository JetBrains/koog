package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Option
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.agent.entity.AIAgentEdge
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase

class AIAgentEdgeBuilder<IncomingOutput, OutgoingInput> internal constructor(
    private val edgeIntermediateBuilder: AIAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>,
) : BaseBuilder<AIAgentEdge<IncomingOutput, OutgoingInput>> {
    override fun build(): AIAgentEdge<IncomingOutput, OutgoingInput> {
        return AIAgentEdge(
            toNode = edgeIntermediateBuilder.toNode,
            forwardOutput = edgeIntermediateBuilder.forwardOutputComposition
        )
    }
}

class AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    internal val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    internal val toNode: AIAgentNodeBase<OutgoingInput, *>,
    internal val forwardOutputComposition: suspend (AIAgentStageContextBase, IncomingOutput) -> Option<IntermediateOutput>
) {
    infix fun onCondition(
        block: suspend AIAgentStageContextBase.(output: IntermediateOutput) -> Boolean
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

    infix fun <NewIntermediateOutput> transformed(
        block: suspend AIAgentStageContextBase.(IntermediateOutput) -> NewIntermediateOutput
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
