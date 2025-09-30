package ai.koog.agents.ext.shell.shell

/**
 * Confirmation handler that always approves commands.
 */
public class AlwaysApproveConfirmationHandler : ShellCommandConfirmationHandler {
    override suspend fun requestConfirmation(
        command: String,
        workingDirectory: String?
    ): ShellCommandConfirmation = ShellCommandConfirmation.Approved
}

/**
 * Confirmation handler that always denies commands.
 */
public class AlwaysDenyConfirmationHandler : ShellCommandConfirmationHandler {
    override suspend fun requestConfirmation(
        command: String,
        workingDirectory: String?
    ): ShellCommandConfirmation = ShellCommandConfirmation.Denied("User not available, all commands will be denied")
}

/**
 * Confirmation handler that prompts user via console input.
 *
 * Prints command details and waits for y/yes to approve, any other input denies.
 */
public class PrintShellCommandConfirmationHandler : ShellCommandConfirmationHandler {
    override suspend fun requestConfirmation(
        command: String,
        workingDirectory: String?
    ): ShellCommandConfirmation {
        println("Agent wants to execute: $command")
        workingDirectory?.let { println("In: $it") }
        print("Confirm (y / n / reason-for-denying): ")

        val userResponse = readln().lowercase()
        return when (userResponse) {
            "y", "yes" -> ShellCommandConfirmation.Approved
            else -> ShellCommandConfirmation.Denied(userResponse)
        }
    }
}
