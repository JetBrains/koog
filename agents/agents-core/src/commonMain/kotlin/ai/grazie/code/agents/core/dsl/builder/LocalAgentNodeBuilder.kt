package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Some
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.SimpleLocalAgentNode

public open class LocalAgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend LocalAgentStageContext.(Input) -> Output
) : BaseBuilder<LocalAgentNode<Input, Output>> {
    public lateinit var name: String

    override fun build(): LocalAgentNode<Input, Output> {
        return SimpleLocalAgentNode(
            name = name,
            execute = execute
        )
    }
}

public infix fun <IncomingOutput, OutgoingInput> LocalAgentNode<*, IncomingOutput>.forwardTo(
    otherNode: LocalAgentNode<OutgoingInput, *>
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return LocalAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}
