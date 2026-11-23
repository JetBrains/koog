package ai.codereview.app

import ai.codereview.client.runCodeReviewClient
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Subprocess-based code-review app: spawns the agent as a child process via
 * the `AGENT_PATH` env var, then talks ACP over the child's stdin/stdout.
 *
 * Setup:
 *   ./gradlew installDist
 *   # AGENT_PATH points at the installed agent launcher
 *
 * Usage:
 *   AGENT_PATH=/path/to/agent ./gradlew runCodeReviewProcessApp
 *   AGENT_PATH=/path/to/agent ./gradlew runCodeReviewProcessApp --args="main"
 */
suspend fun main(args: Array<String>): Unit = coroutineScope {
    logger.info { "Starting Code Review process app" }
    val baseRef = args.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "develop"
    val agentPath = System.getenv("AGENT_PATH")
        ?: error("AGENT_PATH env variable is not set")

    val process = ProcessBuilder(agentPath)
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val clientTransport = StdioTransport(
        parentScope = this,
        ioDispatcher = Dispatchers.IO,
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered(),
    )

    try {
        runCodeReviewClient(clientTransport, baseRef)
    } finally {
        clientTransport.close()
        process.destroy()
    }
    exitProcess(0)
}
