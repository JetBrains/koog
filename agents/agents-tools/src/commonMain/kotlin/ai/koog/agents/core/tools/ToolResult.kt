package ai.koog.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Represents a result produced by a tool operation. This is a marker interface implemented by various result types.
 */
public interface ToolResult {
    private companion object {
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
        public constructor(e: Exception) : this("Failed with exception '${e::class.simpleName}' and message '${e.message}'")

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
            public inline fun build(block: StringBuilder.() -> Unit): Text = Text(StringBuilder().apply(block).toString())
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
    public interface JSONSerializable<T : JSONSerializable<T>> : ToolResult {
        /**
         * Retrieves the serializer instance for the implementing class.
         *
         * @return The serializer of type KSerializer<T> specific to the class.
         */
        public fun getSerializer(): KSerializer<T>

        override fun toStringDefault(): String = json.encodeToString(getSerializer(), this as T)
    }
}
