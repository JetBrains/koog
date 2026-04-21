package ai.koog.agents.mcp

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.kotlinx.toKotlinxJsonObject
import ai.koog.serialization.typeToken
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
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
@InternalAgentsApi
public class McpTool(
    private val mcpClient: Client,
    descriptor: ToolDescriptor,
    metadata: Map<String, String>,
) : Tool<JSONObject, CallToolResult?>(
    argsType = typeToken<JSONObject>(),
    resultType = typeToken<CallToolResult?>(),
    descriptor = descriptor,
    metadata = metadata,
) {
    /**
     * MCP SDK uses kotlinx.serialization for JSON serialization, so keep private instance to perform some
     * JSON serialization/deserialization operations.
     */
    private val json = Json.Default
    private val resultSerializer = CallToolResult.serializer().nullable

    /**
     * Executes the MCP tool with the given arguments.
     *
     * This method calls the MCP tool through the MCP client and converts the result
     * to a Result object that can be used by the agent framework.
     *
     * @param args The arguments for the MCP tool call.
     * @return The result of the MCP tool call.
     */
    override suspend fun execute(args: JSONObject): CallToolResult {
        return mcpClient.callTool(
            name = descriptor.name,
            arguments = args.toKotlinxJsonObject()
        )
    }

    override fun decodeResult(rawResult: JSONElement, serializer: JSONSerializer): CallToolResult? {
        return json.decodeFromJsonElement(resultSerializer, rawResult.toKotlinxJsonElement())
    }

    override fun encodeResult(result: CallToolResult?, serializer: JSONSerializer): JSONElement {
        return json.encodeToJsonElement(resultSerializer, result).toKoogJSONElement()
    }

    /**
     * Postprocess result string representation for LLMs a bit, removing unnecessary meta fields.
     */
    override fun encodeResultToString(result: CallToolResult?, serializer: JSONSerializer): String {
        val preparedResultJson: JsonElement = result
            ?.let {
                JsonObject(
                    json.encodeToJsonElement(resultSerializer, result).jsonObject
                        // LLM doesn't need "meta" fields, leave only actual data
                        .filter { (key, _) -> key !in listOf("type", "_meta") }
                )
            }
            ?: JsonNull

        return serializer.encodeJSONElementToString(preparedResultJson.toKoogJSONElement())
    }
}
