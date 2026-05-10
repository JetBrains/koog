package ai.koog.agents.ext.tool.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/**
 * JVM process executor for [AIAgentCliTool].
 */
public class JvmAIAgentCliExecutor : AIAgentCliExecutor {
    override suspend fun execute(request: AIAgentCliExecutionRequest): AIAgentCliExecutionResult = withContext(Dispatchers.IO) {
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val process = ProcessBuilder(request.command)
            .apply {
                request.workingDirectory?.let { directory(File(it)) }
                environment().putAll(request.environment)
            }
            .start()

        try {
            val stdoutJob = launch {
                process.inputStream.bufferedReader().useLines { lines ->
                    try {
                        lines.forEach { stdoutBuilder.appendLine(it) }
                    } catch (_: IOException) {
                        // The process may close its stream while it is being collected.
                    }
                }
            }

            val stderrJob = launch {
                process.errorStream.bufferedReader().useLines { lines ->
                    try {
                        lines.forEach { stderrBuilder.appendLine(it) }
                    } catch (_: IOException) {
                        // The process may close its stream while it is being collected.
                    }
                }
            }

            val stdinJob = launch {
                try {
                    process.outputStream.bufferedWriter().use { writer ->
                        request.stdin?.let {
                            writer.write(it)
                            writer.flush()
                        }
                    }
                } catch (_: IOException) {
                    // The process may exit or be terminated before stdin is written.
                }
            }

            val isCompleted = withTimeoutOrNull(request.timeoutSeconds * 1000L) {
                process.onExit().await()
            } != null

            if (!isCompleted) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }

            stdinJob.join()
            stdoutJob.join()
            stderrJob.join()

            val output = buildCombinedOutput(
                stdoutBuilder.toString().trimEnd(),
                stderrBuilder.toString().trimEnd(),
                if (isCompleted) null else "CLI process timed out after ${request.timeoutSeconds} seconds"
            )

            AIAgentCliExecutionResult(
                output = output,
                exitCode = if (isCompleted) process.exitValue() else null,
                timedOut = !isCompleted,
            )
        } finally {
            if (process.isAlive) {
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
