package ai.koog.a2a.transport

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

internal object RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RequestId")

    override fun deserialize(decoder: Decoder): RequestId {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only deserialize JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> RequestId.StringId(element.content)
                element.longOrNull != null -> RequestId.NumberId(element.long)
                else -> throw SerializationException("Invalid RequestId type")
            }

            else -> throw SerializationException("Invalid RequestId format")
        }
    }

    override fun serialize(encoder: Encoder, value: RequestId) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Can only serialize JSON")
        when (value) {
            is RequestId.StringId -> jsonEncoder.encodeString(value.value)
            is RequestId.NumberId -> jsonEncoder.encodeLong(value.value)
        }
    }
}
