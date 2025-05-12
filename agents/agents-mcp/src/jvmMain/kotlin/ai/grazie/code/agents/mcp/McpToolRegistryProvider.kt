package ai.grazie.code.agents.mcp

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.tools.StageTool
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * A tool registry that connects to an MCP server, retrieves tools, and transforms them to the Tool<*, *> framework interface.
 */
class McpToolRegistryProvider {

    companion object {
        const val DEFAULT_MCP_CLIENT_NAME = "mcp-client-cli"
        const val DEFAULT_MCP_CLIENT_VERSION = "1.0.0"
    }

    /**
     * Creates a ToolRegistry with tools from the MCP server.
     */
    fun fromClient(mcpClient: Client, stageName: String = StageTool.DEFAULT_STAGE_NAME): ToolRegistry {
        val sdkTools = runBlocking { mcpClient.listTools() }?.tools ?: emptyList()
        return ToolRegistry {
            stage(stageName) {
                sdkTools.forEach { sdkTool ->
                    println(sdkTool)
                    val toolDescriptor = parseToolDescriptor(sdkTool)
                    println(toolDescriptor)
                    tool(McpTool(mcpClient, toolDescriptor))
                }
            }
        }
    }

    suspend fun fromStdioClient(
        process: Process,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
        stageName: String = StageTool.DEFAULT_STAGE_NAME
    ): ToolRegistry {
        // Setup I/O transport using the process streams
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )

        // Create the MCP client
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        mcpClient.connect(transport)

        return fromClient(mcpClient, stageName)
    }

    suspend fun fromSseClient(
        urlString: String,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
        stageName: String = StageTool.DEFAULT_STAGE_NAME
    ): ToolRegistry {

        val transport = SseClientTransport(
            client = HttpClient { 
                install(SSE)
            },
            urlString = urlString,
        )

        // Create the MCP client
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        mcpClient.connect(transport)

        return fromClient(mcpClient, stageName)
    }


    private fun parseParameterType(element: JsonObject): ToolParameterType? {
        val typeStr = if ("type" in element) {
            element.getValue("type").jsonPrimitive.content
        } else {
            return null
        }

        val type = when (typeStr.lowercase()) {
            "string" -> ToolParameterType.String
            "integer" -> ToolParameterType.Integer
            "number" -> ToolParameterType.Float
            "boolean" -> ToolParameterType.Boolean
            "array" -> {
                val items = if ("items" in element) {
                    element.getValue("items").jsonObject
                } else {
                    error("Array type parameters must have items property")
                }
                val itemType = parseParameterType(items) ?: return null
                ToolParameterType.List(itemsType = itemType)
            }

            "object" -> {
                val properties = if ("properties" in element) {
                    element.getValue("properties").jsonObject
                } else {
                    error("Object type parameters must have properties property")
                }
                ToolParameterType.Object(properties.map { (name, property) ->
                    val description = if ("description" in element) {
                        element.getValue("description").jsonPrimitive.content
                    } else {
                        ""
                    }
                    ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject) ?: return null)
                })
            }

            else -> null
        }

        return type
    }

    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            if (element !is JsonObject) {
                return@mapNotNull null
            }

            val description = if ("description" in element) {
                element.getValue("description").jsonPrimitive.content
            } else {
                ""
            }

            val type = parseParameterType(element) ?: return@mapNotNull null

            ToolParameterDescriptor(
                name = name, description = description, type = type
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
