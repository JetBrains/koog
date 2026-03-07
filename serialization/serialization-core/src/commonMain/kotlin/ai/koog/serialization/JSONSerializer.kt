package ai.koog.serialization

/**
 * Serializer for converting values to and from JSON.
 *
 * This is the core abstraction for JSON serialization/deserialization operations in Koog.
 * It provides a library-agnostic API that can be backed by different serialization libraries, such as
 * kotlinx-serialization or Jackson.
 *
 * All methods use [TypeToken] to capture runtime type information.
 * Use the [typeToken] factory functions to create tokens easily.
 *
 * ### Encoding and decoding to/from JSON strings
 *
 * ```kotlin
 * val serializer: JSONSerializer = KotlinxSerializer()
 *
 * // Encode a data class to a JSON string
 * @Serializable
 * data class User(val name: String, val age: Int)
 *
 * val json: String = serializer.encodeToString(User("Alice", 30), typeToken<User>())
 * // json == """{"name":"Alice","age":30}"""
 *
 * // Decode a JSON string back to a data class
 * val user: User = serializer.decodeFromString(json, typeToken<User>())
 * // user == User("Alice", 30)
 * ```
 *
 * ### Working with [JSONElement] (library-agnostic JSON tree)
 *
 * ```kotlin
 * val serializer: JSONSerializer = KotlinxSerializer()
 *
 * // Encode a value to a JSONElement tree
 * val element: JSONElement = serializer.encodeToJSONElement(User("Bob", 25), typeToken<User>())
 *
 * // Decode a JSONElement tree back to a value
 * val user: User = serializer.decodeFromJSONElement(element, typeToken<User>())
 * ```
 *
 * ### Converting between JSON strings and [JSONElement]
 *
 * ```kotlin
 * val serializer: JSONSerializer = KotlinxSerializer()
 *
 * val jsonString = """{"key": "value"}"""
 * val element: JSONElement = serializer.decodeJSONElementFromString(jsonString)
 * val backToString: String = serializer.encodeJSONElementToString(element)
 * ```
 *
 * ### Java usage with [TypeToken]
 * ```java
 * JSONSerializer serializer = new JacksonSerializer();
 *
 * // Non-generic types
 * String json = serializer.encodeToString(user, TypeToken.of(User.class));
 * User decoded = serializer.decodeFromString(json, TypeToken.of(User.class));
 *
 * // Generic types: using TypeCapture to preserve type parameters
 * List<String> list = serializer.decodeFromString(
 *     jsonString,
 *     TypeToken.of(new TypeCapture<List<String>>() {})
 * );
 * ```
 *
 * @see TypeToken for runtime type representation used by all methods
 * @see JSONElement for the library-agnostic JSON tree model
 */
public interface JSONSerializer {
    /**
     * Serializes a value to its JSON representation.
     *
     * @param value value to serialize
     * @param typeToken token capturing type information of [T]
     * @return JSON string representation of the value
     */
    public fun <T> encodeToString(value: T, typeToken: TypeToken): String

    /**
     * Deserializes a JSON string to a value of type [T].
     *
     * @param value JSON string to deserialize
     * @param typeToken token capturing type information of [T]
     * @return deserialized value of type [T]
     */
    public fun <T> decodeFromString(value: String, typeToken: TypeToken): T

    /**
     * Serializes a value to its [JSONElement] representation.
     *
     * @param value value to serialize
     * @param typeToken token capturing type information of [T]
     * @return [JSONElement] representation of the value
     */
    public fun <T> encodeToJSONElement(value: T, typeToken: TypeToken): JSONElement

    /**
     * Deserializes [JSONElement] to a value of type [T].
     *
     * @param value [JSONElement] to deserialize.
     * @param typeToken token capturing type information of [T]
     * @return deserialized value of type [T]
     */
    public fun <T> decodeFromJSONElement(value: JSONElement, typeToken: TypeToken): T

    /**
     * Serializes a [JSONElement] to its JSON string representation.
     *
     * @param value [JSONElement] to serialize
     * @return JSON string representation of the value
     */
    public fun encodeJSONElementToString(value: JSONElement): String =
        encodeToString(value, typeToken = typeToken<JSONElement>())

    /**
     * Deserializes a JSON string to a [JSONElement].
     *
     * @param value JSON string to deserialize
     * @return deserialized [JSONElement]
     */
    public fun decodeJSONElementFromString(value: String): JSONElement =
        decodeFromString(value, typeToken = typeToken<JSONElement>())
}
