package ai.koog.agents.ext.tool.shell

import ai.koog.agents.ext.tool.shell.ShellCommandExecutor.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shell command executor using ProcessBuilder for JVM platforms.
 *
 * @see ShellCommandExecutor
 */
public class JvmShellCommandExecutor : ShellCommandExecutor {

    private companion object {
        val IS_WINDOWS = System.getProperty("os.name")
            .lowercase()
            .contains("win")
    }

    /**
     * Executes a shell command and returns combined output and exit code.
     *
     * @param command Shell command string to execute
     * @param workingDirectory Working directory, or null to use the current directory
     * @param timeoutSeconds Maximum execution time in seconds
     * @return [ExecutionResult] containing combined stdout/stderr output and process exit code
     */
    override suspend fun execute(
        command: String,
        workingDirectory: String?,
        timeoutSeconds: Int
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val shellCommand = if (IS_WINDOWS) {
            val systemRoot = System.getenv("SystemRoot")
                ?: System.getenv("WINDIR")
                ?: "C:\\Windows"
            listOf("$systemRoot\\System32\\cmd.exe", "/c", command)
        } else {
            listOf("/bin/bash", "-c", command)
        }

        val process = ProcessBuilder(shellCommand)
            .apply { workingDirectory?.let { directory(File(it)) } }
            .start()

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val stdoutJob = launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdoutBuilder.appendLine(it) }
            }
        }

        val stderrJob = launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderrBuilder.appendLine(it) }
            }
        }

        try {
            val completed = runInterruptible {
                process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            }

            if (!completed) {
                val combinedPartialOutput = buildCombinedOutput(
                    stdoutBuilder.toString().trimEnd(),
                    stderrBuilder.toString().trimEnd(),
                    "Command timed out after $timeoutSeconds seconds"
                )
                return@withContext ExecutionResult(output = combinedPartialOutput, exitCode = null)
            }

            stdoutJob.join()
            stderrJob.join()

            val combinedOutput = buildCombinedOutput(
                stdoutBuilder.toString().trimEnd(),
                stderrBuilder.toString().trimEnd()
            )

            return@withContext ExecutionResult(output = combinedOutput, exitCode = process.exitValue())
        } finally {
            process.destroyForcibly()
            stdoutJob.cancel()
            stderrJob.cancel()
        }
    }

    private fun buildCombinedOutput(stdout: String, stderr: String, message: String? = null): String {
        return buildString {
            if (stdout.isNotEmpty()) appendLine(stdout)
            if (stderr.isNotEmpty()) appendLine(stderr)
            message?.let { appendLine(it) }
        }.trimEnd()
    }
}
