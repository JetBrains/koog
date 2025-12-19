package ai.koog.agents.core.agent.execution

/**
 * The default separator used to join parts of an agent's execution path.
 */
public const val DEFAULT_AGENT_PATH_SEPARATOR: String = "/"

/**
 * Joins the given parts into a single path string using the specified separator.
 *
 * @param parts The parts to join into a path.
 * @param separator The separator to use between parts. Defaults to [DEFAULT_AGENT_PATH_SEPARATOR].
 * @return A string representing the joined path.
 */
public fun path(vararg parts: String, separator: String = DEFAULT_AGENT_PATH_SEPARATOR): String {
    return parts.joinToString(separator)
}
