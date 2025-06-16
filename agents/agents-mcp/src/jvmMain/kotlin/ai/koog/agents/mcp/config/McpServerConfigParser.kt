package ai.koog.agents.mcp.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Parses and interprets configuration data for MCP (Message Channel Protocol) servers.
 */
public class McpServerConfigParser {

    internal companion object {
        private val logger = KotlinLogging.logger(McpServerConfigParser::class.qualifiedName ?: "ai.koog.agents.mcp.config.McpClientConfigParser")
    }

    /**
     * Parses the given JSON string to create an instance of `McpServerConfig` based on the configuration details.
     * The method determines the type of the MCP server (e.g., SSE or STDIO) and processes its specific configuration.
     *
     * @param input The JSON string containing the MCP server configuration.
     * @return An instance of `McpServerConfig` representing the parsed MCP server configuration.
     *         Depending on the type field, it may return an `McpServerRemoteConfig` or `McpServerCommandConfig`.
     *         If the type field is omitted, it defaults to `McpServerCommandConfig`.
     */
    public fun parse(input: String): McpServerConfig {

        logger.debug { "Parsing MCP server config from input: $input" }

        val fullConfigJsonObject = Json.parseToJsonElement(input).jsonObject
        val mcpServersJsonObject = fullConfigJsonObject.getValue("mcpServers").jsonObject
        val mcpServerJsonObject = mcpServersJsonObject.entries.first()

        val name = mcpServerJsonObject.key

        val mcpServerConfigJsonObject = mcpServerJsonObject.value.jsonObject

        val typeStringJsonElement = mcpServerConfigJsonObject["type"]
        val type = typeStringJsonElement?.type

        logger.debug { "Check MCP server type support. Type: $type" }

        return when (type) {
            McpServerTransportType.SSE -> { mcpServerConfigJsonObject.parseRemoteParameters(name) }
            McpServerTransportType.STDIO -> { mcpServerConfigJsonObject.parseCommandParameters(name) }
            else -> {
                // There is a usual case when the 'type' field is omitted in MCP configuration.
                // Uses stdio and command configuration by default.
                mcpServerConfigJsonObject.parseCommandParameters(name)
            }
        }

    }

    public fun tryParse(input: String): McpServerConfig? =
        try {
            parse(input)
        }
        catch (t: Throwable) {
            logger.error(t) { "Failed to parse MCP server config" }
            null
        }

    public fun parse(file: Path): McpServerConfig {
        val content = file.readText()
        return parse(content)
    }

    public fun tryParse(file: Path): McpServerConfig? =
        try {
            parse(file)
        }
        catch (t: Throwable) {
            logger.error(t) { "Failed to parse MCP server config" }
            null
        }

    //region Private Methods

    private fun JsonElement.parseCommandParameters(name: String): McpServerCommandConfig {
        val commandStringJsonElement = jsonObject["command"]
            ?: error("Error parsing the mcp configuration with name: $name. 'command' parameter not found.")

        val argsArrayJsonElement = jsonObject["args"]

        val envMapJsonElement = jsonObject["env"]

        return McpServerCommandConfig(
            name = name,
            command = commandStringJsonElement.command,
            args = argsArrayJsonElement?.args ?: emptyList(),
            env = envMapJsonElement?.env ?: emptyMap()
        )
    }

    private fun JsonElement.parseRemoteParameters(name: String): McpServerRemoteConfig {
        val hostStringJsonElement = jsonObject["host"]
            ?: error("Error parsing the mcp configuration with name: $name. 'host' parameter not found")

        val portIntJsonElement = jsonObject["port"]
            ?: error("Error parsing the mcp configuration with name: $name. 'port' parameter not found")

        val protocolStringJsonElement = jsonObject["protocol"]

        return McpServerRemoteConfig(
            name = name,
            host = hostStringJsonElement.host,
            port = portIntJsonElement.port,
            protocol = protocolStringJsonElement?.protocol ?: McpServerRemoteConfig.defaultProtocol
        )
    }

    private val JsonElement.type: McpServerTransportType?
        get() {
            val type = this.jsonPrimitive.content
            return McpServerTransportType.entries.find { it.id == type }
        }

    private val JsonElement.command: String
        get() = this.jsonPrimitive.content

    private val JsonElement.args: List<String>
        get() = this.jsonArray.toList().map { it.jsonPrimitive.content }

    private val JsonElement.env: Map<String, String>
        get() = this.jsonObject.entries.associate { it.key to it.value.jsonPrimitive.content }

    private val JsonElement.host: String
        get() = this.jsonPrimitive.content

    private val JsonElement.port: Int
        get() = this.jsonPrimitive.int

    private val JsonElement.protocol: URLProtocol
        get() {
            val protocolString = this.jsonPrimitive.content
            val protocol = URLProtocol.byName[protocolString]

            logger.debug { "Got protocol: <$protocol> from original value: $protocolString" }
            return protocol ?: error("Error parsing the mcp configuration. Protocol not supported: $protocolString.")
        }

    //endregion Private Methods
}
