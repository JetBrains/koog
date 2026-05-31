package ai.koog.a2a.serialization

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FileBytesPart
import ai.koog.a2a.model.FileUrlPart
import ai.koog.a2a.model.OAuthFlows
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.SendMessageResponse
import ai.koog.a2a.model.StreamResponse
import ai.koog.a2a.model.TextPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass

/**
 * Serializer that selects a subclass based on the presence of a discriminator property in the JSON object.
 *
 * @param baseClass The base class to be used for deserialization.
 * @param subclasses A map of discriminator property names to their corresponding serializer classes.
 */
public abstract class PropertyPresencePolymorphicSerializer<T : Any>(
    private val baseClass: KClass<T>,
    private val subclasses: Map<String, KSerializer<out T>>,
) : JsonContentPolymorphicSerializer<T>(baseClass) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out T> {
        val obj = element as? JsonObject
            ?: throw SerializationException("Expected JSON object for polymorphic value")

        val matching = subclasses.filterKeys { it in obj }

        return when (matching.size) {
            1 ->
                matching.values.single()

            0 ->
                throw SerializationException(
                    "Unknown ${baseClass.simpleName} variant. Expected one of the ${subclasses.keys} to be present as property, found ${obj.keys}"
                )

            else ->
                throw SerializationException("Ambiguous ${baseClass.simpleName} variant. Multiple discriminator properties found: ${matching.keys}")
        }
    }
}

public object SendMessageResponseSerializer : PropertyPresencePolymorphicSerializer<SendMessageResponse>(
    baseClass = SendMessageResponse::class,
    subclasses = mapOf(
        "task" to SendMessageResponse.TaskResponse.serializer(),
        "message" to SendMessageResponse.MessageResponse.serializer(),
    )
)

public object StreamResponseSerializer : PropertyPresencePolymorphicSerializer<StreamResponse>(
    baseClass = StreamResponse::class,
    subclasses = mapOf(
        "task" to StreamResponse.TaskResponse.serializer(),
        "message" to StreamResponse.MessageResponse.serializer(),
        "statusUpdate" to StreamResponse.TaskStatusUpdateEventResponse.serializer(),
        "artifactUpdate" to StreamResponse.TaskArtifactUpdateEventResponse.serializer(),
    )
)

public object PartSerializer : PropertyPresencePolymorphicSerializer<Part>(
    baseClass = Part::class,
    subclasses = mapOf(
        "text" to TextPart.serializer(),
        "raw" to FileBytesPart.serializer(),
        "url" to FileUrlPart.serializer(),
        "data" to DataPart.serializer(),
    )
)

public object OAuthFlowsSerializer : PropertyPresencePolymorphicSerializer<OAuthFlows>(
    baseClass = OAuthFlows::class,
    subclasses = mapOf(
        "authorizationCode" to OAuthFlows.AuthorizationCode.serializer(),
        "clientCredentials" to OAuthFlows.ClientCredentials.serializer(),
        "implicit" to OAuthFlows.Implicit.serializer(),
        "password" to OAuthFlows.Password.serializer(),
    )
)

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
