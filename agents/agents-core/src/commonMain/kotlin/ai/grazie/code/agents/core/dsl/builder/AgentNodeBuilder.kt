package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Some
import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext
import ai.grazie.code.agents.core.agent.entity.AgentNode
import ai.grazie.code.agents.core.agent.entity.SimpleAgentNode

open class AgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend AgentStageContext.(Input) -> Output
) : BaseBuilder<AgentNode<Input, Output>> {
    lateinit var name: String

    override fun build(): AgentNode<Input, Output> {
        return SimpleAgentNode<Input, Output>(
            name = name,
            execute = execute
        )
    }
}

infix fun <IncomingOutput, OutgoingInput> AgentNode<*, IncomingOutput>.forwardTo(
    otherNode: AgentNode<OutgoingInput, *>
): AgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return AgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}
