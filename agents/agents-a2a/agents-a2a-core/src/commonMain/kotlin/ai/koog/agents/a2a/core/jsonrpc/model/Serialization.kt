@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.a2a.core.jsonrpc.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.collections.contains

public val JSONRPCJson: Json = Json {
    explicitNulls = false
    encodeDefaults = false
    ignoreUnknownKeys = true
}

internal object RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RequestId")

    override fun deserialize(decoder: Decoder): RequestId {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only deserialize JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> RequestId.StringId(element.content)
                element.longOrNull != null -> RequestId.NumberId(element.long)
                else -> error("Invalid RequestId type")
            }

            else -> error("Invalid RequestId format")
        }
    }

    override fun serialize(encoder: Encoder, value: RequestId) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only serialize JSON")
        when (value) {
            is RequestId.StringId -> jsonEncoder.encodeString(value.value)
            is RequestId.NumberId -> jsonEncoder.encodeLong(value.value)
        }
    }
}

internal object JSONRPCMessageSerializer : JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObject = element.jsonObject

        return when {
            "method" in jsonObject -> when {
                "id" in jsonObject -> JSONRPCRequest.serializer()
                else -> JSONRPCNotification.serializer()
            }

            else -> JSONRPCResponseSerializer
        }
    }
}

internal object JSONRPCResponseSerializer : JsonContentPolymorphicSerializer<JSONRPCResponse>(JSONRPCResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCResponse> {
        val jsonObject = element.jsonObject

        return when {
            "result" in jsonObject -> JSONRPCSuccessResponse.serializer()
            "error" in jsonObject -> JSONRPCErrorResponse.serializer()
            else -> error("Invalid JSON format")
        }
    }
}
