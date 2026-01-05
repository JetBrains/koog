@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonElement

@InternalAgentsApi
public class AgentContextData(
    internal val messageHistory: List<Message>,
    internal val nodePath: String,
    @Deprecated("Use lastOutput instead, lastOutput will be removed in future versions")
    internal val lastInput: JsonElement? = null,
    internal val lastOutput: JsonElement? = null,
    internal val rollbackStrategy: RollbackStrategy,
    internal val additionalRollbackActions: suspend (AIAgentContext) -> Unit = {}
) {
    init {
        require(lastInput == null || lastOutput == null) { "`lastInput` and `lastOutput` cannot be both set" }
        require(lastInput != null || lastOutput != null) { "`lastInput` (until 0.6.0) or `lastOutput` (since 0.6.1) must be set" }
    }
}

public enum class RollbackStrategy {
    /**
     * Rollback state of the agent to the last saved state in full.
     * Meaning restore the entire context, including message history and any other stateful data.
     */
    Default,

    /**
     * Rollback only the message history to the last saved state.
     * Agent starts from the first node with saved message history.
     */
    MessageHistoryOnly,
}
