package ai.koog.protocol.agent.agents.verify

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt

/**
 * Agent that validates or critiques input using LLM with structured output.
 */
public class FlowVerifyAgent(
    override val name: String,
    override val model: String,
    override val config: FlowAgentConfig,
    override val prompt: FlowAgentPrompt?,
    override val parameters: FlowVerifyAgentParameters
) : FlowAgent {

    override val type: FlowAgentKind = FlowAgentKind.VERIFY
}
