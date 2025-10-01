package ai.koog.agents.ext.tool.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shell command executor using ProcessBuilder for JVM platforms.
 *
 * @see ShellCommandExecutor
 */
public class JvmShellCommandExecutor : ShellCommandExecutor() {

    private companion object {
        val IS_WINDOWS = System.getProperty("os.name")
            .lowercase()
            .contains("win")
    }

    /**
     * Executes a shell command and returns combined output and exit code.
     *
     * @param command Shell command string to execute
     * @param workingDirectory Working directory, or null to use current directory
     * @param timeoutSeconds Maximum execution time in seconds, or null for no timeout
     * @return [ExecutionResult] containing combined stdout/stderr output and process exit code
     */
    override suspend fun execute(
        command: String,
        workingDirectory: String?,
        timeoutSeconds: Int?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val shellCommand = if (IS_WINDOWS) {
            listOf("cmd.exe", "/c", command)
        } else {
            listOf("sh", "-c", command)
        }

        val process = ProcessBuilder(shellCommand).apply { workingDirectory?.let { directory(File(it)) } }.start()

        val stdout = async { process.inputStream.bufferedReader().readText() }
        val stderr = async { process.errorStream.bufferedReader().readText() }

        val completed = if (timeoutSeconds != null) {
            process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } else {
            process.waitFor()
            true
        }

        if (!completed) {
            process.destroyForcibly()
            stdout.cancel()
            stderr.cancel()

            val partialStdout = stdout.takeIf { it.isCompleted }?.getCompleted().orEmpty()
            val partialStderr = stderr.takeIf { it.isCompleted }?.getCompleted().orEmpty()

            val timeoutMessage = "Command timed out after $timeoutSeconds seconds"

            val combinedOutput = buildString {
                if (partialStdout.isNotEmpty()) appendWithNewline(partialStdout)
                if (partialStderr.isNotEmpty()) appendWithNewline(partialStderr)
                append(timeoutMessage)
            }

            return@withContext ExecutionResult(
                output = combinedOutput,
                exitCode = null
            )
        }

        val stdoutResult = stdout.await()
        val stderrResult = stderr.await()

        val combinedOutput = buildString {
            if (stdoutResult.isNotEmpty()) appendWithNewline(stdoutResult)
            if (stderrResult.isNotEmpty()) appendWithNewline(stderrResult)
        }

        ExecutionResult(
            output = combinedOutput,
            exitCode = process.exitValue()
        )
    }
}
