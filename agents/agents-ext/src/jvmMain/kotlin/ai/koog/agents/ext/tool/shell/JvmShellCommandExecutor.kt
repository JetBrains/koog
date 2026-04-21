package ai.koog.agents.ext.tool.shell

import ai.koog.agents.ext.tool.shell.ShellCommandExecutor.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

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

        try {
            /*
             PLEASE NOTE: the way output is collected potentially allows for race conditions.
             Since output collection job is started AFTER the process has started, it is possible for some events to
             be emitted before the collection is set up, leading to missed events in the collected output.

             The actual impact is likely to be minimal, but this is still a potential bug source.
             Maybe a better collection strategy could be used in the future, like piping to temp files and then reading
             from them. It is tricky and maybe even impossible to implement this fully in memory, since input streams
             are available only once the process has started.
             */
            val stdoutJob = launch {
                process.inputStream.bufferedReader().useLines { lines ->
                    try {
                        lines.forEach { stdoutBuilder.appendLine(it) }
                    } catch (_: IOException) {
                        // Ignore IO exception if the stream is closed and silently stop stream collection
                    }
                }
            }

            val stderrJob = launch {
                process.errorStream.bufferedReader().useLines { lines ->
                    try {
                        lines.forEach { stderrBuilder.appendLine(it) }
                    } catch (_: IOException) {
                        // Ignore IO exception if the stream is closed and silently stop stream collection
                    }
                }
            }

            val isCompleted = withTimeoutOrNull(timeoutSeconds * 1000L) {
                process.onExit().await()
            } != null

            if (!isCompleted) {
                // descendants need to be deleted for windows
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }

            stdoutJob.join()
            stderrJob.join()

            if (!isCompleted) {
                val combinedPartialOutput = buildCombinedOutput(
                    stdoutBuilder.toString().trimEnd(),
                    stderrBuilder.toString().trimEnd(),
                    "Command timed out after $timeoutSeconds seconds"
                )

                ExecutionResult(output = combinedPartialOutput, exitCode = null)
            } else {
                val combinedOutput = buildCombinedOutput(
                    stdoutBuilder.toString().trimEnd(),
                    stderrBuilder.toString().trimEnd()
                )

                ExecutionResult(output = combinedOutput, exitCode = process.exitValue())
            }
        } finally {
            // Kill the process even when canceled, otherwise it keeps running
            if (process.isAlive) {
                // descendants need to be deleted for windows
                process.descendants().forEach { it.destroyForcibly() }
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
