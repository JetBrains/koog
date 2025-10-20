package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.ToolResult.AsTextSerializer
import ai.koog.agents.core.tools.ToolResult.TextSerializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

/**
 * Utility class for handling results related to tools and their serialization mechanisms.
 */
public class ToolResultUtils {
    /**
     * Companion object containing utility methods for handling specialized serialization mechanisms.
     */
    public companion object {
        /**
         * Creates an instance of `AsTextSerializer` for the specified type `T` that extends `TextSerializable`.
         * The resulting serializer facilitates the conversion of objects implementing the `TextSerializable` interface
         * into a textual format, utilizing their predefined serialization logic.
         *
         * @return An `AsTextSerializer` instance for the type `T`, providing serialization and deserialization support.
         */
        public inline fun <reified T : TextSerializable> toTextSerializer(): KSerializer<T> =
            AsTextSerializer(serializer())
    }
}

/**
 * A generic simple serializer for serializing objects of type [T] into their string representation using a custom converter.
 *
 * @param T The type of the object to be serialized.
 * @property descriptor The serial descriptor for the serialization, defaulting to a primitive string type.
 * @property toStringConverter A lambda function that takes an object of type [T] and returns its string representation.
 *
 * This serializer only supports serialization and does not allow for deserialization of data.
 */
public open class ToolResultStringSerializer<T>(
    final override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ToolResultString", PrimitiveKind.STRING),
    private val toStringConverter: (T) -> String,
) : KSerializer<T> {

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(toStringConverter(value))
    }

    final override fun deserialize(decoder: Decoder): T {
        throw UnsupportedOperationException("Deserialization is not supported")
    }
}
