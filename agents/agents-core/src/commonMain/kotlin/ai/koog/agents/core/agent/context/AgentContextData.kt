@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.prompt.message.Message

@InternalAgentsApi
public class AgentContextData(
    internal val messageHistory: List<Message>,
    internal val nodeId: String,
    internal val lastInput: Any?
)