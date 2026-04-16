package ai.koog.serialization.kotlinx

import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Serializer that uses kotlinx-serialization
 *
 * @property json Kotlinx Json instance to use for serialization/deserialization
 */
public class KotlinxSerializer(
    public val json: Json = Json.Default,
) : JSONSerializer {
    override fun <T> encodeToString(value: T, typeToken: TypeToken): String {
        return json.encodeToString(resolveSerializer(typeToken, json), value)
    }

    override fun <T> decodeFromString(value: String, typeToken: TypeToken): T {
        return json.decodeFromString(resolveSerializer(typeToken, json), value)
    }

    override fun <T> encodeToJSONElement(value: T, typeToken: TypeToken): JSONElement {
        return json.encodeToJsonElement(resolveSerializer(typeToken, json), value).toKoogJSONElement()
    }

    override fun <T> decodeFromJSONElement(value: JSONElement, typeToken: TypeToken): T {
        return json.decodeFromJsonElement(resolveSerializer(typeToken, json), value.toKotlinxJsonElement())
    }
}

internal expect fun <T> resolveSerializer(typeToken: TypeToken, json: Json): KSerializer<T>
