package ai.koog.agents.ext.shell.shell

/**
 * User's decision on whether to execute a shell command.
 */
public sealed class ShellCommandConfirmation {
    /** Command execution approved. */
    public data object Approved : ShellCommandConfirmation()

    /**
     * Command execution denied with explanation.
     *
     * @property userResponse A free-form user reply when not approving; may be a reason or simply a “no”.
     */
    public data class Denied(val userResponse: String) : ShellCommandConfirmation()
}

/**
 * Strategy for obtaining user confirmation before executing shell commands.
 */
public interface ShellCommandConfirmationHandler {
    /**
     * Requests confirmation to execute a command.
     *
     * @param command Command to execute
     * @param workingDirectory Working directory, or null to use the current directory
     * @return Approval or denial decision
     */
    public suspend fun requestConfirmation(
        command: String,
        workingDirectory: String?
    ): ShellCommandConfirmation
}
