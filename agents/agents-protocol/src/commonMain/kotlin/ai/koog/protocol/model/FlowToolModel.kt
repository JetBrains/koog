package ai.koog.protocol.model

import ai.koog.protocol.parser.FlowToolKindSerializer
import ai.koog.protocol.tool.FlowTool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializable model representing a tool configuration in a flow.
 *
 * Tools are external capabilities that agents can use to perform actions beyond LLM inference,
 * such as accessing APIs, running commands, or querying databases.
 *
 * @property name Unique identifier for the tool
 * @property type The kind of tool integration (MCP or Local)
 * @property parameters Configuration parameters specific to the tool type
 */
@Serializable(with = FlowToolModelSerializer::class)
public data class FlowToolModel(
    public val name: String,
    public val type: FlowToolKind,
    public val parameters: FlowToolParameters
) {
    /**
     * Converts this serializable model to a runtime [FlowTool] instance.
     *
     * @return A runtime FlowTool object ready for use by agents
     */
    public fun toFlowTool(): FlowTool {
        return when (type) {
            FlowToolKind.MCP -> {
                val transport = (parameters as FlowToolParameters.FlowMcpToolParameters).transport

                when (transport) {
                    FlowMcpToolTransportKind.STDIO -> {
                        val parameters = parameters as FlowToolParameters.FlowToolStdioParameters
                        FlowTool.Mcp.Stdio(parameters.command, parameters.args)
                    }

                    FlowMcpToolTransportKind.SSE -> {
                        val parameters = parameters as FlowToolParameters.FlowToolSSEParameters
                        FlowTool.Mcp.SSE(parameters.url, parameters.headers)
                    }
                }
            }
            FlowToolKind.LOCAL -> {
                val parameters = parameters as FlowToolParameters.FlowLocalToolParameters
                FlowTool.Local(parameters.path)
            }
        }
    }
}

/**
 * Represents the type of tool integration supported by the flow framework.
 *
 * @property id String identifier used for serialization
 */
@Serializable(with = FlowToolKindSerializer::class)
public sealed class FlowToolKind(public val id: String) {

    /**
     * Model Context Protocol (MCP) tool integration for connecting to external services
     * via standardized communication protocols like stdio or SSE.
     */
    public data object MCP : FlowToolKind("mcp")

    /**
     * Local tool implementation registered within the application by fully qualified name.
     */
    public data object LOCAL : FlowToolKind("local")
}

/**
 * Transport protocol for Model Context Protocol (MCP) tool communication.
 *
 * Defines how the application communicates with MCP-based tools.
 *
 * @property id String identifier used for serialization
 */
@Serializable
public enum class FlowMcpToolTransportKind(public val id: String) {
    @SerialName("stdio")
    STDIO("stdio"),

    @SerialName("sse")
    SSE("sse")
}

/**
 * Base interface for tool configuration parameters.
 */
@Serializable
public sealed interface FlowToolParameters {

    /**
     * Parameters for MCP-based tools with transport configuration.
     */
    @Serializable
    public sealed interface FlowMcpToolParameters : FlowToolParameters {
        /**
         * The transport protocol to use for MCP communication.
         */
        public val transport: FlowMcpToolTransportKind
    }

    /**
     * Parameters for standard input/output (stdio) MCP transport.
     *
     * Launches an external process and communicates via standard input/output streams.
     *
     * @property command The executable command to run
     * @property args List of command-line arguments to pass to the command
     */
    @Serializable
    public data class FlowToolStdioParameters(
        val command: String,
        val args: List<String> = emptyList()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.STDIO
    }

    /**
     * Parameters for Server-Sent Events (SSE) MCP transport.
     *
     * Connects to an MCP server via HTTP Server-Sent Events.
     *
     * @property url The HTTP(S) URL endpoint for the SSE connection
     * @property headers Optional HTTP headers to include in the connection request
     */
    @Serializable
    public data class FlowToolSSEParameters(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.SSE
    }

    /**
     * Parameters for local tool implementations.
     *
     * References a tool that is registered within the application by its fully qualified name.
     *
     * @property path The fully qualified name or path identifier for the local tool
     */
    @Serializable
    public data class FlowLocalToolParameters(
        public val path: String
    ) : FlowToolParameters
}

/**
 * Custom serializer for [FlowToolModel] that uses the `type` field to determine
 * how to deserialize the `parameters` field.
 */
internal object FlowToolModelSerializer : KSerializer<FlowToolModel> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlowToolModel") {
        element<String>("name")
        element<FlowToolKind>("type")
        element<JsonElement>("parameters")
    }

    override fun serialize(encoder: Encoder, value: FlowToolModel) {
        require(encoder is JsonEncoder)
        val json = encoder.json

        val parametersJson = when (val params = value.parameters) {
            is FlowToolParameters.FlowToolStdioParameters ->
                json.encodeToJsonElement(FlowToolParameters.FlowToolStdioParameters.serializer(), params)

            is FlowToolParameters.FlowToolSSEParameters ->
                json.encodeToJsonElement(FlowToolParameters.FlowToolSSEParameters.serializer(), params)

            is FlowToolParameters.FlowLocalToolParameters ->
                json.encodeToJsonElement(FlowToolParameters.FlowLocalToolParameters.serializer(), params)
        }

        val jsonObject = buildJsonObject {
            put("name", JsonPrimitive(value.name))
            put("type", json.encodeToJsonElement(FlowToolKind.serializer(), value.type))
            put("parameters", parametersJson)
        }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): FlowToolModel {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val jsonObject = decoder.decodeJsonElement().jsonObject

        val name = jsonObject["name"]!!.jsonPrimitive.content
        val type = json.decodeFromJsonElement(FlowToolKind.serializer(), jsonObject["type"]!!)
        val parametersJson = jsonObject["parameters"]!!.jsonObject

        val parameters: FlowToolParameters = when (type) {
            FlowToolKind.LOCAL -> json.decodeFromJsonElement(
                FlowToolParameters.FlowLocalToolParameters.serializer(),
                parametersJson
            )
            FlowToolKind.MCP -> {
                val transport = json.decodeFromJsonElement(
                    FlowMcpToolTransportKind.serializer(),
                    parametersJson["transport"]!!
                )
                when (transport) {
                    FlowMcpToolTransportKind.STDIO -> json.decodeFromJsonElement(
                        FlowToolParameters.FlowToolStdioParameters.serializer(),
                        parametersJson
                    )
                    FlowMcpToolTransportKind.SSE -> json.decodeFromJsonElement(
                        FlowToolParameters.FlowToolSSEParameters.serializer(),
                        parametersJson
                    )
                }
            }
        }

        return FlowToolModel(name, type, parameters)
    }
}
