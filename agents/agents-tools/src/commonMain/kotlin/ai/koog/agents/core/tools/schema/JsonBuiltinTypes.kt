package ai.koog.agents.core.tools.schema

import kotlinx.schema.json.GenericPropertyDefinition
import kotlinx.schema.json.PropertyDefinition
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.descriptors.nullable

/*
 kotlinx.serialization.json built-in types (JsonElement, JsonObject, JsonArray, ...) are not handled by the
 kotlinx-schema version we depend on: JsonElement's descriptor is SEALED but doesn't follow the compiler's
 ['type', 'value'] layout, so the introspector fails, and reflection treats JsonObject as a raw Map with string values.

 Until kotlinx-schema can be bumped (0.6.0 handles them, see Kotlin/kotlinx-schema#347), these helpers map them to the
 same schemas kotlinx-schema 0.6.0 produces:
 - JsonObject -> {"type": "object", "additionalProperties": {}}
 - JsonArray -> {"type": "array", "items": {}}
 - JsonElement, JsonPrimitive, JsonNull -> {} (any JSON value)
 */

internal object JsonBuiltinTypeNames {
    const val JSON_ELEMENT: String = "kotlinx.serialization.json.JsonElement"
    const val JSON_PRIMITIVE: String = "kotlinx.serialization.json.JsonPrimitive"
    const val JSON_LITERAL: String = "kotlinx.serialization.json.JsonLiteral"
    const val JSON_NULL: String = "kotlinx.serialization.json.JsonNull"
    const val JSON_OBJECT: String = "kotlinx.serialization.json.JsonObject"
    const val JSON_ARRAY: String = "kotlinx.serialization.json.JsonArray"

    /** Types that can hold any JSON value and are described with the empty schema `{}`. */
    val ANY_VALUE_TYPES: Set<String> = setOf(JSON_ELEMENT, JSON_PRIMITIVE, JSON_LITERAL, JSON_NULL)
}

/**
 * Descriptor that kotlinx-schema's serialization introspector maps to the empty schema `{}`
 * (it recognizes the `kotlin.Any` serial name before looking at the kind).
 */
@OptIn(ExperimentalSerializationApi::class)
private val anyJsonValueDescriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("kotlin.Any", PrimitiveKind.STRING)

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.jsonBuiltinReplacementOrNull(): SerialDescriptor? {
    val replacement = when (serialName.removeSuffix("?")) {
        in JsonBuiltinTypeNames.ANY_VALUE_TYPES -> anyJsonValueDescriptor
        JsonBuiltinTypeNames.JSON_OBJECT -> mapSerialDescriptor(String.serializer().descriptor, anyJsonValueDescriptor)
        JsonBuiltinTypeNames.JSON_ARRAY -> listSerialDescriptor(anyJsonValueDescriptor)
        else -> return null
    }

    return if (isNullable) replacement.nullable else replacement
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.containsJsonBuiltins(visited: MutableSet<String> = mutableSetOf()): Boolean {
    if (jsonBuiltinReplacementOrNull() != null) return true
    // Open polymorphic and contextual descriptors are resolved by the introspector itself, leave them alone
    if (kind == PolymorphicKind.OPEN || kind == SerialKind.CONTEXTUAL) return false
    if (!visited.add(serialName.removeSuffix("?"))) return false

    return (0 until elementsCount).any { getElementDescriptor(it).containsJsonBuiltins(visited) }
}

/**
 * Returns a descriptor in which every kotlinx.serialization.json built-in type is replaced with a descriptor
 * kotlinx-schema can describe (see the comment at the top of this file).
 * Descriptors that don't reference these types are returned as is.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.withSchemaFriendlyJsonTypes(): SerialDescriptor =
    jsonBuiltinReplacementOrNull()
        ?: if (containsJsonBuiltins()) JsonTypesReplacingDescriptor(this) else this

/**
 * Delegates everything to [original], except element descriptors, which get their JSON built-in types replaced.
 *
 * Implementing [SerialDescriptor] requires an opt-in; it's only needed until kotlinx-schema handles these types itself.
 */
@OptIn(ExperimentalSerializationApi::class, SealedSerializationApi::class)
private class JsonTypesReplacingDescriptor(
    private val original: SerialDescriptor,
) : SerialDescriptor by original {
    override fun getElementDescriptor(index: Int): SerialDescriptor =
        original.getElementDescriptor(index).withSchemaFriendlyJsonTypes()

    override fun equals(other: Any?): Boolean = other is JsonTypesReplacingDescriptor && other.original == original

    override fun hashCode(): Int = original.hashCode()

    override fun toString(): String = original.toString()
}

/**
 * Whether this property definition is the empty schema `{}` that accepts any JSON value.
 */
internal fun PropertyDefinition.isAnyJsonValue(): Boolean =
    this is GenericPropertyDefinition &&
        type.isNullOrEmpty() &&
        ref == null &&
        enum == null &&
        constValue == null &&
        oneOf == null &&
        anyOf == null &&
        allOf == null
