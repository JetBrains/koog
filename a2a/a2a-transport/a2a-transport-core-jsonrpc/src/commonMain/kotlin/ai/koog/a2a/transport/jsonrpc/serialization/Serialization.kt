@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.transport.jsonrpc.serialization

import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
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
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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

/**
 * Serializer for [JSONRPCErrorResponse] that always encodes `id`, as `null` when the request id is unknown,
 * as JSON-RPC 2.0 requires. The generated serializer would drop it, because [JSONRPCJson] has `explicitNulls = false`.
 */
public object JSONRPCErrorResponseSerializer : KSerializer<JSONRPCErrorResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JSONRPCErrorResponse") {
        element("id", RequestIdSerializer.descriptor.nullable)
        element("error", JSONRPCError.serializer().descriptor)
        element<String>("jsonrpc")
    }

    override fun deserialize(decoder: Decoder): JSONRPCErrorResponse {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Can only deserialize JSON")
        val json = jsonDecoder.json
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return JSONRPCErrorResponse(
            id = jsonObject["id"]
                ?.takeIf { it !is JsonNull }
                ?.let { json.decodeFromJsonElement(RequestIdSerializer, it) },
            error = jsonObject["error"]
                ?.let { json.decodeFromJsonElement(JSONRPCError.serializer(), it) }
                ?: throw SerializationException("Missing 'error' in JSON-RPC error response"),
            jsonrpc = (jsonObject["jsonrpc"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: throw SerializationException("Missing 'jsonrpc' in JSON-RPC error response"),
        )
    }

    override fun serialize(encoder: Encoder, value: JSONRPCErrorResponse) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Can only serialize JSON")
        val json = jsonEncoder.json

        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put(
                    "id",
                    when (val id = value.id) {
                        is RequestId.StringId -> JsonPrimitive(id.value)
                        is RequestId.NumberId -> JsonPrimitive(id.value)
                        null -> JsonNull
                    }
                )
                put("error", json.encodeToJsonElement(JSONRPCError.serializer(), value.error))
                put("jsonrpc", value.jsonrpc)
            }
        )
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
