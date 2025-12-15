package ai.koog.agents.testing.agent

import ai.koog.agents.core.agent.execution.AgentExecutionInfo

/**
 * Constructs an execution path for an agent by joining the provided path components using
 * the default path separator.
 *
 * @param parts A variable number of path components that should be joined to create the execution path.
 * @return A string representing the fully constructed execution path.
 */
public fun agentExecutionPath(vararg parts: String): String = parts.joinToString(AgentExecutionInfo.defaultPathSeparator)

/**
 * Constructs an [AgentExecutionInfo] instance from the provided path components.
 */
public fun agentExecutionInfo(vararg parts: String): AgentExecutionInfo {
    var parent: AgentExecutionInfo? = null
    var executionInfo: AgentExecutionInfo? = null

    parts.forEach { partName ->
        val current = AgentExecutionInfo(parent, partName)
        parent = current

        executionInfo = current
    }

    return executionInfo ?: error("At least one path component must be provided.")
}
