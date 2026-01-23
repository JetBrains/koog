package ai.koog.protocol.agent.agents.react

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt

/**
 * Agent that performs tasks using ReAct (Reasoning and Acting) strategy.
 *
 * ReAct agents alternate between reasoning about the task and executing actions
 * through tool calls, allowing for more deliberate and step-by-step problem solving.
 */
public class FlowReActAgent(
    override val name: String,
    override val model: String,
    override val config: FlowAgentConfig,
    override val prompt: FlowAgentPrompt?,
    override val parameters: FlowReActAgentParameters
) : FlowAgent {

    override val type: FlowAgentKind = FlowAgentKind.REACT
}
