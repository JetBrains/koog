package ai.koog.agents.ext.tool.cli

/**
 * User decision on whether to delegate work to an external AI agent CLI.
 */
public sealed class AIAgentCliConfirmation {
    /**
     * CLI delegation approved.
     */
    public data object Approved : AIAgentCliConfirmation()

    /**
     * CLI delegation denied.
     *
     * @property userResponse Free-form explanation or denial text.
     */
    public data class Denied(val userResponse: String) : AIAgentCliConfirmation()
}

/**
 * Strategy for approving external AI agent CLI delegation.
 */
public fun interface AIAgentCliConfirmationHandler {
    /**
     * Requests approval before starting the external CLI process.
     *
     * @param request Fully built process invocation.
     * @param args Tool arguments supplied by the agent.
     * @return Approval or denial decision.
     */
    public suspend fun requestConfirmation(
        request: AIAgentCliExecutionRequest,
        args: AIAgentCliTool.Args,
    ): AIAgentCliConfirmation
}

/**
 * Confirmation handler that always approves CLI delegation.
 *
 * Use only when the host application already provides an appropriate sandbox or trust boundary.
 */
public class BraveModeAIAgentCliConfirmationHandler : AIAgentCliConfirmationHandler {
    override suspend fun requestConfirmation(
        request: AIAgentCliExecutionRequest,
        args: AIAgentCliTool.Args,
    ): AIAgentCliConfirmation = AIAgentCliConfirmation.Approved
}

/**
 * Console confirmation handler for interactive applications.
 */
public class PrintAIAgentCliConfirmationHandler : AIAgentCliConfirmationHandler {
    override suspend fun requestConfirmation(
        request: AIAgentCliExecutionRequest,
        args: AIAgentCliTool.Args,
    ): AIAgentCliConfirmation {
        println("Agent wants to delegate to ${request.profileId}: ${request.command.joinToString(" ")}")
        request.workingDirectory?.let { println("In: $it") }
        println("Timeout: ${request.timeoutSeconds}s")
        println("Task: ${args.task}")
        print("Confirm (y / n / reason-for-denying): ")

        val userResponse = readln().trim()
        return when (userResponse.lowercase()) {
            "y", "yes" -> AIAgentCliConfirmation.Approved
            else -> AIAgentCliConfirmation.Denied(userResponse)
        }
    }
}
