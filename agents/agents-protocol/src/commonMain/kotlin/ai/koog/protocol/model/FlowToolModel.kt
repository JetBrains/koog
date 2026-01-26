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
 *
 */
@Serializable(with = FlowToolModelSerializer::class)
public data class FlowToolModel(
    public val name: String,
    public val type: FlowToolKind,
    public val parameters: FlowToolParameters
) {
    /**
     *
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
 *
 */
@Serializable(with = FlowToolKindSerializer::class)
public sealed class FlowToolKind(public val id: String) {

    /**
     *
     */
    public data object MCP : FlowToolKind("mcp")

    /**
     *
     */
    public data object LOCAL : FlowToolKind("local")
}

/**
 *
 */
@Serializable
public enum class FlowMcpToolTransportKind(public val id: String) {
    @SerialName("stdio")
    STDIO("stdio"),

    @SerialName("sse")
    SSE("sse")
}

/**
 *
 */
@Serializable
public sealed interface FlowToolParameters {

    /**
     *
     */
    @Serializable
    public sealed interface FlowMcpToolParameters : FlowToolParameters {
        /**
         *
         */
        public val transport: FlowMcpToolTransportKind
    }

    /**
     *
     */
    @Serializable
    public data class FlowToolStdioParameters(
        val command: String,
        val args: List<String> = emptyList()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.STDIO
    }

    /**
     *
     */
    @Serializable
    public data class FlowToolSSEParameters(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : FlowMcpToolParameters {
        override val transport: FlowMcpToolTransportKind = FlowMcpToolTransportKind.SSE
    }

    /**
     *
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
