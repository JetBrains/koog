package ai.koog.prompt.structure.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Provides utilities for handling JSON structures using Kotlin Serialization.
 * This class facilitates JSON serialization and deserialization with options for
 * custom configurations and pretty-printing.
 *
 * @property json The instance of [Json] used for JSON operations. Defaults to [defaultJson].
 */
public class JsonStructureLanguage(
    private val json: Json = defaultJson
) {
    /**
     * Companion object for the `JsonStructureLanguage` class. Provides shared configurations
     * and utilities for JSON handling and structure definition.
     */
    public companion object {
        /**
         * A configuration object for the Kotlinx Serialization JSON library. This instance is pre-configured
         * with specific behaviors for JSON serialization and deserialization, such as handling unknown keys,
         * encoding default values, omitting null values, and lenient parsing.
         *
         * Properties included in this configuration:
         * - `ignoreUnknownKeys`: Allows deserialization to ignore unknown keys in the JSON input.
         * - `encodeDefaults`: Ensures that default values of properties are included during serialization.
         * - `explicitNulls`: Disables explicit representation of `null` values in the serialized JSON.
         * - `isLenient`: Permits relaxed JSON parsing, such as accepting unquoted strings or comments.
         * - `coerceInputValues`: Automatically coerces incompatible or missing input values to defaults.
         * - `classDiscriminator`: Specifies the property name used for polymorphic JSON type discrimination.
         */
        public val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = true
            coerceInputValues = true
            classDiscriminator = "kind"
        }
    }

    /**
     * A Json instance configured for pretty-printing JSON output.
     *
     * This instance uses the default behavior from the provided `json`
     * instance and enables pretty printing with a specified indentation level.
     * It is intended for generating human-readable JSON representations.
     */
    private val jsonPretty = Json(json) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Serializes the given value of type [T] into its JSON string representation using the specified [serializer].
     *
     * @param T The type of the value to serialize.
     * @param value The value to be serialized into JSON.
     * @param serializer The serializer used to convert the value of type [T] into a JSON string.
     * @return A JSON string representation of the given value.
     */
    public fun <T> string(value: T, serializer: KSerializer<T>): String = json.encodeToString(serializer, value)

    /**
     * Serializes a given value of type [T] into its JSON string representation using the default serializer.
     *
     * @param T The type of the object to be serialized.
     * @param value The value to be serialized into a JSON string.
     * @return A JSON string representation of the provided value.
     */
    public inline fun <reified T> string(value: T): String = string(value, serializer<T>())

    /**
     * Serializes the given value into a pretty-printed JSON string using the provided serializer.
     *
     * @param T The type of the value to be serialized.
     * @param value The value to serialize into a pretty-printed JSON string.
     * @param serializer The serializer used to convert the value into JSON format.
     * @return A pretty-printed JSON string representation of the given value.
     */
    public fun <T> pretty(value: T, serializer: KSerializer<T>): String = jsonPretty.encodeToString(serializer, value)

    /**
     * Converts the given value into a pretty-printed JSON string representation.
     *
     * @param T The type of the value to be converted.
     * @param value The value to be serialized and formatted as a pretty JSON string.
     * @return A pretty-printed JSON string representation of the provided value.
     */
    public inline fun <reified T> pretty(value: T): String = pretty(
        value,
        serializer<T>()
    )

    /**
     * Parses the given text into an object of type T using the provided serializer.
     *
     * @param text The input string to be deserialized.
     * @param serializer The serializer used to deserialize the input text.
     * @return The deserialized object of type T.
     */
    public fun <T> parse(text: String, serializer: KSerializer<T>): T = json.decodeFromString(serializer, cleanup(text))

    /**
     * Cleans up a text string by removing unnecessary lines and retaining only lines
     * that resemble JSON-compatible structures or primitives.
     *
     * @param text The input text string to be cleaned.
     * @return A cleaned string containing only JSON-related lines or primitives.
     */
    private fun cleanup(text: String): String {
        // cleanup some lines that are not json
        var lines = text.lines().map { it.trim() }
        lines = lines.filter { it.isNotBlank() }
        lines = lines.filter {
            val start = it.firstOrNull() ?: return@filter false
            val isStructureStart = start in setOf('{', '[', '"', '}', ']', '\'')
            val isDigit = start.isDigit() || start == '-'
            val isPrimitive = it.startsWith("true") || it.startsWith("false") || it.startsWith("null")
            isStructureStart || isDigit || isPrimitive
        }
        val content = lines.joinToString("\n")
        return content
    }

    /**
     * Parses the given JSON string into an object of the specified type.
     *
     * @param text The JSON string to be parsed.
     * @return The parsed object of type T.
     */
    public inline fun <reified T> parse(text: String): T = parse(text, serializer<T>())
}
