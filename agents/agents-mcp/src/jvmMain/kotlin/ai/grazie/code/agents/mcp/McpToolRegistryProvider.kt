package ai.grazie.code.agents.mcp

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.tools.ToolStage.Companion.DEFAULT_STAGE_NAME
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * A provider for creating tool registries that connect to Model Context Protocol (MCP) servers.
 *
 * This class facilitates the integration of MCP tools into the agent framework by:
 * 1. Connecting to MCP servers through various transport mechanisms (stdio, SSE)
 * 2. Retrieving available tools from the MCP server
 * 3. Transforming MCP tools into the agent framework's Tool interface
 * 4. Registering the transformed tools in a ToolRegistry
 */
object McpToolRegistryProvider {
    /**
     * Default name for the MCP client when connecting to an MCP server.
     */
    const val DEFAULT_MCP_CLIENT_NAME = "mcp-client-cli"

    /**
     * Default version for the MCP client when connecting to an MCP server.
     */
    const val DEFAULT_MCP_CLIENT_VERSION = "1.0.0"


    /**
     * Creates a default standard input/output transport for a provided process.
     *
     * @param process The process whose input and output streams will be used for communication.
     * @return A `StdioClientTransport` configured to communicate with the process using its standard input and output.
     */
    fun defaultStdioTransport(process: Process): StdioClientTransport {
        return StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
    }

    /**
     * Creates a default server-sent events (SSE) transport from a provided URL.
     *
     * @param url The URL to be used for establishing an SSE connection.
     * @return An instance of SseClientTransport configured with the given URL.
     */
    fun defaultSseTransport(url: String): SseClientTransport {
        // Setup SSE transport using the HTTP client
        return SseClientTransport(
            client = HttpClient {
                install(SSE)
            },
            urlString = url,
        )
    }

    /**
     * Creates a ToolRegistry with tools from an existing MCP client.
     *
     * This method retrieves all available tools from the MCP server using the provided client,
     * transforms them into the agent framework's Tool interface, and registers them in a ToolRegistry.
     *
     * @param mcpClient The MCP client connected to an MCP server.
     * @param stageName The name of the stage in which to register the tools.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    fun fromClient(mcpClient: Client, stageName: String = DEFAULT_STAGE_NAME): ToolRegistry {
        val sdkTools = runBlocking { mcpClient.listTools() }?.tools.orEmpty()
        return ToolRegistry {
            stage(stageName) {
                sdkTools.forEach { sdkTool ->
                    val toolDescriptor = parseToolDescriptor(sdkTool)
                    tool(McpTool(mcpClient, toolDescriptor))
                }
            }
        }
    }

    /**
     * Creates a ToolRegistry with tools from an MCP server using provided transport for communication.
     *
     * This method establishes a connection to an MCP server through provided transport.
     * It's typically used when the MCP server is running as a separate process (e.g., a Docker container or a CLI tool).
     *
     * @param transport The transport to use.
     * @param name The name of the MCP client.
     * @param version The version of the MCP client.
     * @param stageName The name of the stage in which to register the tools.
     * @return A ToolRegistry containing all tools from the MCP server.
     */
    suspend fun fromTransport(
        transport: Transport,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION,
        stageName: String = DEFAULT_STAGE_NAME
    ): ToolRegistry {
        // Create the MCP client
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        // Connect to the MCP server
        mcpClient.connect(transport)

        return fromClient(mcpClient, stageName)
    }

    /**
     * Parses a JSON object representing a parameter type into a ToolParameterType.
     *
     * This method converts JSON Schema type definitions into the corresponding ToolParameterType.
     * It supports primitive types (string, integer, number, boolean) as well as complex types (array, object).
     *
     * @param element The JSON object representing the parameter type.
     * @return The corresponding ToolParameterType
     */
    private fun parseParameterType(element: JsonObject): ToolParameterType {
        // Extract the type string from the JSON object
        val typeStr = if ("type" in element) {
            element.getValue("type").jsonPrimitive.content
        } else {
            throw IllegalArgumentException("Parameter type must have type property")
        }

        // Convert the type string to a ToolParameterType
        return when (typeStr.lowercase()) {
            // Primitive types
            "string" -> ToolParameterType.String
            "integer" -> ToolParameterType.Integer
            "number" -> ToolParameterType.Float
            "boolean" -> ToolParameterType.Boolean

            // Array type
            "array" -> {
                val items = if ("items" in element) {
                    element.getValue("items").jsonObject
                } else {
                    throw IllegalArgumentException("Array type parameters must have items property")
                }
                val itemType = parseParameterType(items)

                ToolParameterType.List(itemsType = itemType)
            }

            // Object type
            "object" -> {
                val properties = if ("properties" in element) {
                    element.getValue("properties").jsonObject
                } else {
                    throw IllegalArgumentException("Object type parameters must have properties property")
                }

                ToolParameterType.Object(properties.map { (name, property) ->
                    val description = element["description"]?.jsonPrimitive?.content.orEmpty()
                    ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject))
                })
            }

            // Unsupported type
            else -> throw IllegalArgumentException("Unsupported parameter type: $typeStr")
        }
    }

    /**
     * Parses a JSON object representing a set of parameters into a list of ToolParameterDescriptor objects.
     *
     * This method extracts parameter information (name, description, type) from a JSON Schema properties object.
     *
     * @param properties The JSON object representing the parameters.
     * @return A list of ToolParameterDescriptor objects.
     */
    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            require(element is JsonObject) { "Parameter $name must be a JSON object" }

            // Extract description from the element
            val description = element["description"]?.jsonPrimitive?.content.orEmpty()

            // Parse the parameter type
            val type = parseParameterType(element)

            // Create a ToolParameterDescriptor
            ToolParameterDescriptor(
                name = name, description = description, type = type
            )
        }
    }

    /**
     * Parses an MCP SDK Tool into a ToolDescriptor.
     *
     * This method extracts tool information (name, description, parameters) from an MCP SDK Tool
     * and converts it into a ToolDescriptor that can be used by the agent framework.
     *
     * @param sdkTool The MCP SDK Tool to parse.
     * @return A ToolDescriptor representing the MCP tool.
     */
    private fun parseToolDescriptor(sdkTool: SDKTool): ToolDescriptor {
        // Parse all parameters from the input schema
        val parameters = parseParameters(sdkTool.inputSchema.properties)

        // Get the list of required parameters
        val requiredParameters = sdkTool.inputSchema.required ?: emptyList()

        // Create a ToolDescriptor
        return ToolDescriptor(
            name = sdkTool.name,
            description = sdkTool.description.orEmpty(),
            requiredParameters = parameters.filter { requiredParameters.contains(it.name) },
            optionalParameters = parameters.filter { !requiredParameters.contains(it.name) },
        )
    }
}
