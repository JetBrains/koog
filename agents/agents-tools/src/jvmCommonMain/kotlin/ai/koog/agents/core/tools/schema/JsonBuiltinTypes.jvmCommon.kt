package ai.koog.agents.core.tools.schema

import kotlinx.schema.generator.core.ir.AnyNode
import kotlinx.schema.generator.core.ir.ListNode
import kotlinx.schema.generator.core.ir.MapNode
import kotlinx.schema.generator.core.ir.ObjectNode
import kotlinx.schema.generator.core.ir.PrimitiveKind
import kotlinx.schema.generator.core.ir.PrimitiveNode
import kotlinx.schema.generator.core.ir.TypeGraph
import kotlinx.schema.generator.core.ir.TypeId
import kotlinx.schema.generator.core.ir.TypeNode
import kotlinx.schema.generator.core.ir.TypeRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

/*
 Reflection counterpart of JsonBuiltinTypes.kt.

 kotlinx-schema's reflection introspector describes JsonObject as a raw Map with string values (it has no type
 arguments) and walks JsonElement as a sealed hierarchy, producing a graph with dangling references. The graph is
 fixed up after introspection, using the Kotlin types of the introspected parameters and properties.
 */

private val anyValueRef: TypeRef = TypeRef.Inline(AnyNode())

private fun jsonObjectRef(nullable: Boolean): TypeRef = TypeRef.Inline(
    MapNode(key = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)), value = anyValueRef),
    nullable,
)

private fun jsonArrayRef(nullable: Boolean): TypeRef = TypeRef.Inline(ListNode(anyValueRef), nullable)

private fun isIterable(klass: KClass<*>): Boolean = Iterable::class.java.isAssignableFrom(klass.java)

private fun isMap(klass: KClass<*>): Boolean = Map::class.java.isAssignableFrom(klass.java)

/**
 * Type reference for [type] if it is a JSON built-in type, or a list/map of them. Null for any other type.
 */
private fun jsonBuiltinRefOrNull(type: KType): TypeRef? {
    val klass = type.classifier as? KClass<*> ?: return null
    val nullable = type.isMarkedNullable

    return when {
        klass == JsonObject::class -> jsonObjectRef(nullable)
        klass == JsonArray::class -> jsonArrayRef(nullable)
        klass == JsonElement::class || klass == JsonPrimitive::class || klass == JsonNull::class ->
            TypeRef.Inline(AnyNode(), nullable)

        isMap(klass) -> type.arguments.getOrNull(1)?.type?.let(::jsonBuiltinRefOrNull)?.let { valueRef ->
            TypeRef.Inline(MapNode(TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)), valueRef), nullable)
        }

        isIterable(klass) -> type.arguments.singleOrNull()?.type?.let(::jsonBuiltinRefOrNull)?.let { elementRef ->
            TypeRef.Inline(ListNode(elementRef), nullable)
        }

        else -> null
    }
}

/**
 * Replacement for references by id to JSON built-in types, left in the graph when they are nested in other types.
 */
private fun jsonBuiltinRefOrNull(id: TypeId, nullable: Boolean): TypeRef? = when (id.value) {
    in JsonBuiltinTypeNames.ANY_VALUE_TYPES -> TypeRef.Inline(AnyNode(), nullable)
    JsonBuiltinTypeNames.JSON_OBJECT -> jsonObjectRef(nullable)
    JsonBuiltinTypeNames.JSON_ARRAY -> jsonArrayRef(nullable)
    else -> null
}

private fun isJsonBuiltinId(id: TypeId): Boolean = jsonBuiltinRefOrNull(id, nullable = false) != null

private fun TypeRef.replaceJsonBuiltinRefs(): TypeRef = when (this) {
    is TypeRef.Ref -> jsonBuiltinRefOrNull(id, nullable) ?: this
    is TypeRef.Inline -> when (val node = node) {
        is ListNode -> copy(node = node.copy(element = node.element.replaceJsonBuiltinRefs()))
        is MapNode -> copy(node = node.copy(value = node.value.replaceJsonBuiltinRefs()))
        else -> this
    }
}

/**
 * Collects classes reachable from [type] (type arguments, properties, sealed subclasses), keyed by the [TypeId]
 * kotlinx-schema's reflection introspector gives them.
 */
private fun collectClasses(type: KType, classes: MutableMap<TypeId, KClass<*>>) {
    val klass = type.classifier as? KClass<*> ?: return
    type.arguments.forEach { argument -> argument.type?.let { collectClasses(it, classes) } }
    collectClasses(klass, classes)
}

private fun collectClasses(klass: KClass<*>, classes: MutableMap<TypeId, KClass<*>>) {
    val name = klass.qualifiedName ?: return
    if (name.startsWith("kotlin.") || name.startsWith("java.") || name.startsWith("kotlinx.serialization.json.")) return
    if (classes.put(TypeId(name), klass) != null) return

    klass.sealedSubclasses.forEach { collectClasses(it, classes) }
    runCatching { klass.memberProperties }.getOrNull()?.forEach { collectClasses(it.returnType, classes) }
}

private fun propertyTypes(klass: KClass<*>): Map<String, KType> =
    runCatching { klass.memberProperties.associate { it.name to it.returnType } }.getOrDefault(emptyMap())

private fun TypeNode.withJsonBuiltinsReplaced(propertyTypes: Map<String, KType>): TypeNode = when (this) {
    is ObjectNode -> copy(
        properties = properties.map { property ->
            property.copy(
                type = propertyTypes[property.name]?.let(::jsonBuiltinRefOrNull)
                    ?: property.type.replaceJsonBuiltinRefs()
            )
        }
    )

    is ListNode -> copy(element = element.replaceJsonBuiltinRefs())
    is MapNode -> copy(value = value.replaceJsonBuiltinRefs())
    else -> this
}

private fun TypeGraph.withJsonBuiltinsReplaced(
    classes: Map<TypeId, KClass<*>>,
    extraPropertyTypes: Map<TypeId, Map<String, KType>>,
): TypeGraph = TypeGraph(
    root = root.replaceJsonBuiltinRefs(),
    nodes = nodes
        .filterKeys { !isJsonBuiltinId(it) }
        .mapValues { (id, node) ->
            val propertyTypes = extraPropertyTypes[id] ?: classes[id]?.let(::propertyTypes).orEmpty()
            node.withJsonBuiltinsReplaced(propertyTypes)
        },
)

/**
 * Fixes up a graph produced by kotlinx-schema's reflection introspector for [klass], so that
 * kotlinx.serialization.json built-in types get the same schemas as in [withSchemaFriendlyJsonTypes].
 */
internal fun TypeGraph.withSchemaFriendlyJsonTypes(klass: KClass<*>): TypeGraph {
    jsonBuiltinRefOrNull(klass.qualifiedName?.let(::TypeId) ?: return this, nullable = false)
        ?.let { return TypeGraph(root = it, nodes = emptyMap()) }

    val classes = mutableMapOf<TypeId, KClass<*>>()
    collectClasses(klass, classes)

    return withJsonBuiltinsReplaced(classes, extraPropertyTypes = emptyMap())
}

/**
 * Fixes up a graph produced by kotlinx-schema's reflection introspector for the function [callable], so that
 * kotlinx.serialization.json built-in types get the same schemas as in [withSchemaFriendlyJsonTypes].
 */
internal fun TypeGraph.withSchemaFriendlyJsonTypes(callable: KCallable<*>): TypeGraph {
    val parameterTypes = callable.parameters
        .filter { it.kind == KParameter.Kind.VALUE && it.name != null }
        .associate { it.name!! to it.type }

    val classes = mutableMapOf<TypeId, KClass<*>>()
    parameterTypes.values.forEach { collectClasses(it, classes) }

    // The function itself is represented by an object node whose id is the function name
    val functionId = (root as? TypeRef.Ref)?.id

    return withJsonBuiltinsReplaced(
        classes = classes,
        extraPropertyTypes = functionId?.let { mapOf(it to parameterTypes) }.orEmpty(),
    )
}
