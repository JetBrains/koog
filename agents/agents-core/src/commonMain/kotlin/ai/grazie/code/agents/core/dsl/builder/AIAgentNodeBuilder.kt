package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Some
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNode

public open class AIAgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend AIAgentStageContextBase.(Input) -> Output
) : BaseBuilder<AIAgentNodeBase<Input, Output>> {
    public lateinit var name: String

    override fun build(): AIAgentNodeBase<Input, Output> {
        return AIAgentNode(
            name = name,
            execute = execute
        )
    }
}

public infix fun <IncomingOutput, OutgoingInput> AIAgentNodeBase<*, IncomingOutput>.forwardTo(
    otherNode: AIAgentNodeBase<OutgoingInput, *>
): AIAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return AIAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}
