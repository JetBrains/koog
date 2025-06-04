package ai.koog.agents.mcp.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readText

public class McpClientConfigParser {

    public companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.mcp.config.McpClientConfigParser")
    }

    public fun parse(input: String): McpServerConfig {
        val fullConfigJsonObject = Json.parseToJsonElement(input).jsonObject
        val mcpServersJsonObject = fullConfigJsonObject.getValue("mcpServers").jsonObject
        val mcpServerJsonObject = mcpServersJsonObject.entries.first()

        val name = mcpServerJsonObject.key

        val mcpServerConfigJsonObject = mcpServerJsonObject.value.jsonObject

        val typeStringJsonElement = mcpServerConfigJsonObject["type"]

        val commandStringJsonElement = mcpServerConfigJsonObject["command"]
            ?: error("Error parsing the mcp configuration: 'command' parameter not found in the configuration input: $input")

        val argsArrayJsonElement = mcpServerConfigJsonObject["args"]

        val envMapJsonElement = mcpServerConfigJsonObject["env"]

        return McpServerConfig(
            name = name,
            type = typeStringJsonElement?.type,
            command = commandStringJsonElement.command,
            args = argsArrayJsonElement?.args ?: emptyList(),
            env = envMapJsonElement?.env ?: emptyMap()
        )
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

    private val JsonElement.type: McpServerType?
        get() {
            val type = this.jsonPrimitive.content
            return McpServerType.entries.find { it.id == type }
        }

    private val JsonElement.command: String
        get() = this.jsonPrimitive.content

    private val JsonElement.args: List<String>
        get() = this.jsonArray.toList().map { it.jsonPrimitive.content }

    private val JsonElement.env: Map<String, String>
        get() = this.jsonObject.entries.associate { it.key to it.value.jsonPrimitive.content }

    //endregion Private Methods
}
