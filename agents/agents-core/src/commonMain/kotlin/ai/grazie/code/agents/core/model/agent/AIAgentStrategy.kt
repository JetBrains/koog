package ai.grazie.code.agents.core.model.agent

/**
 * Base interface for AI agents.
 *
 * @param Config Compatible config struct. Used in runners to enforce the correct config struct to be passed.
 */
interface AIAgentStrategy<Config : AIAgentConfig> {
    val name: String
}