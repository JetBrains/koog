package ai.koog.prompt.executor.clients.mistralai.serialization

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.mistralai.model.Stop
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@InternalLLMClientApi
internal object StopSerializer : KSerializer<Stop> {

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Stop) {
        when (value) {
            is Stop.Single -> encoder.encodeString(value.value)
            is Stop.Multiple -> encoder.encodeSerializableValue(
                ListSerializer(String.serializer()),
                value.values
            )
        }
    }

    override fun deserialize(decoder: Decoder): Stop {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        return when (element) {
            is JsonPrimitive -> Stop.Single(element.content)
            is JsonArray -> Stop.Multiple(element.map { it.jsonPrimitive.content })
            else -> throw SerializationException("Invalid stop value")
        }
    }
}
