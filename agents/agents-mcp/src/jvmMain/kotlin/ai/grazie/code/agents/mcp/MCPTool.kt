package ai.grazie.code.agents.mcp

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
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
 * A Tool implementation that calls an MCP tool.
 */
class MCPTool(
    private val mcpClient: Client, override val descriptor: ToolDescriptor
) : Tool<MCPTool.Args, MCPTool.Result>() {

    @Serializable(with = ArgsSerializer::class)
    data class Args(val arguments: JsonObject) : Tool.Args

    class ArgsSerializer : KSerializer<Args> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ai.grazie.agents.mcp.MCPTool.Args") {
            element("arguments", JsonObject.Companion.serializer().descriptor)
        }

        override fun serialize(encoder: Encoder, value: Args) {
            when (encoder) {
                is JsonEncoder -> {
                    value.arguments
                }

                else -> {
                    val jsonString = Json.Default.encodeToString(JsonObject.Companion.serializer(), value.arguments)
                    encoder.encodeString(jsonString)
                }
            }
        }

        override fun deserialize(decoder: Decoder): Args {
            return when (decoder) {
                is JsonDecoder -> {
                    val jsonElement = decoder.decodeJsonElement()
                    Args(jsonElement as JsonObject)
                }

                else -> {
                    val jsonString = decoder.decodeString()
                    val jsonObject = Json.Default.decodeFromString(JsonObject.Companion.serializer(), jsonString)
                    Args(jsonObject)
                }
            }
        }
    }

    class Result(val promptMessageContents: List<PromptMessageContent>) : ToolResult {
        // TODO: Decide on how to dump to string different types of content
        override fun toStringDefault(): String = promptMessageContents.toString()
    }

    override val argsSerializer: KSerializer<Args> = ArgsSerializer()

    override suspend fun execute(args: Args): Result {
        val result = mcpClient.callTool(
            name = descriptor.name, arguments = args.arguments
        )
        return Result(result?.content ?: emptyList())
    }
}