@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.utils.Option

public actual class AIAgentEdge<IncomingOutput, OutgoingInput> internal actual constructor(
    public actual val fromNode: AIAgentNodeBase<*, IncomingOutput>,
    public actual val toNode: AIAgentNodeBase<OutgoingInput, *>,
    internal actual val forwardOutput: suspend (context: AIAgentGraphContextBase, output: IncomingOutput) -> Option<OutgoingInput>,
) {

    @Suppress("UNCHECKED_CAST")
    internal actual suspend fun forwardOutputUnsafe(output: Any?, context: AIAgentGraphContextBase): Option<OutgoingInput> =
        forwardOutput(context, output as IncomingOutput)
}
