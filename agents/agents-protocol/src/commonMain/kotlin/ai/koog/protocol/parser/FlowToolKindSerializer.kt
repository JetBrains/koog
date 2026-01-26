package ai.koog.protocol.parser

import ai.koog.protocol.model.FlowToolKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for [FlowToolKind] that serializes/deserializes as a simple string.
 */
internal object FlowToolKindSerializer : KSerializer<FlowToolKind> {
    override val descriptor: SerialDescriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "FlowToolKind",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: FlowToolKind) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): FlowToolKind {
        val id = decoder.decodeString()
        return when (id.lowercase()) {
            "mcp" -> FlowToolKind.MCP
            "local" -> FlowToolKind.LOCAL
            else -> error("Unknown FlowToolKind: $id")
        }
    }
}
