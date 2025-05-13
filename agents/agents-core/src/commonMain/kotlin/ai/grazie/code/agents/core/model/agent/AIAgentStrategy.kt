package ai.grazie.code.agents.core.model.agent

/**
 * Base interface for AI agents.
 *
 * @param Config Compatible config struct. Used in runners to enforce the correct config struct to be passed.
 */
public interface AIAgentStrategy<Config : AIAgentConfig> {
    public val name: String
}
