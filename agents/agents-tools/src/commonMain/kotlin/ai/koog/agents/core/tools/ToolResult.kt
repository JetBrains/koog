package ai.koog.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

/**
 * Represents a result produced by a tool operation. This is a marker interface implemented by various result types.
 */
@Deprecated("Extending ToolResult is no longer required. Tool results are entirely handled by KotlinX Serialization.")
public interface ToolResult {
    /**
     * Companion object for the enclosing class.
     *
     * Provides utility functionalities, including methods to handle and interact with
     * objects of types implementing the `TextSerializable` interface. It includes support
     * for creating a text-based serializer for the objects using the `AsTextSerializer`
     * class and a pre-configured `Json` instance for serialization with customizable options.
     */
    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }
    }

    /**
     * Provides a string representation of the implementing instance with default formatting.
     *
     * @return A string representation of the object.
     */
    public fun toStringDefault(): String

    /**
     * Result implementation representing a simple tool result, just a string.
     */
    @Deprecated("Extending ToolResult.Text is no longer required (just use plain String class instead). Tool results are entirely handled by KotlinX Serialization.")
    @Serializable
    @JvmInline
    public value class Text(public val text: String) : JSONSerializable<Text> {
        override fun getSerializer(): KSerializer<Text> = serializer()

        /**
         * Constructs a [Text] instance with a message generated from the given exception.
         *
         * The message is built using the exception's class name and its message.
         *
         * @param e The exception from which to generate the message.
         */
        public constructor(
            e: Exception
        ) : this("Failed with exception '${e::class.simpleName}' and message '${e.message}'")

        /**
         * Companion object for the [Text] class providing utility functions.
         */
        public companion object {
            /**
             * Builds a [Text] object by applying the given block to a [StringBuilder].
             *
             * @param block A lambda that operates on a [StringBuilder] to construct the text content.
             * @return A [Text] instance containing the constructed string.
             */
            public inline fun build(
                block: StringBuilder.() -> Unit
            ): Text = Text(StringBuilder().apply(block).toString())
        }

        override fun toStringDefault(): String = text
    }

    /**
     * A custom inline value class that wraps a `kotlin.Boolean` to provide additional functionality or semantics.
     *
     * The `Boolean` value class is used to represent a logical value, either `true` or `false`, with the added capability
     * of being part of a custom implementation.
     *
     * @property result The internal `kotlin.Boolean` value representing the logical state.
     */
    @Deprecated("Extending ToolResult.Boolean is no longer required (just use plain Boolean class instead). Tool results are entirely handled by KotlinX Serialization.")
    @JvmInline
    public value class Boolean(public val result: kotlin.Boolean) : ToolResult {
        /**
         * Companion object that provides constants for Boolean values.
         */
        public companion object {
            /**
             * Represents the boolean value `true`.
             *
             * This constant is a predefined instance of the `Boolean` value class indicating a `true` result.
             * It is used to signify a positive or affirmative condition.
             */
            public val TRUE: Boolean = Boolean(true)

            /**
             * Represents the boolean constant `false` in the custom `Boolean` value class.
             * It is a pre-defined instance of the `Boolean` type with its internal value set to `false`.
             */
            public val FALSE: Boolean = Boolean(false)
        }

        override fun toStringDefault(): String = result.toString()
    }

    /**
     * Represents a numeric value as a tool result.
     *
     * This value class wraps a `kotlin.Number` instance and implements the `ToolResult` interface,
     * allowing seamless representation of numerical results in a standardized format.
     *
     * @property result The underlying numeric value.
     */
    @Deprecated("Extending ToolResult.Number is no longer required (just use plain Int/Double/... classes instead). Tool results are entirely handled by KotlinX Serialization.")
    @JvmInline
    public value class Number(public val result: kotlin.Number) : ToolResult {
        override fun toStringDefault(): String = result.toString()
    }

    /**
     * Represents an interface that provides functionality for serializing implementing classes into JSON format
     * using kotlinx.serialization library.
     *
     * @param T The type of the implementing class, which must also be JSONSerializable.
     */
    @Deprecated("Extending ToolResult.JSONSerializable<T> is no longer required (just use T type directly and mark it as `@Serializable`). Tool results are entirely handled by KotlinX Serialization.")
    public interface JSONSerializable<T : JSONSerializable<T>> : ToolResult {
        /**
         * Retrieves the serializer instance for the implementing class.
         *
         * @return The serializer of type KSerializer<T> specific to the class.
         */
        public fun getSerializer(): KSerializer<T>

        override fun toStringDefault(): String = json.encodeToString(getSerializer(), this as T)
    }

    /**
     * A serializer that converts an object implementing the TextSerializable interface into a textual format
     * by utilizing the `textForLLM` function defined in the interface.
     *
     * This serializer provides functionality to encode the object into a string format suitable for textual
     * representation and decodes it using a provided serializer for the type.
     *
     * @param T The type of object being serialized/deserialized, constrained to types implementing TextSerializable.
     * @param valueSerializer The serializer responsible for handling the underlying type operations.
     */
    public open class AsTextSerializer<T : TextSerializable>(
        private val valueSerializer: KSerializer<T>
    ) : KSerializer<T> {
        /**
         * The descriptor that provides metadata about the serialized form of the value.
         * This descriptor corresponds to the valueSerializer's descriptor, defining the
         * structure of the serialized data and associated information like kind and elements.
         */
        override val descriptor: SerialDescriptor = valueSerializer.descriptor

        /**
         * Serializes an object of type `T` into its textual representation.
         *
         * This method uses the provided encoder to encode the `textForLLM` representation of the `value`.
         *
         * @param encoder The encoder used to serialize the object.
         * @param value The object of type `T` to be serialized.
         */
        override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeString(value.textForLLM())
        }

        /**
         * Deserializes the provided encoded data using the specified decoder and returns the resulting object of type T.
         *
         * @param decoder The decoder to read the serialized data from.
         * @return The deserialized object of type T.
         */
        override fun deserialize(decoder: Decoder): T {
            return valueSerializer.deserialize(decoder)
        }
    }

    /**
     * Abstract class representing a text-serializable object.
     * This class provides a contract for converting an object to a text representation
     * that can be utilized by language learning models (LLMs) or other text-processing systems.
     */
    @Serializable
    public abstract class TextSerializable {
        /**
         * Abstract method to retrieve a text representation for integration with language learning models.
         *
         * @return a string that represents the textual content to be used by language learning models.
         */
        public abstract fun textForLLM(): String
    }
}
