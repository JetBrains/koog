package ai.koog.agents.ext.tool.shell

import ai.koog.agents.ext.tool.shell.ShellCommandExecutor.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

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

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val process = ProcessBuilder(shellCommand)
            .apply { workingDirectory?.let { directory(File(it)) } }
            .start()

        val stdoutJob = launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdoutBuilder.appendLine(it) }
            }
        }

        val stderrJob = launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderrBuilder.appendLine(it) }
            }
        }

        try {
            val isCompleted = withTimeoutOrNull(timeoutSeconds * 1000L) {
                process.onExit().await()
            } != null

            stdoutJob.join()
            stderrJob.join()

            if (!isCompleted) {
                process.destroyForcibly()

                val combinedPartialOutput = buildCombinedOutput(
                    stdoutBuilder.toString().trimEnd(),
                    stderrBuilder.toString().trimEnd(),
                    "Command timed out after $timeoutSeconds seconds"
                )
                return@withContext ExecutionResult(output = combinedPartialOutput, exitCode = null)
            }

            val combinedOutput = buildCombinedOutput(
                stdoutBuilder.toString().trimEnd(),
                stderrBuilder.toString().trimEnd()
            )

            return@withContext ExecutionResult(output = combinedOutput, exitCode = process.exitValue())
        } finally {
            // Kill the process even when canceled, otherwise it keeps running
            if (process.isAlive) {
                process.destroyForcibly()
            }
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
