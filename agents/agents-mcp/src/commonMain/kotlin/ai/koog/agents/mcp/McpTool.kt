package ai.koog.agents.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A Tool implementation that calls an MCP (Model Context Protocol) tool.
 *
 * This class serves as a bridge between the agent framework's Tool interface and the MCP SDK.
 * It allows MCP tools to be used within the agent framework by:
 * 1. Converting agent framework tool arguments to MCP tool arguments
 * 2. Calling the MCP tool through the MCP client
 * 3. Converting MCP tool results back to agent framework tool results
 */
public class McpTool(
    private val mcpClient: Client,
    override val descriptor: ToolDescriptor,
) : Tool<JsonObject, CallToolResult?>() {
    override val name: String = descriptor.name
    override val description: String = descriptor.description

    override val argsSerializer: KSerializer<JsonObject> = JsonObject.serializer()
    override val resultSerializer: KSerializer<CallToolResult?> = CallToolResult.serializer().nullable

    /**
     * Executes the MCP tool with the given arguments.
     *
     * This method calls the MCP tool through the MCP client and converts the result
     * to a Result object that can be used by the agent framework.
     *
     * @param args The arguments for the MCP tool call.
     * @return The result of the MCP tool call.
     */
    override suspend fun execute(args: JsonObject): CallToolResult {
        return mcpClient.callTool(
            name = descriptor.name,
            arguments = args
        )
    }

    /**
     * Postprocess result string representation for LLMs a bit, removing unnecessary meta fields.
     */
    override fun encodeResultToString(result: CallToolResult?): String {
        val preparedResultJson: JsonElement = result
            ?.let {
                JsonObject(
                    json.encodeToJsonElement(resultSerializer, it).jsonObject
                        // LLM doesn't need "meta" fields, leave only actual data
                        .filter { (key, _) -> key !in listOf("type", "_meta") }
                )
            }
            ?: JsonNull

        return json.encodeToString(preparedResultJson)
    }
}
