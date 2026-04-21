package ai.koog.serialization.kotlinx

import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONLiteral
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

//region kotlinx-serialization to Koog serialization

/**
 * Converts kotlinx-serialization [JsonElement] to [ai.koog.serialization.JSONElement].
 */
public fun JsonElement.toKoogJSONElement(): JSONElement = when (this) {
    is JsonObject -> toKoogJSONObject()
    is JsonArray -> toKoogJSONArray()
    is JsonPrimitive -> toKoogJSONPrimitive()
}

/**
 * Converts kotlinx-serialization [JsonObject] to [ai.koog.serialization.JSONObject].
 */
public fun JsonObject.toKoogJSONObject(): JSONObject = JSONObject(
    entries = mapValues { (_, value) -> value.toKoogJSONElement() }
)

/**
 * Converts kotlinx-serialization [JsonArray] to [ai.koog.serialization.JSONArray].
 */
public fun JsonArray.toKoogJSONArray(): JSONArray = JSONArray(
    elements = map { it.toKoogJSONElement() }
)

/**
 * Converts kotlinx-serialization [JsonPrimitive] to [ai.koog.serialization.JSONPrimitive].
 */
public fun JsonPrimitive.toKoogJSONPrimitive(): JSONPrimitive = when (this) {
    is JsonNull -> JSONNull
    else -> JSONLiteral(content = this.content, isString = this.isString)
}

//endregion

//region Koog serialization to kotlinx-serialization

/**
 * Converts [JSONElement] to kotlinx-serialization [JsonElement].
 */
public fun JSONElement.toKotlinxJsonElement(): JsonElement = when (this) {
    is JSONObject -> toKotlinxJsonObject()
    is JSONArray -> toKotlinxJsonArray()
    is JSONPrimitive -> toKotlinxJsonPrimitive()
}

/**
 * Converts [JSONObject] to kotlinx-serialization [JsonObject].
 */
public fun JSONObject.toKotlinxJsonObject(): JsonObject = buildJsonObject {
    entries.forEach { (key, value) ->
        put(key, value.toKotlinxJsonElement())
    }
}

/**
 * Converts [JSONArray] to kotlinx-serialization [JsonArray].
 */
public fun JSONArray.toKotlinxJsonArray(): JsonArray = buildJsonArray {
    elements.forEach { element ->
        add(element.toKotlinxJsonElement())
    }
}

/**
 * Converts [JSONPrimitive] to kotlinx-serialization [JsonPrimitive].
 */
public fun JSONPrimitive.toKotlinxJsonPrimitive(): JsonPrimitive = when (this) {
    is JSONNull -> JsonNull

    is JSONLiteral -> if (isString) {
        JsonPrimitive(content)
    } else {
        JsonUnquotedLiteral(content)
    }
}

//endregion
