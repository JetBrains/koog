package ai.koog.protocol.agent.agents.task

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt

/**
 *
 */
public class FlowTaskAgent(
    override val name: String,
    override val model: String,
    override val config: FlowAgentConfig,
    override val prompt: FlowAgentPrompt?,
    override val parameters: FlowTaskAgentParameters
) : FlowAgent {

    override val type: FlowAgentKind = FlowAgentKind.TASK
}
