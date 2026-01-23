package ai.koog.flow

import ai.koog.agent.FlowAgent
import ai.koog.model.Transition
import ai.koog.tools.FlowTool

/**
 * Simple flow implementation that executes agents following transitions.
 */
public data class SimpleFlow(
    override val id: String,
    override val agents: List<FlowAgent>,
    override val tools: List<FlowTool>,
    override val transitions: List<Transition>
) : Flow {

    override suspend fun run(): String {
        if (agents.isEmpty()) return ""

        val agentsByName = agents.associateBy { "agent.${it.name}" }
        var currentAgentRef = transitions.firstOrNull()?.from
            ?: return agents.first().execute()

        var result = ""
        val visited = mutableSetOf<String>()

        while (currentAgentRef != "__end__" && currentAgentRef !in visited) {
            visited.add(currentAgentRef)

            val agent = agentsByName[currentAgentRef]
                ?: error("Agent not found: $currentAgentRef")

            println("Executing agent: ${agent.name} (${agent.type})")
            result = agent.execute()
            println("  Result: $result")

            // Find the next transition
            val transition = transitions.find { it.from == currentAgentRef }
            currentAgentRef = transition?.to ?: "__end__"
        }

        return result
    }
}
