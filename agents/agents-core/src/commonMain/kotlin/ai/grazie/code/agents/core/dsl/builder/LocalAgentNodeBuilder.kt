package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.utils.Some
import ai.grazie.code.agents.core.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.agent.graph.LocalAgentNode
import ai.grazie.code.agents.core.agent.graph.SimpleLocalAgentNode

open class LocalAgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend LocalAgentStageContext.(Input) -> Output
) : BaseBuilder<LocalAgentNode<Input, Output>> {
    lateinit var name: String

    override fun build(): LocalAgentNode<Input, Output> {
        return SimpleLocalAgentNode<Input, Output>(
            name = name,
            execute = execute
        )
    }
}

infix fun <IncomingOutput, OutgoingInput> LocalAgentNode<*, IncomingOutput>.forwardTo(
    otherNode: LocalAgentNode<OutgoingInput, *>
): LocalAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return LocalAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}
