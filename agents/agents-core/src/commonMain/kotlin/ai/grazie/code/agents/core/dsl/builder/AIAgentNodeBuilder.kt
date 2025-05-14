package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Some
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNode

open class AIAgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend AIAgentStageContextBase.(Input) -> Output
) : BaseBuilder<AIAgentNodeBase<Input, Output>> {
    lateinit var name: String

    override fun build(): AIAgentNodeBase<Input, Output> {
        return AIAgentNode<Input, Output>(
            name = name,
            execute = execute
        )
    }
}

infix fun <IncomingOutput, OutgoingInput> AIAgentNodeBase<*, IncomingOutput>.forwardTo(
    otherNode: AIAgentNodeBase<OutgoingInput, *>
): AIAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return AIAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}
