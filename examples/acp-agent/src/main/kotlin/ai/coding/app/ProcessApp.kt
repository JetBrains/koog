package ai.coding.app

import ai.coding.client.runTerminalClient
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = KotlinLogging.logger {}

suspend fun main() = coroutineScope {
    logger.info { "Starting ACP App" }
    val token = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY env variable is not set")
    val agentPath = System.getenv("AGENT_PATH") ?: error("AGENT_PATH env variable is not set")

    val process =
        ProcessBuilder(agentPath)
            .apply {
                environment()["OPENAI_API_KEY"] = token
            }
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()

    val stdin = process.outputStream.asSink().buffered()
    val stdout = process.inputStream.asSource().buffered()

    val clientTransport = StdioTransport(
        parentScope = this,
        ioDispatcher = Dispatchers.IO,
        input = stdout,
        output = stdin
    )

    try {
        runTerminalClient(clientTransport)
    } finally {
        logger.info { "ACP app shutting down" }
        clientTransport.close()
    }
}