package ai.grazie.agents.mcp

import ai.grazie.code.agents.core.tools.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * A tool registry that connects to an MCP server, retrieves tools, and transforms them to the Tool<*, *> framework interface.
 */
class MCPToolRegistryProvider {
    /**
     * Creates a ToolRegistry with tools from the MCP server.
     */
    fun fromClient(mcpClient: Client, stageName: String = ToolStage.DEFAULT_STAGE_NAME): ToolRegistry {
        val mcpTools = runBlocking { mcpClient.listTools() }?.tools ?: emptyList()

        return ToolRegistry {
            stage(stageName) {
                mcpTools.forEach { mcpTool ->
                    val toolDescriptor = parseToolDescriptor(mcpTool)
                    tool(MCPTool(mcpClient, toolDescriptor))
                }
            }
        }
    }

    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.map { (name, element) ->
            // The element is a JsonObject that looks like {"type":"string","description":"The address to geocode"}
            val description = if (element is JsonObject && "description" in element) {
                element["description"]?.jsonPrimitive?.content ?: ""
            } else {
                ""
            }

            val typeStr = if (element is JsonObject && "type" in element) {
                element["type"]?.jsonPrimitive?.content ?: "string"
            } else {
                "string"
            }

            val type = when (typeStr.lowercase()) {
                "string" -> ToolParameterType.String
                "integer" -> ToolParameterType.Integer
                "number" -> ToolParameterType.Float
                "boolean" -> ToolParameterType.Boolean
                "array" ->
                else -> ToolParameterType.String // Default to string for unknown types
            }

            ToolParameterDescriptor(
                name = name,
                description = description,
                type = type
            )
        }
    }

    private fun parseToolDescriptor(sdkTool: SDKTool): ToolDescriptor {
        val parameters = parseParameters(sdkTool.inputSchema.properties)
        val requiredParameters = sdkTool.inputSchema.required ?: emptyList()

        return ToolDescriptor(
            name = sdkTool.name,
            description = sdkTool.description ?: "",
            requiredParameters = parameters.filter { requiredParameters.contains(it.name) },
            optionalParameters = parameters.filter { !requiredParameters.contains(it.name) },
        )
    }
}

/**
 * A Tool implementation that calls an MCP tool.
 */
private class MCPTool(
    private val mcpClient: Client,
    override val descriptor: ToolDescriptor
) : Tool<MCPTool.Args, MCPTool.Result>() {

    @Serializable
    data class Args(val arguments: Map<String, Any>) : Tool.Args

    class Result private constructor(private val content: String) : ToolResult {
        override fun toStringDefault(): String = content

        companion object {
            fun create(content: String): Result = Result(content)
        }
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override suspend fun execute(args: Args): Result {
        val result = mcpClient.callTool(
            name = descriptor.name,
            arguments = args.arguments
        )

        val content = (result?.content ?: "No result").toString()
        return Result.create(content)
    }
}
