package ai.koog.protocol.flow

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.agents.parallel.FlowParallelAgent
import ai.koog.protocol.tool.FlowTool
import ai.koog.protocol.transition.FlowTransition

/**
 * Result of flow validation containing any errors or warnings found.
 */
public data class ValidationResult(
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList()
) {
    public val isValid: Boolean get() = errors.isEmpty()

    public fun requireValid() {
        if (!isValid) {
            val errorMessage = buildString {
                appendLine("Flow validation failed with ${errors.size} error(s):")
                errors.forEachIndexed { index, error ->
                    appendLine("  ${index + 1}. ${error.message}")
                    if (error.suggestion != null) {
                        appendLine("     Suggestion: ${error.suggestion}")
                    }
                }
            }
            error(errorMessage)
        }
    }
}

/**
 * Represents a validation error that prevents flow execution.
 */
public data class ValidationError(
    val message: String,
    val suggestion: String? = null
)

/**
 * Represents a validation warning that doesn't prevent execution but indicates potential issues.
 */
public data class ValidationWarning(
    val message: String
)

/**
 * Validates flow configuration for common errors and potential issues.
 */
public object FlowValidator {

    /**
     * Validates a flow configuration and returns validation result.
     *
     * @param id Flow identifier
     * @param agents List of agents in the flow
     * @param tools List of tools available to agents
     * @param transitions List of transitions between agents
     * @return ValidationResult containing any errors or warnings
     */
    public fun validate(
        id: String,
        agents: List<FlowAgent>,
        tools: List<FlowTool>,
        transitions: List<FlowTransition>
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Basic structure validation
        if (agents.isEmpty()) {
            errors.add(ValidationError(
                "Flow '$id' has no agents defined",
                "Add at least one agent to the flow"
            ))
            // Cannot continue validation without agents
            return ValidationResult(errors, warnings)
        }

        // Validate agent names are unique
        val duplicateNames = agents.groupBy { it.name }
            .filter { it.value.size > 1 }
            .keys

        if (duplicateNames.isNotEmpty()) {
            errors.add(ValidationError(
                "Duplicate agent names found: ${duplicateNames.joinToString()}",
                "Ensure all agent names are unique within the flow"
            ))
        }

        // Validate transitions reference existing agents
        validateTransitionReferences(agents, transitions, errors)

        // Detect unreachable agents
        detectUnreachableAgents(agents, transitions, warnings)

        // Detect cycles in transitions
        detectCycles(agents, transitions, warnings)

        // Validate tool references
        validateToolReferences(agents, tools, errors)

        // Validate parallel agent configurations
        validateParallelAgents(agents, errors)

        return ValidationResult(errors, warnings)
    }

    //region Private Methods

    private fun validateTransitionReferences(
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        errors: MutableList<ValidationError>
    ) {
        val agentNames = agents.map { it.name }.toSet()

        transitions.forEach { transition ->
            if (transition.from !in agentNames) {
                errors.add(ValidationError(
                    "Transition references non-existent 'from' agent: '${transition.from}'",
                    "Available agents: ${agentNames.joinToString()}"
                ))
            }

            // 'to' can be either an agent name or the special __finish__ node
            if (transition.to != "__finish__" && transition.to !in agentNames) {
                errors.add(ValidationError(
                    "Transition references non-existent 'to' agent: '${transition.to}'",
                    "Available agents: ${agentNames.joinToString()}, or use '__finish__' to end the flow"
                ))
            }
        }
    }

    private fun detectUnreachableAgents(
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        warnings: MutableList<ValidationWarning>
    ) {
        if (transitions.isEmpty()) {
            // No transitions means sequential execution - all agents are reachable
            return
        }

        // Find the first agent (one that is not a 'to' target or first in list)
        val firstAgent = FlowUtil.getFirstAgentOrNull(agents, transitions)
        if (firstAgent == null) {
            warnings.add(ValidationWarning("Cannot determine first agent in flow"))
            return
        }

        // Build reachability graph
        val reachable = mutableSetOf(firstAgent.name)
        val queue = mutableListOf(firstAgent.name)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val outgoing = transitions.filter { it.from == current }

            outgoing.forEach { transition ->
                if (transition.to != "__finish__" && transition.to !in reachable) {
                    reachable.add(transition.to)
                    queue.add(transition.to)
                }
            }
        }

        // Report unreachable agents
        val unreachable = agents.map { it.name }.filter { it !in reachable }
        if (unreachable.isNotEmpty()) {
            warnings.add(ValidationWarning(
                "Unreachable agents detected: ${unreachable.joinToString()}. " +
                    "These agents will never execute."
            ))
        }
    }

    private fun detectCycles(
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        warnings: MutableList<ValidationWarning>
    ) {
        if (transitions.isEmpty()) {
            return
        }

        // Build adjacency list
        val graph = mutableMapOf<String, MutableList<String>>()
        agents.forEach { graph[it.name] = mutableListOf() }

        transitions.forEach { transition ->
            if (transition.to != "__finish__") {
                graph[transition.from]?.add(transition.to)
            }
        }

        // DFS to detect cycles
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val cyclesFound = mutableListOf<List<String>>()

        fun dfs(node: String, path: MutableList<String>): Boolean {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            graph[node]?.forEach { neighbor ->
                if (neighbor !in visited) {
                    if (dfs(neighbor, path)) {
                        return true
                    }
                } else if (neighbor in recursionStack) {
                    // Cycle detected - extract cycle from path
                    val cycleStart = path.indexOf(neighbor)
                    val cycle = path.subList(cycleStart, path.size) + neighbor
                    cyclesFound.add(cycle)
                }
            }

            recursionStack.remove(node)
            path.removeAt(path.lastIndex)
            return false
        }

        agents.forEach { agent ->
            if (agent.name !in visited) {
                dfs(agent.name, mutableListOf())
            }
        }

        if (cyclesFound.isNotEmpty()) {
            cyclesFound.forEach { cycle ->
                warnings.add(ValidationWarning(
                    "Cycle detected in flow: ${cycle.joinToString(" -> ")}. " +
                        "Ensure there are exit conditions to prevent infinite loops."
                ))
            }
        }
    }

    private fun validateToolReferences(
        agents: List<FlowAgent>,
        tools: List<FlowTool>,
        errors: MutableList<ValidationError>
    ) {
        // Note: MCP tools are discovered at runtime from MCP servers,
        // so we cannot validate tool name references until the flow is executed.
        // We can only check if agents that reference tools have at least one tool source defined.

        agents.forEach { agent ->
            val requestedTools = when (agent) {
                is ai.koog.protocol.agent.agents.task.FlowTaskAgent -> agent.parameters.toolNames
                is ai.koog.protocol.agent.agents.verify.FlowVerifyAgent -> agent.parameters.toolNames
                is ai.koog.protocol.agent.agents.react.FlowReActAgent -> agent.parameters.toolNames
                else -> null
            }

            // If agent requests specific tools but no tools are configured, warn the user
            if (requestedTools != null && requestedTools.isNotEmpty() && tools.isEmpty()) {
                errors.add(ValidationError(
                    "Agent '${agent.name}' references tools ${requestedTools.joinToString()} but no tool sources are configured",
                    "Add at least one tool definition (MCP or local) to the flow"
                ))
            }
        }
    }

    private fun validateParallelAgents(
        agents: List<FlowAgent>,
        errors: MutableList<ValidationError>
    ) {
        agents.filterIsInstance<FlowParallelAgent>().forEach { parallelAgent ->
            parallelAgent.parameters.agents.forEach { childAgentName ->
                val childAgent = agents.find { it.name == childAgentName }
                if (childAgent == null) {
                    errors.add(ValidationError(
                        "Parallel agent '${parallelAgent.name}' references non-existent child agent: '$childAgentName'",
                        "Ensure all referenced agents are defined in the flow"
                    ))
                } else if (childAgent is FlowParallelAgent) {
                    errors.add(ValidationError(
                        "Parallel agent '${parallelAgent.name}' cannot reference another parallel agent: '$childAgentName'",
                        "Nested parallel execution is not supported"
                    ))
                }
            }

            if (parallelAgent.parameters.agents.isEmpty()) {
                errors.add(ValidationError(
                    "Parallel agent '${parallelAgent.name}' has no child agents defined",
                    "Add at least one agent to the 'agents' parameter"
                ))
            }
        }
    }

    //endregion Private Methods
}
