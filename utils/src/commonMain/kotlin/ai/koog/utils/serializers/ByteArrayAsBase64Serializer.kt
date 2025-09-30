package ai.koog.utils.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A custom serializer for serializing and deserializing `ByteArray` objects
 * as Base64-encoded strings.
 *
 * Uses the `Base64.Default` encoding and decoding mechanisms from the
 * kotlinx-serialization library to transform `ByteArray` values into readable
 * Base64 string representation during serialization and to decode Base64 strings
 * back into byte arrays during deserialization.
 *
 * The underlying serialized form is described as a primitive value of kind `STRING`.
 *
 * This serializer is particularly useful when working with data that needs to
 * be encoded as Base64 in JSON or other text-based serialization formats.
 */
@OptIn(ExperimentalEncodingApi::class)
public class ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    private val base64 = Base64.Default

    /**
     * The serial descriptor associated with the `ByteArrayAsBase64Serializer`.
     *
     * Represents a primitive value with the kind `STRING`.
     * Used to describe the serialized form of a ByteArray value
     * encoded as a Base64 string.
     */
    override val descriptor: SerialDescriptor
        get() =
            PrimitiveSerialDescriptor(
                "ByteArrayAsBase64Serializer",
                PrimitiveKind.STRING,
            )

    /**
     * Serializes a [ByteArray] into a Base64-encoded string.
     *
     * @param encoder The encoder used for encoding the Base64 string.
     * @param value The [ByteArray] that needs to be serialized.
     */
    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        val base64Encoded = base64.encode(value)
        encoder.encodeString(base64Encoded)
    }

    /**
     * Deserializes a Base64-encoded string into a [ByteArray].
     *
     * Reads the Base64-encoded string from the given decoder, decodes it, and
     * returns the resulting [ByteArray].
     *
     * @param decoder The decoder used to read the Base64-encoded string.
     * @return The decoded [ByteArray].
     */
    override fun deserialize(decoder: Decoder): ByteArray {
        val base64Decoded = decoder.decodeString()
        return base64.decode(base64Decoded)
    }
}
