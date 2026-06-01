package ai.koog.a2a.serialization

import ai.koog.a2a.model.APIKeySecurityScheme
import ai.koog.a2a.model.AuthorizationCodeOAuthFlow
import ai.koog.a2a.model.ClientCredentialsOAuthFlow
import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.DeviceCodeOAuthFlow
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.FileBytesPart
import ai.koog.a2a.model.FileUrlPart
import ai.koog.a2a.model.HTTPAuthSecurityScheme
import ai.koog.a2a.model.ImplicitOAuthFlow
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MutualTLSSecurityScheme
import ai.koog.a2a.model.OAuth2SecurityScheme
import ai.koog.a2a.model.OAuthFlow
import ai.koog.a2a.model.OpenIdConnectSecurityScheme
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.PasswordOAuthFlow
import ai.koog.a2a.model.ResponseEvent
import ai.koog.a2a.model.SecurityScheme
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass

/**
 * Polymorphic JSON serializer that selects a subclass based on the presence of a discriminator property in the JSON object.
 *
 * @param baseClass The base class to be used for deserialization.
 * @param variants A map of discriminator property names to their corresponding serializer classes.
 */
public abstract class PropertyPresencePolymorphicSerializer<T : Any>(
    private val baseClass: KClass<T>,
    private val variants: Map<String, KSerializer<out T>>,
) : JsonContentPolymorphicSerializer<T>(baseClass) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out T> {
        val obj = element as? JsonObject
            ?: throw SerializationException("Expected JSON object for polymorphic value")

        val matching = variants.filterKeys { it in obj }

        return when (matching.size) {
            1 ->
                matching.values.single()

            0 ->
                throw SerializationException(
                    "Unknown ${baseClass.simpleName} variant. Expected one of the ${variants.keys} to be present as property, found ${obj.keys}"
                )

            else ->
                throw SerializationException("Ambiguous ${baseClass.simpleName} variant. Multiple discriminator properties found: ${matching.keys}")
        }
    }
}

/**
 * Polymorphic JSON serializer that represents each value as a single-property object.
 *
 * The property name identifies the variant, and the property value contains the
 * value serialized with the corresponding variant serializer.
 *
 * @param baseClass The polymorphic base class.
 * @param variants Map of discriminator property names to serializers for supported variants.
 */
public abstract class PropertyWrappingPolymorphicSerializer<T : Any>(
    private val baseClass: KClass<T>,
    private val variants: Map<String, KSerializer<out T>>
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PropertyWrappingPolymorphicSerializer<${baseClass.simpleName}>")

    override fun serialize(encoder: Encoder, value: T) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Can only serialize JSON")
        val subClassName = value::class.simpleName ?: value::class.toString()

        val actualSerializer = encoder.serializersModule
            .getPolymorphic(baseClass, value)
            ?: throw SerializationException(
                "Class '$subClassName' is not registered for polymorphic serialization in the scope of ${baseClass.simpleName}.\n" +
                    "Mark the base class as 'sealed' or register the serializer explicitly."
            )

        val variant = variants
            .filterValues { it == actualSerializer }.keys
            .let { matchingVariants ->
                matchingVariants
                    .singleOrNull()
                    ?: throw SerializationException(
                        "Expected to match exactly one of the ${baseClass.simpleName} variants for provided $subClassName, but found: $matchingVariants"
                    )
            }

        val serializedValue = jsonEncoder.json.encodeToJsonElement(actualSerializer, value)
        val wrappedValue = buildJsonObject {
            put(variant, serializedValue)
        }

        encoder.encodeJsonElement(wrappedValue)
    }

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Can only deserialize JSON")
        val jsonElement = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("Expected JSON object")

        val variant = jsonElement.keys.singleOrNull()
            ?: throw SerializationException("Expected exactly one discriminator property")
        val actualSerializer = variants[variant]
            ?: throw SerializationException("Unknown discriminator property: $variant")

        val serializedValue = jsonElement[variant]!!

        return jsonDecoder.json.decodeFromJsonElement(actualSerializer, serializedValue)
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

public object PartSerializer : PropertyPresencePolymorphicSerializer<Part>(
    baseClass = Part::class,
    variants = mapOf(
        TextPart.DISCRIMINATOR to TextPart.serializer(),
        FileBytesPart.DISCRIMINATOR to FileBytesPart.serializer(),
        FileUrlPart.DISCRIMINATOR to FileUrlPart.serializer(),
        DataPart.DISCRIMINATOR to DataPart.serializer(),
    )
)

public object EventSerializer : PropertyWrappingPolymorphicSerializer<Event>(
    baseClass = Event::class,
    variants = mapOf(
        Task.KIND to Task.serializer(),
        Message.KIND to Message.serializer(),
        TaskStatusUpdateEvent.KIND to TaskStatusUpdateEvent.serializer(),
        TaskArtifactUpdateEvent.KIND to TaskArtifactUpdateEvent.serializer(),
    )
)

public object ResponseEventSerializer : PropertyWrappingPolymorphicSerializer<ResponseEvent>(
    baseClass = ResponseEvent::class,
    variants = mapOf(
        Task.KIND to Task.serializer(),
        Message.KIND to Message.serializer(),
    )
)

public object TaskEventSerializer : PropertyWrappingPolymorphicSerializer<TaskEvent>(
    baseClass = TaskEvent::class,
    variants = mapOf(
        Task.KIND to Task.serializer(),
        TaskStatusUpdateEvent.KIND to TaskStatusUpdateEvent.serializer(),
        TaskArtifactUpdateEvent.KIND to TaskArtifactUpdateEvent.serializer(),
    )
)

public object SecuritySchemeSerializer : PropertyWrappingPolymorphicSerializer<SecurityScheme>(
    baseClass = SecurityScheme::class,
    variants = mapOf(
        APIKeySecurityScheme.KIND to APIKeySecurityScheme.serializer(),
        HTTPAuthSecurityScheme.KIND to HTTPAuthSecurityScheme.serializer(),
        OAuth2SecurityScheme.KIND to OAuth2SecurityScheme.serializer(),
        OpenIdConnectSecurityScheme.KIND to OpenIdConnectSecurityScheme.serializer(),
        MutualTLSSecurityScheme.KIND to MutualTLSSecurityScheme.serializer(),
    )
)

@Suppress("DEPRECATION")
public object OAuthFlowSerializer : PropertyWrappingPolymorphicSerializer<OAuthFlow>(
    baseClass = OAuthFlow::class,
    variants = mapOf(
        AuthorizationCodeOAuthFlow.KIND to AuthorizationCodeOAuthFlow.serializer(),
        ClientCredentialsOAuthFlow.KIND to ClientCredentialsOAuthFlow.serializer(),
        ImplicitOAuthFlow.KIND to ImplicitOAuthFlow.serializer(),
        PasswordOAuthFlow.KIND to PasswordOAuthFlow.serializer(),
        DeviceCodeOAuthFlow.KIND to DeviceCodeOAuthFlow.serializer(),
    )
)
