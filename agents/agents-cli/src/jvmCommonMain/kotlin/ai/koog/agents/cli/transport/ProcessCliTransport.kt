package ai.koog.agents.cli.transport

import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Base class for transports that execute a local [Process].
 */
public abstract class ProcessCliTransport : CliTransport {

    /**
     * Builds the [ProcessBuilder] for execution.
     */
    protected abstract fun buildCommand(
        command: List<String>,
        workspace: String,
        env: Map<String, String> = emptyMap()
    ): List<String>

    override suspend fun checkAvailability(binaryPath: String, workspace: String, timeout: Duration?): CliAvailability =
        checkAvailability(buildCommand(listOf(binaryPath, "--version"), workspace), workspace, timeout)

    override fun execute(
        command: List<String>,
        workspace: String,
        env: Map<String, String>,
        timeout: Duration?
    ): Flow<CliEvent> {
        val fullCommand = buildCommand(command, workspace, env)

        return channelFlow {
            val process = try {
                ProcessBuilder(fullCommand)
                    .directory(File(workspace))
                    .apply { environment().putAll(env) }
                    .redirectErrorStream(false)
                    .start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to start process: ${e.message}" }
                val failed = CliEvent.Failed(e.message ?: e.toString())
                send(failed)
                close(e)
                return@channelFlow
            }

            process.outputStream.close()

            val stdoutJob = launch(Dispatchers.SuitableForIO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { content ->
                        logger.debug { "Process stdout: $content" }
                        send(CliEvent.Stdout(content))
                    }
                }
            }

            val stderrJob = launch(Dispatchers.SuitableForIO) {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { content ->
                        logger.warn { "Process stderr: $content" }
                        send(CliEvent.Stderr(content))
                    }
                }
            }

            val waiter = launch(Dispatchers.SuitableForIO) {
                try {
                    val code = if (timeout != null) {
                        logger.debug { "Waiting for process with timeout: $timeout" }
                        if (withTimeoutOrNull(timeout) { process.waitFor() } == null) {
                            logger.error { "Execution timed out after $timeout. Destroying process." }
                            process.destroy()
                            throw CliTimeoutException(timeout)
                        }
                        process.exitValue()
                    } else {
                        logger.debug { "Waiting for process indefinitely" }
                        process.waitFor()
                    }

                    stdoutJob.join()
                    stderrJob.join()

                    if (code != 0) {
                        logger.warn { "Process exited with non-zero code: $code" }
                    }

                    val exit = CliEvent.Exit(code)
                    send(exit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CliTimeoutException) {
                    send(CliEvent.Failed("Timeout exceeded: ${e.message}"))
                } catch (e: Exception) {
                    logger.error(e) { "Error while waiting for process: ${e.message}" }
                    send(CliEvent.Failed(e.message ?: e.toString()))
                } finally {
                    close()
                }
            }

            awaitClose {
                try {
                    process.destroy()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
                stdoutJob.cancel()
                stderrJob.cancel()
                waiter.cancel()
            }
        }.flowOn(Dispatchers.SuitableForIO)
    }

    protected suspend fun checkAvailability(command: List<String>, workspace: String, timeout: Duration?): CliAvailability = try {
        val exitCode = withContext(Dispatchers.SuitableForIO) {
            val process = ProcessBuilder(command)
                .directory(File(workspace))
                .start()

            val timeout = timeout ?: 1.minutes
            val finished = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

            if (!finished) {
                process.destroy()
                throw CliTimeoutException(timeout)
            }

            process.exitValue()
        }
        if (exitCode == 0) {
            CliAvailable
        } else {
            CliUnavailable("Process exited with code $exitCode")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CliUnavailable(reason = e.message, cause = e)
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
    }
}
