package ai.koog.agents.core.agent.execution

import kotlinx.serialization.Serializable

/**
 * Represents execution information for an agent, including a reference to a parent execution
 * and a name identifying the specific part of the execution.
 *
 * @property parent A reference to the parent `AgentExecutionInfo` instance. This allows the current
 *           object to represent a node in a hierarchical structure of executions. If null, it indicates that
 *           the current instance is the root.
 * @property partName A string representing the name of the current part or segment of the execution.
 */
@Serializable
public data class AgentExecutionInfo(
    public val parent: AgentExecutionInfo?,
    public val partName: String
) {

    /**
     * A companion object for the `AgentExecutionInfo` class that provides utility constants.
     */
    public companion object {

        /**
         * The default separator used to join parts of a path.
         */
        public val defaultPathSeparator: CharSequence = "/"
    }

    /**
     * Constructs a path string representing the sequence of `partName` values from the current
     * `AgentExecutionInfo` instance to the top-most parent, joined by the specified separator.
     *
     * @param separator The string used to separate each part of the path. If null, a default separator is used.
     * @return A string representing the path constructed from `partName` values.
     */
    public fun path(separator: String? = null): String {
        val separator = separator ?: defaultPathSeparator

        return buildList {
            var current: AgentExecutionInfo? = this@AgentExecutionInfo

            while (current != null) {
                add(current.partName)
                current = current.parent
            }
        }.reversed().joinToString(separator)
    }
}
