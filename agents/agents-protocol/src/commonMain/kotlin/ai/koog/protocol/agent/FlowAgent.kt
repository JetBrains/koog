package ai.koog.protocol.agent

/**
 *
 */
public interface FlowAgent {

    /**
     * Agent identifier (maps to "name" in JSON).
     */
    public val name: String

    /**
     * Agent type (maps to "type" in JSON).
     */
    public val type: FlowAgentKind

    /**
     * LLM model to use for this agent.
     */
    public val model: String

    /**
     * Agent configuration.
     */
    public val config: FlowAgentConfig

    /**
     * Agent prompt configuration.
     */
    public val prompt: FlowAgentPrompt?

    /**
     * Agent-specific parameters.
     */
    public val parameters: FlowAgentParameters
}
