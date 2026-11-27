package ai.koog.prompt.message

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Loads a [CacheControl] serializer for [qualifiedName] when the platform can resolve classes by name.
 * [qualifiedName] is the kotlinx serial name, which defaults to the qualified class name.
 * Returns null when the class cannot be found or the platform has no class loader.
 */
internal expect fun loadCacheControlSerializer(qualifiedName: String): KSerializer<CacheControl>?

/**
 * Serializes a [CacheControl] value together with the serial name of its runtime class.
 *
 * [CacheControl] is an open interface, so kotlinx.serialization has no closed polymorphic
 * hierarchy to pick a subclass serializer from. Provider directives such as
 * `AnthropicCacheControl.Default` and `BedrockCacheControl.Default` would otherwise encode
 * as the same empty object and collide in a prompt cache key.
 */
@OptIn(ExperimentalAtomicApi::class, InternalSerializationApi::class)
internal object CacheControlSerializer : KSerializer<CacheControl> {
    private val serializersByType = AtomicReference(emptyMap<String, KSerializer<CacheControl>>())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ai.koog.prompt.message.CacheControl") {
        element("type", serializer<String>().descriptor)
        element("value", serializer<JsonElement>().descriptor)
    }

    override fun serialize(encoder: Encoder, value: CacheControl) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("CacheControl can only be serialized as JSON")
        val serializer = serializerFor(value)
        val typeName = serializer.descriptor.serialName
        remember(typeName, serializer)
        val payload = jsonEncoder.json.encodeToJsonElement(serializer, value)
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("type", JsonPrimitive(typeName))
                put("value", payload)
            }
        )
    }

    override fun deserialize(decoder: Decoder): CacheControl {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("CacheControl can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val typeName = element["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("CacheControl value is missing its type name")
        val payload = element["value"]
            ?: throw SerializationException("CacheControl value is missing its payload")
        val serializer = serializerForName(typeName)
            ?: throw SerializationException(
                "Serializer for CacheControl type '$typeName' was not found. The class must be @Serializable."
            )
        return jsonDecoder.json.decodeFromJsonElement(serializer, payload)
    }

    private fun serializerFor(value: CacheControl): KSerializer<CacheControl> {
        val serializer = value::class.serializerOrNull()
            ?: throw SerializationException(
                "CacheControl implementation ${value::class.simpleName ?: value::class} must be @Serializable"
            )
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<CacheControl>
    }

    private fun serializerForName(typeName: String): KSerializer<CacheControl>? {
        loadCacheControlSerializer(typeName)?.let { loaded ->
            remember(typeName, loaded)
            return loaded
        }
        return serializersByType.load()[typeName]
    }

    private fun remember(typeName: String, serializer: KSerializer<CacheControl>) {
        while (true) {
            val current = serializersByType.load()
            if (typeName in current) return
            if (serializersByType.compareAndSet(current, current + (typeName to serializer))) return
        }
    }
}
