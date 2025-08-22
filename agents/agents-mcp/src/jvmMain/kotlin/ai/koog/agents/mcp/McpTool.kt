package ai.koog.agents.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

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
    override val descriptor: ToolDescriptor
) : Tool<McpTool.Args, McpTool.Result>() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Arguments for an MCP tool call.
     *
     * This class wraps a JsonObject containing the arguments to be passed to an MCP tool.
     * It's serialized using a custom serializer to handle different encoding formats.
     *
     * @property arguments The JsonObject containing the arguments for the MCP tool.
     */
    @Serializable(with = ArgsSerializer::class)
    public data class Args(val arguments: JsonObject) : ToolArgs

    /**
     * Custom serializer for the Args class.
     *
     * This serializer handles the conversion between Args and various serialization formats.
     * It specifically handles JsonEncoder differently from other encoders to maintain
     * compatibility with the MCP SDK.
     */
    public class ArgsSerializer : KSerializer<Args> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("ai.koog.agents.mcp.McpTool.Args") {
                element("arguments", JsonObject.serializer().descriptor)
            }

        override fun serialize(encoder: Encoder, value: Args) {
            when (encoder) {
                is JsonEncoder -> {
                    // Encode the arguments directly as a JsonObject
                    encoder.encodeJsonElement(value.arguments)
                }

                else -> {
                    // For other encoders, convert to a JSON string first
                    val jsonString = Json.encodeToString(JsonObject.serializer(), value.arguments)
                    encoder.encodeString(jsonString)
                }
            }
        }

        override fun deserialize(decoder: Decoder): Args {
            return when (decoder) {
                is JsonDecoder -> {
                    // Decode directly from JsonElement
                    val jsonElement = decoder.decodeJsonElement()
                    Args(jsonElement as JsonObject)
                }

                else -> {
                    // For other decoders, parse from a JSON string
                    val jsonString = decoder.decodeString()
                    val jsonObject = Json.decodeFromString(JsonObject.serializer(), jsonString)
                    Args(jsonObject)
                }
            }
        }
    }

    /**
     * Result of an MCP tool call.
     *
     * This class wraps a list of PromptMessageContent objects returned by an MCP tool.
     * It implements the ToolResult interface to make it compatible with the agent framework.
     *
     * @property promptMessageContents The list of content items returned by the MCP tool.
     */
    public class Result(public val promptMessageContents: List<PromptMessageContent>) : ToolResult {
        /**
         * Converts the result to a string representation.
         *
         * This method provides a formatted string representation of the result content.
         * If the content is empty, it returns a message indicating no content.
         *
         * @return A string representation of the result.
         */
        override fun toStringDefault(): String {
            if (promptMessageContents.isEmpty()) {
                return "[No content]"
            }

            // Format each content item and join them with newlines
            return promptMessageContents.joinToString("\n") { content ->
                content.toString()
            }
        }
    }

    override val argsSerializer: KSerializer<Args> = ArgsSerializer()

    /**
     * Executes the MCP tool with the given arguments.
     *
     * This method calls the MCP tool through the MCP client and converts the result
     * to a Result object that can be used by the agent framework.
     *
     * @param args The arguments for the MCP tool call.
     * @return The result of the MCP tool call.
     */
    override suspend fun execute(args: Args): Result {
        val result = mcpClient.callTool(
            name = descriptor.name,
            arguments = args.arguments
        )
        return Result(result?.content ?: emptyList())
    }
}
