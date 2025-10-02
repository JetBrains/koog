@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.transport.jsonrpc.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.collections.contains

public val JSONRPCJson: Json = Json {
    explicitNulls = false
    encodeDefaults = false
    ignoreUnknownKeys = true
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
            "error" in jsonObject -> JSONRPCErrorResponse.serializer()
            else -> JSONRPCSuccessResponse.serializer()
        }
    }
}
