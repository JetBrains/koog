package ai.grazie.code.agents.local.graph

import ai.grazie.code.agents.core.utils.Option
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext

class LocalAgentEdge<IncomingOutput, OutgoingInput> internal constructor(
    val toNode: LocalAgentNode<OutgoingInput, *>,
    val forwardOutput: suspend (context: LocalAgentStageContext, output: IncomingOutput) -> Option<OutgoingInput>,
) {
    @Suppress("UNCHECKED_CAST")
    internal suspend fun forwardOutputUnsafe(output: Any?, context: LocalAgentStageContext): Option<OutgoingInput> =
        forwardOutput(context, output as IncomingOutput)
}
