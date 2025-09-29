package ai.koog.agents.ext.tool.shell

import ai.koog.agents.ext.shell.shell.ShellCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

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
     * @return [ExecutionResult] containing combined stdout/stderr output and process exit code
     */
    public override suspend fun execute(
        command: String,
        workingDirectory: String?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            if (IS_WINDOWS) {
                listOf("cmd.exe", "/c", command)
            } else {
                listOf("sh", "-c", command)
            }
        ).apply {
            workingDirectory?.let { directory(File(it)) }
        }.start()

        val stdout = async { process.inputStream.bufferedReader().readText() }
        val stderr = async { process.errorStream.bufferedReader().readText() }

        val exitCode = process.waitFor()

        ExecutionResult(
            output = listOf(stdout.await(), stderr.await())
                .filter { it.isNotEmpty() }
                .joinToString("\n"),
            exitCode = exitCode
        )
    }
}
