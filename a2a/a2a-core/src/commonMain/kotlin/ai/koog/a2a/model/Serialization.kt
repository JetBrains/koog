package ai.koog.a2a.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object SecuritySchemeSerializer : JsonContentPolymorphicSerializer<SecurityScheme>(SecurityScheme::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SecurityScheme> {
        val jsonObject = element.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content ?: error("Missing 'type' field in SecurityScheme")

        return when (type) {
            "apiKey" -> APIKeySecurityScheme.serializer()
            "http" -> HTTPAuthSecurityScheme.serializer()
            "oauth2" -> OAuth2SecurityScheme.serializer()
            "openIdConnect" -> OpenIdConnectSecurityScheme.serializer()
            "mutualTLS" -> MutualTLSSecurityScheme.serializer()
            else -> error("Unknown SecurityScheme type: $type")
        }
    }
}

internal object PartSerializer : JsonContentPolymorphicSerializer<Part>(Part::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Part> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: error("Missing 'kind' field in Part")

        return when (kind) {
            "text" -> TextPart.serializer()
            "file" -> FilePart.serializer()
            "data" -> DataPart.serializer()
            else -> error("Unknown Part kind: $kind")
        }
    }
}

internal object FileSerializer : JsonContentPolymorphicSerializer<File>(File::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<File> {
        val jsonObject = element.jsonObject

        return when {
            "bytes" in jsonObject -> FileWithBytes.serializer()
            "uri" in jsonObject -> FileWithUri.serializer()
            else -> error("Unknown File type")
        }
    }
}

internal object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Event> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: error("Missing 'kind' field in Event")

        return when (kind) {
            "status-update" -> TaskStatusUpdateEvent.serializer()
            "artifact-update" -> TaskArtifactUpdateEvent.serializer()
            "task" -> Task.serializer()
            "message" -> Message.serializer()
            else -> error("Unknown kind: $kind")
        }
    }
}

internal object CommunicationEventSerializer : JsonContentPolymorphicSerializer<CommunicationEvent>(CommunicationEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CommunicationEvent> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: error("Missing 'kind' field in CommunicationEvent")

        return when (kind) {
            "task" -> Task.serializer()
            "message" -> Message.serializer()
            else -> error("Unknown kind: $kind")
        }
    }
}

internal object TaskEventSerializer : JsonContentPolymorphicSerializer<TaskEvent>(TaskEvent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TaskEvent> {
        val jsonObject = element.jsonObject
        val kind = jsonObject["kind"]?.jsonPrimitive?.content ?: error("Missing 'kind' field in TaskEvent")

        return when (kind) {
            "task" -> Task.serializer()
            "status-update" -> TaskStatusUpdateEvent.serializer()
            "artifact-update" -> TaskArtifactUpdateEvent.serializer()
            else -> error("Unknown kind: $kind")
        }
    }
}
