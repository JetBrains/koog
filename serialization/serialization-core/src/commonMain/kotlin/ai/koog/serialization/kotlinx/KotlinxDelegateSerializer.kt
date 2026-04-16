package ai.koog.serialization.kotlinx

import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder

/**
 * This is a bridge between library-agnostic [JSONSerializer] and implementations that use kotlinx-serialization.
 *
 * It is convenient to use one particular serialization library, but sometimes external user inputs have to
 * be mixed into internal data structures. This delegate addresses the problem, allowing us to continue using kotlinx-serialization
 * while delegating serialization of user defined data to the provided [JSONSerializer].
 *
 * Example:
 * ```kotlin
 * val outputType: TypeToken = ... // we know Output type token
 * val serializer: JSONSerializer = ... // and some serializer was configured, e.g. JacksonSerializer
 *
 * @Serializable
 * public data class AgentToolResult<Output>(
 *     val successful: Boolean,
 *     val errorMessage: String? = null,
 *     val result: Output? = null // mixed user data with generic type
 * )
 *
 * // Then, given a configured JSONSerializer, we can still use kotlinx-serialization internally
 * val rawResult: JsonElement = ...
 * json.decodeFromJsonElement(
 *     deserializer = AgentToolResult.serializer(
 *         KotlinxDelegateSerializer(serializer, outputType)
 *     ),
 *     element = rawResult.toKotlinxJsonElement(),
 * )
 *
 * // The same with deserialization
 * val result: AgentToolResult<Output> = ...
 * json.encodeToJsonElement(
 *     serializer = AgentToolResult.serializer(
 *         KotlinxDelegateSerializer(serializer, outputType)
 *     ),
 *     value = result,
 * ).toKoogJSONElement()
 * ```
 */
@InternalKoogSerializationApi
public class KotlinxDelegateSerializer<T>(
    private val delegate: JSONSerializer,
    private val typeToken: TypeToken,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("KotlinxDelegateSerializer ")

    override fun serialize(encoder: Encoder, value: T) {
        val jsonEncoder = (encoder as? JsonEncoder) ?: throw IllegalArgumentException("Encoder must be JsonEncoder")

        val valueJson = delegate.encodeToJSONElement(value, typeToken).toKotlinxJsonElement()
        jsonEncoder.encodeJsonElement(valueJson)
    }

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder = (decoder as? JsonDecoder) ?: throw IllegalArgumentException("Decoder must be JsonEncoder")

        val valueJson = jsonDecoder.decodeJsonElement().toKoogJSONElement()
        return delegate.decodeFromJSONElement(valueJson, typeToken)
    }
}
