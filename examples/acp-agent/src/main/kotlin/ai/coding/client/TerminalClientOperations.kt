package ai.coding.client

import com.agentclientprotocol.client.*
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.FileSystemOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.common.TerminalOperations
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

class TerminalClientSessionOperations : ClientSessionOperations, FileSystemOperations, TerminalOperations {
    private val activeTerminals = ConcurrentHashMap<String, Process>()

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        println("Agent requested permissions for tool call: ${toolCall.title}. Choose one of the following options:")
        for ((i, permission) in permissions.withIndex()) {
            println("${i + 1}. ${permission.name}")
        }
        while (true) {
            val read = readln()
            val optionIndex = read.toIntOrNull()
            if (optionIndex != null && optionIndex in permissions.indices) {
                return RequestPermissionResponse(RequestPermissionOutcome.Selected(permissions[optionIndex].optionId), _meta)
            }
            println("Invalid option selected. Try again.")
        }
    }

    override suspend fun notify(
        notification: SessionUpdate,
        _meta: JsonElement?,
    ) {
        println("Agent sent notification:")
        notification.render()
    }

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse {
        val content = Paths.get(path).readText()
        return ReadTextFileResponse(content)
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        Paths.get(path).writeText(content)
        return WriteTextFileResponse()
    }

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: JsonElement?,
    ): CreateTerminalResponse {
        val processBuilder = ProcessBuilder(listOf(command) + args)
        if (cwd != null) {
            processBuilder.directory(File(cwd))
        }
        env.forEach { processBuilder.environment()[it.name] = it.value }

        val process = processBuilder.start()
        val terminalId = UUID.randomUUID().toString()
        activeTerminals[terminalId] = process

        return CreateTerminalResponse(terminalId)
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: JsonElement?,
    ): TerminalOutputResponse {
        val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val output = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout

        return TerminalOutputResponse(output, truncated = false)
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: JsonElement?,
    ): ReleaseTerminalResponse {
        activeTerminals.remove(terminalId)
        return ReleaseTerminalResponse()
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: JsonElement?,
    ): WaitForTerminalExitResponse {
        val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
        val exitCode = process.waitFor()
        return WaitForTerminalExitResponse(exitCode.toUInt())
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: JsonElement?,
    ): KillTerminalCommandResponse {
        val process = activeTerminals[terminalId]
        process?.destroy()
        return KillTerminalCommandResponse()
    }
}

suspend fun CoroutineScope.runTerminalClient(transport: Transport) {
    // Create client-side connection
    val protocol = Protocol(this, transport)
    val client = Client(
        protocol
    )

    logger.info { "Starting agent process..." }

    // Connect to agent and start transport
    protocol.start()

    logger.info { "Connected to agent, initializing..." }

    val agentInfo = client.initialize(
        ClientInfo(
            capabilities = ClientCapabilities(
                fs = FileSystemCapability(
                    readTextFile = true,
                    writeTextFile = true
                ),
                terminal = true
            )
        )
    )
    println("Agent info: $agentInfo")

    println()

    // Create a session
    val session = client.newSession(
        SessionCreationParameters(Paths.get("").absolutePathString(), emptyList())
    ) { session, _ -> TerminalClientSessionOperations() }

    println("=== Session created: ${session.sessionId} ===")
    println("Type your messages below. Use 'exit', 'quit', or Ctrl+C to stop.")
    println("=".repeat(60))
    println()

    try {
        // Start interactive chat loop
        while (true) {
            print("You: ")
            val userInput = readLine()

            // Check for exit conditions
            if (userInput == null || userInput.lowercase() in listOf("exit", "quit", "bye")) {
                println("\n=== Goodbye! ===")
                break
            }

            // Skip empty inputs
            if (userInput.isBlank()) {
                continue
            }

            try {
                session.prompt(listOf(ContentBlock.Text(userInput.trim()))).collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> {
                            event.update.render()
                        }

                        is Event.PromptResponseEvent -> {
                            when (event.response.stopReason) {
                                StopReason.END_TURN -> {
                                    // Normal completion - no action needed
                                }

                                StopReason.MAX_TOKENS -> {
                                    println("\n[Response truncated due to token limit]")
                                }

                                StopReason.MAX_TURN_REQUESTS -> {
                                    println("\n[Turn limit reached]")
                                }

                                StopReason.REFUSAL -> {
                                    println("\n[Agent declined to respond]")
                                }

                                StopReason.CANCELLED -> {
                                    println("\n[Response was cancelled]")
                                }
                            }
                        }
                    }
                }



                println() // Extra newline for readability

            } catch (e: Exception) {
                println("\n[Error: ${e.message}]")
                logger.error(e) { "Error during chat interaction" }
                println()
            }
        }

    } catch (e: Exception) {
        logger.error(e) { "Client error occurred" }
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        logger.info { "ACP client shutting down" }
    }
}