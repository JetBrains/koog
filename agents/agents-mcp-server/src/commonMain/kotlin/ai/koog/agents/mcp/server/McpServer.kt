package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import io.modelcontextprotocol.kotlin.sdk.types.Tool as SdkTool

/**
 * Supported MCP server transport types for the Ktor-based [startMcpServer].
 *
 * For stdio transport use the platform-specific `startStdioMcpServer(tools)` entry point.
 */
public enum class McpServerTransportType {
    /** Streamable HTTP transport. */
    StreamableHttp,

    /** SSE transport. */
    SSE,
}

/**
 * Starts a new MCP server with the given [tools] on the specified [port] and [host].
 * Defaults to Streamable HTTP transport.
 *
 * @param factory The Ktor application engine factory to use (e.g., CIO, Netty).
 * @param tools The tools to expose via the MCP server.
 * @param port The port to listen on.
 * @param host The host to bind to.
 * @param transport The transport type to use.
 * @return The MCP [Server].
 */
public suspend fun startMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    tools: ToolRegistry,
    port: Int = 3000,
    host: String = "localhost",
    transport: McpServerTransportType = McpServerTransportType.StreamableHttp,
): Server = doStartMcpServer(factory, port, host, tools) { server ->
    installMcpTransport(server, transport)
}.first

/**
 * Starts a new MCP server with the given [tools] on an OS-allocated port on the passed [host].
 * The actual port can be obtained from the returned list of [EngineConnectorConfig].
 * Defaults to Streamable HTTP transport.
 *
 * @param factory The Ktor application engine factory to use (e.g., CIO, Netty).
 * @param tools The tools to expose via the MCP server.
 * @param host The host to bind to.
 * @param transport The transport type to use.
 * @return A pair of the MCP [Server] and the list of connectors (used to discover the allocated port).
 */
public suspend fun startMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    tools: ToolRegistry,
    host: String = "localhost",
    transport: McpServerTransportType = McpServerTransportType.StreamableHttp,
): Pair<Server, List<EngineConnectorConfig>> = doStartMcpServer(factory, 0, host, tools) { server ->
    installMcpTransport(server, transport)
}

/**
 * Starts a new MCP server with the passed [tools] that listens to and writes
 * to the specified [port] on the passed [host] using SSE transport.
 */
@Deprecated(
    "SSE transport is deprecated. Use startMcpServer() which defaults to Streamable HTTP.",
    ReplaceWith("startMcpServer(factory, tools, port, host)"),
    level = DeprecationLevel.WARNING,
)
public suspend fun startSseMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    port: Int = 3000,
    host: String = "localhost",
    tools: ToolRegistry,
): Server = doStartMcpServer(factory, port, host, tools) { server -> mcp { server } }.first

/**
 * Starts a new MCP server with the passed [tools] that listens to and writes
 * to the allocated port on the passed [host] using SSE transport.
 * A port can be obtained from the returned list of [EngineConnectorConfig].
 */
@Deprecated(
    "SSE transport is deprecated. Use startMcpServer() which defaults to Streamable HTTP.",
    ReplaceWith("startMcpServer(factory, tools, host)"),
    level = DeprecationLevel.WARNING,
)
public suspend fun startSseMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    host: String = "localhost",
    tools: ToolRegistry,
): Pair<Server, List<EngineConnectorConfig>> =
    doStartMcpServer(factory, 0, host, tools) { server -> mcp { server } }

private fun Application.installMcpTransport(server: Server, transport: McpServerTransportType) {
    when (transport) {
        McpServerTransportType.StreamableHttp -> mcpStreamableHttp { server }
        McpServerTransportType.SSE -> mcp { server }
    }
}

private suspend fun doStartMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    port: Int,
    host: String,
    tools: ToolRegistry,
    install: Application.(Server) -> Unit,
): Pair<Server, List<EngineConnectorConfig>> {
    val server = configureMcpServer(tools)

    val emb = embeddedServer(factory = factory, host = host, port = port) { install(server) }
        .also { emb -> server.onClose { emb.stop(1000, 1000) } }
        .startSuspend(wait = false)

    return server to emb.connectors()
}

private suspend fun EmbeddedServer<*, *>.connectors(): List<EngineConnectorConfig> = coroutineScope {
    val scope = this@coroutineScope
    val server = this@connectors

    while (scope.isActive) {
        val connectors = server.engine.resolvedConnectors()
        if (connectors.isNotEmpty()) {
            return@coroutineScope connectors
        }
        delay(50.milliseconds)
    }

    return@coroutineScope emptyList()
}

/**
 * Build an MCP server with the given [tools].
 *
 * @param tools The tools to add to the server
 * @param implementation Optional implementation information for the server.
 * @param serializer Optional serializer to use for encoding and decoding tool arguments and results.
 * Defaults to [KotlinxSerializer]
 */
@OptIn(InternalAgentToolsApi::class)
public fun configureMcpServer(
    tools: ToolRegistry,
    implementation: Implementation = Implementation("MCP Server with Koog-based tools", "dev"),
    serializer: JSONSerializer = KotlinxSerializer(),
): Server {
    val server = Server(
        serverInfo = implementation,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    tools.tools.forEach { tool ->
        server.addTool(tool, serializer)
    }

    return server
}

/**
 * Adds a tool to the MCP server.
 *
 * @param tool The tool to add
 * @param serializer Optional serializer to use for encoding and decoding tool arguments and results
 * Defaults to [KotlinxSerializer]
 */
@OptIn(InternalAgentToolsApi::class)
public fun Server.addTool(
    tool: Tool<*, *>,
    serializer: JSONSerializer = KotlinxSerializer(),
) {
    addTool(tool.descriptor.asSdkTool()) { request ->
        val args = try {
            tool.decodeArgs(
                rawArgs = (request.arguments ?: EmptyJsonObject).toKoogJSONObject(),
                serializer = serializer
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@addTool CallToolResult(
                content = listOf(TextContent("Failed to parse arguments for tool '${tool.name}': ${e.message}")),
                isError = true,
            )
        }
        val result = tool.executeUnsafe(args)

        CallToolResult(
            content = listOf(
                TextContent(tool.encodeResultToStringUnsafe(result, serializer))
            )
        )
    }
}

private fun ToolDescriptor.asSdkTool(): SdkTool {
    return SdkTool(
        name = name,
        description = description,
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                (requiredParameters + optionalParameters).forEach { param ->
                    put(param.name, param.toJsonSchema())
                }
            },
            required = requiredParameters.map { it.name },
        ),
        outputSchema = null,
        annotations = null,
        title = null,
    )
}

// copied from AbstractOpenAILLMClient.kt
private fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
    put("description", description)
    fillJsonSchema(type)
}

private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
    when (type) {
        ToolParameterType.Boolean -> put("type", "boolean")

        ToolParameterType.Float -> put("type", "number")

        ToolParameterType.Integer -> put("type", "integer")

        ToolParameterType.String -> put("type", "string")

        ToolParameterType.Null -> put("type", "null")

        is ToolParameterType.Enum -> {
            put("type", "string")
            putJsonArray("enum") {
                type.entries.forEach { entry -> add(entry) }
            }
        }

        is ToolParameterType.List -> {
            put("type", "array")
            putJsonObject("items") { fillJsonSchema(type.itemsType) }
        }

        is ToolParameterType.AnyOf -> {
            putJsonArray("anyOf") {
                addAll(
                    type.types.map { propertiesType ->
                        propertiesType.toJsonSchema()
                    }
                )
            }
        }

        is ToolParameterType.Object -> {
            put("type", "object")
            type.additionalProperties?.let { put("additionalProperties", it) }
            putJsonObject("properties") {
                type.properties.forEach { property ->
                    putJsonObject(property.name) {
                        fillJsonSchema(property.type)
                        put("description", property.description)
                    }
                }
            }
        }
    }
}
