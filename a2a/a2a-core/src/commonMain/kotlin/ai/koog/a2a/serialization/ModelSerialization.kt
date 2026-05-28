package ai.koog.a2a.serialization

import ai.koog.a2a.model.APIKeySecurityScheme
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.HTTPAuthSecurityScheme
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MutualTLSSecurityScheme
import ai.koog.a2a.model.OAuth2SecurityScheme
import ai.koog.a2a.model.OpenIdConnectSecurityScheme
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.SecurityScheme
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public object SecuritySchemeSerializer : JsonContentPolymorphicSerializer<SecurityScheme>(SecurityScheme::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SecurityScheme> {
        val jsonObject = element.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content ?: throw SerializationException("Missing 'type' field in SecurityScheme")

        return when (type) {
            "apiKey" -> APIKeySecurityScheme.serializer()
            "http" -> HTTPAuthSecurityScheme.serializer()
            "oauth2" -> OAuth2SecurityScheme.serializer()
            "openIdConnect" -> OpenIdConnectSecurityScheme.serializer()
            "mutualTLS" -> MutualTLSSecurityScheme.serializer()
            else -> throw SerializationException("Unknown SecurityScheme type: $type")
        }
    }
}

public object PartSerializer : JsonContentPolymorphicSerializer<Part>(Part::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Part> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: throw SerializationException("Missing 'kind' field in Part")

        return when (kind) {
            "text" -> TextPart.serializer()
            "file" -> FilePart.serializer()
            "data" -> DataPart.serializer()
            else -> throw SerializationException("Unknown Part kind: $kind")
        }
    }
}

public object FileSerializer : JsonContentPolymorphicSerializer<File>(File::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<File> {
        val jsonObject = element.jsonObject

        return when {
            "bytes" in jsonObject -> FileWithBytes.serializer()
            "uri" in jsonObject -> FileWithUri.serializer()
            else -> throw SerializationException("Unknown File type")
        }
    }
}

public object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Event> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: throw SerializationException("Missing 'kind' field in Event")

        return when (kind) {
            "status-update" -> TaskStatusUpdateEvent.serializer()
            "artifact-update" -> TaskArtifactUpdateEvent.serializer()
            "task" -> Task.serializer()
            "message" -> Message.serializer()
            else -> throw SerializationException("Unknown kind: $kind")
        }
    }
}

public object CommunicationEventSerializer : JsonContentPolymorphicSerializer<CommunicationEvent>(CommunicationEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CommunicationEvent> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: throw SerializationException("Missing 'kind' field in CommunicationEvent")

        return when (kind) {
            "task" -> Task.serializer()
            "message" -> Message.serializer()
            else -> throw SerializationException("Unknown kind: $kind")
        }
    }
}

public object TaskEventSerializer : JsonContentPolymorphicSerializer<TaskEvent>(TaskEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TaskEvent> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: throw SerializationException("Missing 'kind' field in TaskEvent")

        return when (kind) {
            "task" -> Task.serializer()
            "status-update" -> TaskStatusUpdateEvent.serializer()
            "artifact-update" -> TaskArtifactUpdateEvent.serializer()
            else -> throw SerializationException("Unknown kind: $kind")
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
public object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    private val base64 = Base64.Default

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("ByteArrayAsBase64Serializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        val base64Encoded = base64.encode(value)
        encoder.encodeString(base64Encoded)
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val base64Decoded = decoder.decodeString()
        return base64.decode(base64Decoded)
    }
}
