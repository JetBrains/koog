package ai.grazie.code.prompt.agents


/**
 * Represents the trajectory of an agent's operations, detailing the request, steps taken,
 * and accompanying explanation for the sequence of actions.
 *
 * @property request The initial request or input provided to the agent.
 * @property steps A list of tool steps that outline the agent's processing sequence.
 * @property explanation A textual explanation of the agent's reasoning or decision-making process.
 * @property outcome An outcome that should be observed after the agent execution
 */
data class AgentTrajectory(
    val request: String,
    val steps: List<ToolStep>,
    val explanation: String,
    val outcome: String
) {
    /**
     * Represents a single step performed by a tool within an agent's trajectory.
     *
     * @property tool The identifier or name of the tool used in this step.
     * @property params The parameters provided to the tool during execution.
     * @property reason The reason or intent behind invoking the tool in this step.
     * @property outcome The outcome or result of the tool's execution.
     */
    data class ToolStep(
        val tool: String,
        val params: String,
        val reason: String,
        val outcome: String
    )
}