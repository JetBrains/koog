package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.utils.Option
import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext

class AgentEdge<IncomingOutput, OutgoingInput> internal constructor(
    val toNode: AgentNode<OutgoingInput, *>,
    internal val forwardOutput: suspend (context: AgentStageContext, output: IncomingOutput) -> Option<OutgoingInput>,
) {
    @Suppress("UNCHECKED_CAST")
    internal suspend fun forwardOutputUnsafe(output: Any?, context: AgentStageContext): Option<OutgoingInput> =
        forwardOutput(context, output as IncomingOutput)
}
