@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.transport.jsonrpc.serialization

import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCMessage
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCNotification
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCSuccessResponse
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
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

public val JSONRPCJson: Json = Json {
    explicitNulls = false
    encodeDefaults = false
    ignoreUnknownKeys = true
}

public object JSONRPCMessageSerializer : JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
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

public object JSONRPCResponseSerializer : JsonContentPolymorphicSerializer<JSONRPCResponse>(JSONRPCResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCResponse> {
        val jsonObject = element.jsonObject

        return when {
            "error" in jsonObject -> JSONRPCErrorResponse.serializer()
            else -> JSONRPCSuccessResponse.serializer()
        }
    }
}

public object RequestIdSerializer : KSerializer<RequestId> {
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
