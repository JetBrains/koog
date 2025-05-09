package ai.grazie.agents.mcp

import ai.grazie.code.agents.core.tools.*
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * A tool registry that connects to an MCP server, retrieves tools, and transforms them to the Tool<*, *> framework interface.
 */
class MCPToolRegistryProvider {
    /**
     * Creates a ToolRegistry with tools from the MCP server.
     */
    fun fromClient(mcpClient: Client, stageName: String = ToolStage.DEFAULT_STAGE_NAME): ToolRegistry {
        val sdkTools = runBlocking { mcpClient.listTools() }?.tools ?: emptyList()
        println(sdkTools)
        return ToolRegistry {
            stage(stageName) {
                sdkTools.forEach { sdkTool ->
                    val toolDescriptor = parseToolDescriptor(sdkTool)
                    tool(MCPTool(mcpClient, toolDescriptor))
                }
            }
        }
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

/**
 * A Tool implementation that calls an MCP tool.
 */
private class MCPTool(
    private val mcpClient: Client, override val descriptor: ToolDescriptor
) : Tool<MCPTool.Args, MCPTool.Result>() {

    @Serializable(with = ArgsSerializer::class)
    data class Args(val arguments: JsonObject) : Tool.Args

    class ArgsSerializer : KSerializer<Args> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ai.grazie.agents.mcp.MCPTool.Args") {
            element("arguments", JsonObject.serializer().descriptor)
        }

        override fun serialize(encoder: Encoder, value: Args) {
            when (encoder) {
                is JsonEncoder -> {
                    value.arguments
                }

                else -> {
                    val jsonString = Json.encodeToString(JsonObject.serializer(), value.arguments)
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
                    val jsonObject = Json.decodeFromString(JsonObject.serializer(), jsonString)
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
