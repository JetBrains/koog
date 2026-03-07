@file:JvmName("JacksonJSONElementMappers")

package ai.koog.serialization.jackson

import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONLiteral
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ValueNode
import com.fasterxml.jackson.databind.util.RawValue

//region Jackson to Koog serialization

/**
 * Converts Jackson [JsonNode] to [JSONElement].
 */
public fun JsonNode.toKoogJSONElement(): JSONElement = when (this) {
    is ObjectNode -> toKoogJSONObject()
    is ArrayNode -> toKoogJSONArray()
    is ValueNode -> toKoogJSONPrimitive()
    else -> throw IllegalArgumentException("Unsupported JsonNode type: ${this::class.simpleName}")
}

/**
 * Converts Jackson [ObjectNode] to [JSONObject].
 */
public fun ObjectNode.toKoogJSONObject(): JSONObject {
    return JSONObject(
        entries = this.fieldNames().asSequence().associateWith { fieldName ->
            this.get(fieldName).toKoogJSONElement()
        }
    )
}

/**
 * Converts Jackson [ArrayNode] to [JSONArray].
 */
public fun ArrayNode.toKoogJSONArray(): JSONArray {
    return JSONArray(
        elements = map { it.toKoogJSONElement() }
    )
}

/**
 * Converts Jackson primitive [JsonNode] to [JSONPrimitive].
 */
public fun ValueNode.toKoogJSONPrimitive(): JSONPrimitive = when {
    isNull -> JSONNull
    isTextual -> JSONLiteral(asText(), isString = true)
    else -> JSONLiteral(asText(), isString = false)
}

//endregion

//region Koog serialization to Jackson

/**
 * Converts [JSONElement] to Jackson [JsonNode].
 */
public fun JSONElement.toJacksonJsonNode(): JsonNode = when (this) {
    is JSONObject -> toJacksonObjectNode()
    is JSONArray -> toJacksonArrayNode()
    is JSONPrimitive -> toJacksonJsonNode()
}

/**
 * Converts [JSONObject] to Jackson [ObjectNode].
 */
public fun JSONObject.toJacksonObjectNode(): ObjectNode {
    val objectNode = JsonNodeFactory.instance.objectNode()
    entries.forEach { (key, value) ->
        objectNode.set<JsonNode>(key, value.toJacksonJsonNode())
    }
    return objectNode
}

/**
 * Converts [JSONArray] to Jackson [ArrayNode].
 */
public fun JSONArray.toJacksonArrayNode(): ArrayNode {
    val arrayNode = JsonNodeFactory.instance.arrayNode()
    elements.forEach { element ->
        arrayNode.add(element.toJacksonJsonNode())
    }
    return arrayNode
}

/**
 * Converts [JSONPrimitive] to Jackson [JsonNode].
 */
public fun JSONPrimitive.toJacksonJsonNode(): ValueNode = when (this) {
    is JSONNull -> NullNode.instance

    is JSONLiteral -> if (isString) {
        JsonNodeFactory.instance.textNode(content)
    } else {
        JsonNodeFactory.instance.let { factory ->
            intOrNull?.let { factory.numberNode(it) }
                ?: longOrNull?.let { factory.numberNode(it) }
                ?: doubleOrNull?.let { factory.numberNode(it) }
                ?: booleanOrNull?.let { factory.booleanNode(it) }
                ?: factory.rawValueNode(RawValue(content))
        }
    }
}

//endregion
