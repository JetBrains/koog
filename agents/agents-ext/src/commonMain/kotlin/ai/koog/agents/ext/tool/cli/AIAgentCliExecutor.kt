package ai.koog.agents.ext.tool.cli

/**
 * Executes an external AI agent CLI process.
 *
 * @see ShellAIAgentCliExecutor
 */
public interface AIAgentCliExecutor {
    /**
     * Runs the CLI process described by [request].
     *
     * @param request Fully built process invocation.
     * @return Captured process output and completion status.
     */
    public suspend fun execute(request: AIAgentCliExecutionRequest): AIAgentCliExecutionResult
}

/**
 * Fully expanded invocation for an external AI agent CLI.
 *
 * The command is represented as an executable plus argument vector rather than a shell string.
 * Implementations should pass it directly to process APIs to avoid shell interpolation.
 *
 * @property profileId Stable profile identifier, for diagnostics.
 * @property executable Executable name or absolute path.
 * @property arguments Command arguments in order.
 * @property stdin Optional text to write to the process standard input.
 * @property workingDirectory Optional directory where the process should run.
 * @property environment Additional environment variables for the process.
 * @property timeoutSeconds Maximum time to wait before terminating the process.
 */
public data class AIAgentCliExecutionRequest(
    val profileId: String,
    val executable: String,
    val arguments: List<String>,
    val stdin: String? = null,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int,
) {
    /**
     * The process command as an argument vector.
     */
    public val command: List<String> = listOf(executable) + arguments
}

/**
 * Result returned by an external AI agent CLI process.
 *
 * @property output Combined process output.
 * @property exitCode Process exit code, or null when the process did not complete normally.
 * @property timedOut True when the process was terminated because [AIAgentCliExecutionRequest.timeoutSeconds] was reached.
 */
public data class AIAgentCliExecutionResult(
    val output: String,
    val exitCode: Int?,
    val timedOut: Boolean = false,
)
