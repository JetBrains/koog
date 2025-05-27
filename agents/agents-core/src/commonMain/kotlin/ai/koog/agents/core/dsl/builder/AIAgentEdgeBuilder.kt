package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.utils.Option
import ai.koog.agents.core.agent.entity.AIAgentEdge
import ai.koog.agents.core.agent.entity.AIAgentNodeBase

@DslMarker
@Target(AnnotationTarget.FUNCTION)
public annotation class EdgeTransformation

public class AIAgentEdgeBuilder<IncomingOutput, OutgoingInput> internal constructor(
    private val edgeIntermediateBuilder: AIAgentEdgeBuilderIntermediate<IncomingOutput, OutgoingInput, OutgoingInput>,
) : BaseBuilder<AIAgentEdge<IncomingOutput, OutgoingInput>> {
    override fun build(): AIAgentEdge<IncomingOutput, OutgoingInput> {
        return AIAgentEdge(
            toNode = edgeIntermediateBuilder.toNode,
            forwardOutput = edgeIntermediateBuilder.forwardOutputComposition
        )
    }
}

public class AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal constructor(
    internal val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    internal val toNode: AIAgentNodeBase<OutgoingInput, *>,
    internal val forwardOutputComposition: suspend (AIAgentContextBase, IncomingOutput) -> Option<IntermediateOutput>
) {
    @EdgeTransformation
    public infix fun onCondition(
        block: suspend AIAgentContextBase.(output: IntermediateOutput) -> Boolean
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

    @EdgeTransformation
    public infix fun <NewIntermediateOutput> transformed(
        block: suspend AIAgentContextBase.(IntermediateOutput) -> NewIntermediateOutput
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
