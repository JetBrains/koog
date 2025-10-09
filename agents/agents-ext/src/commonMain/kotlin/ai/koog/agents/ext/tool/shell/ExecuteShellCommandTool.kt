package ai.koog.agents.ext.tool.shell

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes shell commands with user approval and automatic timeout.
 *
 * Before running any command, asks the user for confirmation. If approved, runs the command
 * and captures everything it prints. If the command takes too long, cancels it automatically.
 *
 * @property executor Platform-specific command executor (handles cmd.exe on Windows, sh on Unix)
 * @property confirmationHandler Asks the user whether to allow command execution
 */
public class ExecuteShellCommandTool(
    private val executor: ShellCommandExecutor,
    private val confirmationHandler: ShellCommandConfirmationHandler
) : Tool<ExecuteShellCommandTool.Args, ExecuteShellCommandTool.Result>() {

    /**
     * Parameters for running a shell command.
     *
     * @property command The command to run (e.g., "ls -la", "git status", "npm install")
     * @property workingDirectory Directory to run the command in, or null to use the current directory
     * @property timeoutSeconds Cancel the command if it runs longer than this many seconds (default: 60)
     */
    @Serializable
    public data class Args(
        @property:LLMDescription(
            "The exact shell command line to execute." +
                "- Examples: 'git status', './gradlew assemble', 'ls -la'" +
                "- Each call runs in a new isolated shell, so directory changes (like `cd`) do NOT persist. Use workingDirectory instead of cd."
        )
        val command: String,
        @property:LLMDescription(
            "Maximum execution time, in seconds. " +
                "Commands that exceed this limit are terminated; " +
                "commands that finish sooner return immediately. " +
                "Choose a value that balances long-running work (to avoid cutting it off near completion) " +
                "against the risk of hangs, infinite loops, or waiting indefinitely."
        )
        val timeoutSeconds: Int,
        @property:LLMDescription(
            "An absolute filesystem path where the command runs. Must exist and be accessible; otherwise execution will fail immediately." +
                "Optional. Default: uses the current working directory when null."
        )
        val workingDirectory: String? = null
    )

    /**
     * Result of attempting to run a command.
     *
     * @property command The command string that was run (or denied)
     * @property exitCode The exit code returned by the process, or null if the command never started
     * @property output Everything the command printed to the screen, or an explanation if it didn't run (e.g., "Command timed out", "denied by user")
     */
    @Serializable
    public data class Result(
        val command: String,
        val exitCode: Int?,
        val output: String
    ) {
        /**
         * Formats this result like a terminal session.
         *
         * Returns a multi-line string showing:
         * - The command that was run
         * - Everything it printed, or "(no output)" if the command succeeded but printed nothing
         * - The exit code (omitted if the command was denied or timed out)
         *
         * @return Formatted string ready for display or logging
         */
        public fun textForLLM(): String = buildString {
            appendLine("Command: $command")
            if (output.isNotEmpty()) {
                appendLine(output)
            } else if (exitCode != null) {
                appendLine("(no output)")
            }
            exitCode?.let {
                appendLine("Exit code: $it")
            }
        }.trimEnd()
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val resultSerializer: KSerializer<Result> = Result.serializer()
    override val name: String = "__execute_shell_command__"
    override val description: String = """
        Executes a shell command.  
        Depending on configuration, the command may run immediately or ask the user before running.  
        A working directory and timeout can be provided. If a timeout is reached, any available output is included.  
        Returns everything the command printed and the exit code, or a message if it was not run or did not finish.
    """.trimIndent()

    /**
     * Runs a command after asking the user for permission.
     *
     * First asks the confirmation handler whether to allow the command. If approved, executes it
     * with the specified timeout. If denied, timed out, or crashes, returns a result explaining
     * what happened.
     *
     * @param args The command string, timeout duration, optional working directory
     * @return Result containing the command output and exit code, or an error message explaining why it didn't run
     * @throws CancellationException if canceled while the command is executing
     **/
    override suspend fun execute(args: Args): Result = when (
        val confirmation = confirmationHandler.requestConfirmation(args)
    ) {
        is ShellCommandConfirmation.Approved -> try {
            val result = executor.execute(args.command, args.workingDirectory, args.timeoutSeconds)
            Result(args.command, result.exitCode, result.output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(args.command, null, "Failed to execute command: ${e.message}")
        }

        is ShellCommandConfirmation.Denied ->
            Result(args.command, null, "Command execution denied with user response: ${confirmation.userResponse}")
    }
}
