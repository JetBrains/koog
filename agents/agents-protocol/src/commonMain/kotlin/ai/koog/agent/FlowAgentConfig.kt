package ai.koog.agent

/**
 * TODO: Update this code
 */
public data class FlowAgentConfig(
    public val model: String? = null,
    public val temperature: Double? = null,
    public val maxIterations: Int? = null,
    public val maxTokens: Int? = null,
    public val topP: Double? = null,
    public val toolChoice: ToolChoiceKind? = null,
    public val speculation: String? = null,
) {

    /**
     *
     */
    public companion object {

        private const val DEFAULT_MODEL = "openai/gpt-3.5-turbo"
    }
}
