package ai.koog.agents.ext.tool.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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
    ): ShellCommandExecutor.ExecutionResult = withContext(Dispatchers.IO) {
        val shellCommand = if (IS_WINDOWS) {
            listOf("cmd.exe", "/c", command)
        } else {
            listOf("bash", "-c", command)
        }

        val process = ProcessBuilder(shellCommand)
            .apply { workingDirectory?.let { directory(File(it)) } }
            .start()

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val stdoutJob = launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdoutBuilder.append(it).append('\n') }
            }
        }

        val stderrJob = launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderrBuilder.append(it).append('\n') }
            }
        }

        val completed = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            stdoutJob.cancel()
            stderrJob.cancel()

            val partialStdout = stdoutBuilder.toString().replace("\r\n", "\n").trimEnd()
            val partialStderr = stderrBuilder.toString().replace("\r\n", "\n").trimEnd()

            val timeoutMessage = "Command timed out after $timeoutSeconds seconds"

            val combinedOutput = buildString {
                if (partialStdout.isNotEmpty()) appendLine(partialStdout)
                if (partialStderr.isNotEmpty()) appendLine(partialStderr)
                appendLine(timeoutMessage)
            }.trimEnd()

            return@withContext ShellCommandExecutor.ExecutionResult(
                output = combinedOutput,
                exitCode = null
            )
        }

        withTimeout(1.seconds) {
            stdoutJob.join()
            stderrJob.join()
        }

        val stdoutResult = stdoutBuilder.toString().replace("\r\n", "\n").trimEnd()
        val stderrResult = stderrBuilder.toString().replace("\r\n", "\n").trimEnd()

        val combinedOutput = buildString {
            if (stdoutResult.isNotEmpty()) appendLine(stdoutResult)
            if (stderrResult.isNotEmpty()) appendLine(stderrResult)
        }.trimEnd()

        ShellCommandExecutor.ExecutionResult(
            output = combinedOutput,
            exitCode = process.exitValue()
        )
    }
}
