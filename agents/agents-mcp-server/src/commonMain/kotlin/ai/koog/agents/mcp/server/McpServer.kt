package ai.koog.agents.mcp.server

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import io.modelcontextprotocol.kotlin.sdk.Tool as SdkTool

/**
 * Starts a new MCP server with the passed [tools] that listens to and writes to the specified [port] on the passed [host].
 * A port can be obtained from the returned list of [EngineConnectorConfig].
 */
public suspend fun startSseMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    port: Int = 3000,
    host: String = "localhost",
    tools: ToolRegistry,
): Server = doStartSseMcpServer(factory, port, host, tools, true).first

/**
 * Starts a new MCP server with the passed [tools] that listens to and writes to the allocated port on the passed [host].
 * A port can be obtained from the returned list of [EngineConnectorConfig].
 */
public suspend fun startSseMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    host: String = "localhost",
    tools: ToolRegistry,
): Pair<Server, List<EngineConnectorConfig>> = doStartSseMcpServer(factory, 0, host, tools, false)

private suspend fun doStartSseMcpServer(
    factory: ApplicationEngineFactory<*, *>,
    port: Int,
    host: String,
    tools: ToolRegistry,
    skipConnectors: Boolean,
): Pair<Server, List<EngineConnectorConfig>> {
    val server = configureMcpServer(tools)

    val emb = embeddedServer(factory = factory, host = host, port = port) {
        mcp { server }
    }
        .also { emb -> server.onClose { emb.stop(1000, 1000) } }
        .startSuspend(wait = false)

    val connectors = if (skipConnectors) {
        emptyList()
    } else {
        emb.connectors()
    }

    return server to connectors
}

private suspend fun EmbeddedServer<*, *>.connectors(): List<EngineConnectorConfig> = coroutineScope {
    val scope = this@coroutineScope
    val server = this@connectors

    while (scope.isActive) {
        val connectors = server.engine.resolvedConnectors()
        if (connectors.isNotEmpty()) {
            return@coroutineScope connectors
        }
    }

    return@coroutineScope emptyList()
}

/**
 * Build an MCP server with the given [tools].
 */
@OptIn(InternalAgentToolsApi::class)
public fun configureMcpServer(
    tools: ToolRegistry,
    implementation: Implementation = Implementation("MCP Server with Koog-based tools", "dev")
): Server {
    val enabler = object : DirectToolCallsEnabler {}

    val server = Server(
        serverInfo = implementation,
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    tools.tools.forEach { tool ->
        server.addTool(tool, enabler)
    }

    return server
}

/**
 * Adds a tool to the MCP server.
 */
@OptIn(InternalAgentToolsApi::class)
public fun Server.addTool(
    tool: Tool<*, *>,
    enabler: DirectToolCallsEnabler = object : DirectToolCallsEnabler {},
) {
    addTool(tool.descriptor.asSdkTool()) { request ->
        val args = tool.decodeArgs(request.arguments)
        val result = tool.executeUnsafe(args, enabler)

        CallToolResult(
            content = listOf(TextContent(tool.encodeResultToStringUnsafe(result)))
        )
    }
}

private fun ToolDescriptor.asSdkTool(): SdkTool {
    return SdkTool(
        name = name,
        description = description,
        inputSchema = SdkTool.Input(
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
