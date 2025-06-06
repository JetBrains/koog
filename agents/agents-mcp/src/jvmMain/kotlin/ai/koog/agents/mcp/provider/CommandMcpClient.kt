package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.config.McpServerCommandConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay

public abstract class CommandBaseMcpClient(
    override val config: McpServerCommandConfig,
    public val type: CommandMcpClientType,
    override val name: String = DEFAULT_MCP_CLIENT_NAME,
    override val version: String = DEFAULT_MCP_CLIENT_VERSION,
) : McpClient {

    public companion object {

        private val logger = KotlinLogging.logger("ai.koog.agents.mcp.provider.CommandBaseMcpClient")

        /**
         * Default name for the MCP client when connecting to an MCP server.
         */
        public const val DEFAULT_MCP_CLIENT_NAME: String = "mcp-client-cli"

        /**
         * Default version for the MCP client when connecting to an MCP server.
         */
        public const val DEFAULT_MCP_CLIENT_VERSION: String = "1.0.0"
    }

    private var _client: Client? = null

    override val client: Client
        get() = _client ?: throw IllegalStateException("MCP client is not connected")

    private var _process: Process? = null


    override suspend fun connect() {

        val process = startProcess().also { _process = it }

        val transport = McpToolRegistryProvider.defaultStdioTransport(process)

        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        mcpClient.connect(transport).also { _client = mcpClient }
    }

    override fun getTools(): List<McpTool> {
        client.listTools()
    }

    override suspend fun close() {
        if (_client != null) {
            _client?.close()
        }

        if (_process != null) {
            _process?.destroy()
        }
    }

    //region Private Methods

    private suspend fun startProcess(): Process {
        val processBuilder = ProcessBuilder(config.command)

        if (config.args.isNotEmpty()) {
            processBuilder.command().addAll(config.args)
        }

        if (config.env.isNotEmpty()) {
            processBuilder.environment().putAll(config.env)
        }

        val process = processBuilder.start()

        // TODO: Replace with a proper check for a process to start and make necessary preparations.
        delay(2000)

        return process
    }

    private suspend fun tryStartProcess(): Process? {
        try {
            return startProcess()
        }
        catch (t: Throwable) {
            logger.error(t) { "Failed to start MCP server process" }
            return null
        }
    }

    //endregion Private Methods
}