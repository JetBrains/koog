package ai.koog.serialization.jackson

import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONLiteral
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ValueNode
import com.fasterxml.jackson.module.kotlin.addDeserializer
import com.fasterxml.jackson.module.kotlin.addSerializer

/**
 * Jackson module that handles [JSONElement] serialization and deserialization to a proper JSON.
 */
public class JSONElementModule : SimpleModule() {
    init {
        addSerializer(JSONElement::class, JSONElementSerializer)
        addSerializer(JSONObject::class, JSONObjectSerializer)
        addSerializer(JSONArray::class, JSONArraySerializer)
        addSerializer(JSONPrimitive::class, JSONPrimitiveSerializer)
        addSerializer(JSONLiteral::class, JSONLiteralSerializer)
        addSerializer(JSONNull::class, JSONNullSerializer)

        addDeserializer(JSONElement::class, JSONElementDeserializer)
        addDeserializer(JSONObject::class, JSONObjectDeserializer)
        addDeserializer(JSONArray::class, JSONArrayDeserializer)
        addDeserializer(JSONPrimitive::class, JSONPrimitiveDeserializer)
        addDeserializer(JSONLiteral::class, JSONLiteralDeserializer)
        addDeserializer(JSONNull::class, JSONNullDeserializer)
    }
}

// Serializers

public object JSONElementSerializer : JsonSerializer<JSONElement>() {
    override fun serialize(value: JSONElement, gen: JsonGenerator, serializers: SerializerProvider) {
        val jsonNode = value.toJacksonJsonNode()
        gen.writeTree(jsonNode)
    }
}

public object JSONObjectSerializer : JsonSerializer<JSONObject>() {
    override fun serialize(value: JSONObject, gen: JsonGenerator, serializers: SerializerProvider) {
        val jsonNode = value.toJacksonObjectNode()
        gen.writeTree(jsonNode)
    }
}

public object JSONArraySerializer : JsonSerializer<JSONArray>() {
    override fun serialize(value: JSONArray, gen: JsonGenerator, serializers: SerializerProvider) {
        val jsonNode = value.toJacksonArrayNode()
        gen.writeTree(jsonNode)
    }
}

public object JSONPrimitiveSerializer : JsonSerializer<JSONPrimitive>() {
    override fun serialize(value: JSONPrimitive, gen: JsonGenerator, serializers: SerializerProvider) {
        val jsonNode = value.toJacksonJsonNode()
        gen.writeTree(jsonNode)
    }
}

public object JSONLiteralSerializer : JsonSerializer<JSONLiteral>() {
    override fun serialize(value: JSONLiteral, gen: JsonGenerator, serializers: SerializerProvider) {
        val jsonNode = value.toJacksonJsonNode()
        gen.writeTree(jsonNode)
    }
}

public object JSONNullSerializer : JsonSerializer<JSONNull>() {
    override fun serialize(value: JSONNull, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNull()
    }
}

// Deserializers

public object JSONElementDeserializer : JsonDeserializer<JSONElement>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONElement {
        val jsonNode = p.readValueAsTree<JsonNode>()
        return if (jsonNode == null || jsonNode.isNull) JSONNull else jsonNode.toKoogJSONElement()
    }
}

public object JSONObjectDeserializer : JsonDeserializer<JSONObject>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONObject {
        val jsonNode = p.readValueAsTree<ObjectNode>()
        return jsonNode.toKoogJSONObject()
    }
}

public object JSONArrayDeserializer : JsonDeserializer<JSONArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONArray {
        val jsonNode = p.readValueAsTree<ArrayNode>()
        return jsonNode.toKoogJSONArray()
    }
}

public object JSONPrimitiveDeserializer : JsonDeserializer<JSONPrimitive>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONPrimitive {
        val jsonNode = p.readValueAsTree<ValueNode>()
        return if (jsonNode == null || jsonNode.isNull) {
            JSONNull
        } else {
            jsonNode.toKoogJSONPrimitive()
        }
    }
}

public object JSONLiteralDeserializer : JsonDeserializer<JSONLiteral>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONLiteral {
        val primitive = JSONPrimitiveDeserializer.deserialize(p, ctxt)
        return primitive as? JSONLiteral
            ?: throw IllegalStateException("Expected JSONLiteral but got ${primitive::class.simpleName}")
    }
}

public object JSONNullDeserializer : JsonDeserializer<JSONNull>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JSONNull {
        val primitive = JSONPrimitiveDeserializer.deserialize(p, ctxt)
        return primitive as? JSONNull
            ?: throw IllegalStateException("Expected JSONNull but got ${primitive::class.simpleName}")
    }
}
