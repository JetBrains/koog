package ai.koog.protocol.agent.agents.react

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 * Parameters for ReAct agents.
 *
 * ReAct (Reasoning and Acting) agents alternate between reasoning about tasks
 * and executing actions through tool calls.
 *
 * @property task The task description to be executed by the agent
 * @property toolNames Optional list of tool names that the agent can use. If null, all tools are available
 * @property reasoningInterval Number of tool executions between reasoning steps (default: 1)
 */
@Serializable
public data class FlowReActAgentParameters(
    val task: String,
    val toolNames: List<String>? = null,
    val reasoningInterval: Int = 1
) : FlowAgentParameters
