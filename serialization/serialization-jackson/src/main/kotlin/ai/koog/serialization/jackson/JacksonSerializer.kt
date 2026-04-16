package ai.koog.serialization.jackson

import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.JavaClassToken
import ai.koog.serialization.JavaTypeToken
import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.KotlinClassToken
import ai.koog.serialization.KotlinTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.jvm.javaType

/**
 * Serializer that uses Jackson (jackson-databind).
 *
 * @param objectMapper The Jackson [ObjectMapper] to use for serialization/deserialization.
 */
public class JacksonSerializer(
    objectMapper: ObjectMapper = ObjectMapper(),
) : JSONSerializer {
    private val objectMapper: ObjectMapper = objectMapper
        .copy()
        // Register JSONElementModule to handle JSONElement serialization/deserialization
        .registerModule(JSONElementModule())

    override fun <T> encodeToString(value: T, typeToken: TypeToken): String {
        return objectMapper.writeValueAsString(value)
    }

    override fun <T> decodeFromString(value: String, typeToken: TypeToken): T {
        val javaType = resolveJavaType(typeToken)
        val result: Any? = objectMapper.readValue(value, javaType)

        // Handle JSONElement null case: Jackson returns Java null for JSON null,
        // but we need to return JSONNull singleton
        @Suppress("UNCHECKED_CAST")
        return when {
            result == null && JSONElement::class.java.isAssignableFrom(javaType.rawClass) -> JSONNull as T
            result == null && JSONPrimitive::class.java.isAssignableFrom(javaType.rawClass) -> JSONNull as T
            else -> result as T
        }
    }

    override fun <T> encodeToJSONElement(value: T, typeToken: TypeToken): JSONElement {
        val jsonNode = objectMapper.valueToTree<JsonNode>(value)
        return jsonNode.toKoogJSONElement()
    }

    override fun <T> decodeFromJSONElement(value: JSONElement, typeToken: TypeToken): T {
        val jsonNode = value.toJacksonJsonNode()
        val javaType = resolveJavaType(typeToken)
        @Suppress("UNCHECKED_CAST")
        return objectMapper.treeToValue(jsonNode, javaType.rawClass) as T
    }

    @OptIn(InternalKoogSerializationApi::class)
    private fun resolveJavaType(typeToken: TypeToken): JavaType = when (typeToken) {
        is KotlinTypeToken ->
            objectMapper.typeFactory.constructType(typeToken.type.javaType)

        is KotlinClassToken ->
            objectMapper.typeFactory.constructParametricType(
                typeToken.klass.java,
                *typeToken.typeArguments.map { resolveJavaType(it) }.toTypedArray(),
            )

        is JavaTypeToken ->
            objectMapper.typeFactory.constructType(typeToken.type)

        is JavaClassToken ->
            objectMapper.typeFactory.constructParametricType(
                typeToken.klass,
                *typeToken.typeArguments.map { resolveJavaType(it) }.toTypedArray(),
            )

        is KSerializerTypeToken<*> ->
            throw IllegalArgumentException("KSerializerTypeToken is not supported for JacksonSerializer: $typeToken")
    }
}
