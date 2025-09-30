package ai.koog.agents.ext.tool.shell

/**
 * Brave mode confirmation handler that bypasses user confirmation.
 *
 * Use when you trust the agent to execute commands without prompting.
 */
public class BraveModeConfirmationHandler : ShellCommandConfirmationHandler {
    override suspend fun requestConfirmation(
        command: String,
        workingDirectory: String?
    ): ShellCommandConfirmation = ShellCommandConfirmation.Approved
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
