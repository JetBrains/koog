package ai.koog.agents.ext.tool.shell

/**
 * Shell command executor using platform-specific shells (cmd.exe on Windows, sh on Unix).
 */
public interface ShellCommandExecutor {
    /**
     * Executes a command and captures what it prints.
     *
     * @param command Command string (e.g., "ls -la | grep txt")
     * @param workingDirectory Working directory, or null to use the current directory
     * @param timeoutSeconds Maximum execution time in seconds, or null for no timeout
     * @return Output and exit code
     */
    public suspend fun execute(command: String, workingDirectory: String?, timeoutSeconds: Int): ExecutionResult

    /**
     * Command execution result.
     *
     * @property output All text printed by the command (both success and error messages)
     * @property exitCode Process exit code (0 = success), or null if the process was interrupted or timed out
     */
    public data class ExecutionResult(
        val output: String,
        val exitCode: Int?
    )
}
