package ai.koog.protocol.agent.agents.transform

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt

/**
 * Agent that transforms input data without LLM interaction.
 */
public data class FlowInputTransformAgent(
    override val name: String,
    override val model: String,
    override val config: FlowAgentConfig,
    override val prompt: FlowAgentPrompt?,
    override val parameters: FlowTransformParameters
) : FlowAgent {

    override val type: FlowAgentKind = FlowAgentKind.TRANSFORM
}
