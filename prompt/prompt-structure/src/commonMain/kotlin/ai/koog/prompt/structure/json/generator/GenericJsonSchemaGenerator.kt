package ai.koog.prompt.structure.json.generator

import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Generic extensions of [JsonSchemaGenerator] that provides some common base implementations of visit methods.
 * This class can be used as a base to implement custom generators that share generic schema generation logic.
 *
 * Note: it does not handle nullability because these might be different in different schema specs.
 * Implementations must handle these themselves.
 */
public abstract class GenericJsonSchemaGenerator : JsonSchemaGenerator() {
    /**
     * Generic implementation that provides basic routing to appropriate visit method and adds description.
     */
    override fun process(context: GenerationContext): JsonObject {
        return when (context.descriptor.kind) {
            PrimitiveKind.STRING ->
                processString(context)

            PrimitiveKind.BOOLEAN ->
                processBoolean(context)

            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                processInteger(context)

            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
                processNumber(context)

            SerialKind.ENUM ->
                processEnum(context)

            StructureKind.LIST ->
                processList(context)

            StructureKind.MAP ->
                processMap(context)

            StructureKind.CLASS, StructureKind.OBJECT ->
                processObject(context)

            is PolymorphicKind ->
                processPolymorphic(context)

            else ->
                throw IllegalArgumentException("Encountered unsupported type while generating JSON schema: ${context.descriptor.kind}")
        }
    }

    /**
     * Puts [description] to the current [JsonObjectBuilder]
     */
    protected fun JsonObjectBuilder.putDescription(description: String?) {
        description?.let {
            put(JsonSchemaConsts.Keys.DESCRIPTION, it)
        }
    }

    protected fun JsonObjectBuilder.putMax(max: Int?, type: String) {
        max?.let {
            when (type) {
                JsonSchemaConsts.Types.STRING -> put(JsonSchemaConsts.Keys.MAX_LENGTH, it)
                JsonSchemaConsts.Types.NUMBER -> put(JsonSchemaConsts.Keys.MAX, it)
                JsonSchemaConsts.Types.INTEGER -> put(JsonSchemaConsts.Keys.MAX, it)
                JsonSchemaConsts.Types.ARRAY -> put(JsonSchemaConsts.Keys.MAX_ITEMS, it)
                else -> throw IllegalArgumentException("Unsupported type for max: $type")
            }
        }
    }
    protected fun JsonObjectBuilder.putMin(min: Int?, type: String) {
        min?.let {
            when (type) {
                JsonSchemaConsts.Types.STRING -> put(JsonSchemaConsts.Keys.MIN_LENGTH, it)
                JsonSchemaConsts.Types.NUMBER -> put(JsonSchemaConsts.Keys.MIN, it)
                JsonSchemaConsts.Types.INTEGER -> put(JsonSchemaConsts.Keys.MIN, it)
                JsonSchemaConsts.Types.ARRAY -> put(JsonSchemaConsts.Keys.MIN_ITEMS, it)
                else -> throw IllegalArgumentException("Unsupported type for min: $type")
            }
        }
    }

    override fun processString(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.STRING)
        putDescription(context.currentDescription)
        putMax(context.getTypeMax(), JsonSchemaConsts.Types.STRING)
        putMin(context.getTypeMin(), JsonSchemaConsts.Types.STRING)
    }

    override fun processBoolean(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.BOOLEAN)
        putDescription(context.currentDescription)
    }

    override fun processInteger(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.INTEGER)
        putDescription(context.currentDescription)
        putMax(context.getTypeMax(), JsonSchemaConsts.Types.INTEGER)
        putMin(context.getTypeMin(), JsonSchemaConsts.Types.INTEGER)
    }

    override fun processNumber(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.NUMBER)
        putDescription(context.currentDescription)
        putMax(context.getTypeMax(), JsonSchemaConsts.Types.NUMBER)
        putMin(context.getTypeMin(), JsonSchemaConsts.Types.NUMBER)
    }

    override fun processEnum(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.STRING)
        put(
            JsonSchemaConsts.Keys.ENUM,
            JsonArray(context.descriptor.elementNames.map { JsonPrimitive(it) })
        )

        putDescription(context.currentDescription)
    }

    override fun processList(context: GenerationContext): JsonObject = buildJsonObject {
        val itemDescriptor = context.descriptor.getElementDescriptor(0)

        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.ARRAY)
        putMin(context.getTypeMin(), JsonSchemaConsts.Types.ARRAY)
        putMax(context.getTypeMax(), JsonSchemaConsts.Types.ARRAY)
        put(JsonSchemaConsts.Keys.ITEMS, process(context.copy(descriptor = itemDescriptor, currentDescription = null)))

        putDescription(context.currentDescription)
    }

    override fun processMap(context: GenerationContext): JsonObject = buildJsonObject {
        val keyDescriptor = context.descriptor.getElementDescriptor(0)
        val valueDescriptor = context.descriptor.getElementDescriptor(1)

        // For maps, we support only string keys and values of the element type
        require(keyDescriptor.kind == PrimitiveKind.STRING) {
            "JSON schema only supports string keys in maps, found: ${keyDescriptor.serialName}"
        }

        put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.OBJECT)
        put(
            JsonSchemaConsts.Keys.ADDITIONAL_PROPERTIES,
            process(context.copy(descriptor = valueDescriptor, currentDescription = null))
        )

        putDescription(context.currentDescription)
    }

    private fun applyConstraints(
        property: JsonObject,
        kind: SerialKind,
        min: Int?,
        max: Int?
    ): JsonObject {
        val result = property.toMutableMap()

        val type = when (kind) {
            PrimitiveKind.STRING -> JsonSchemaConsts.Types.STRING
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> JsonSchemaConsts.Types.INTEGER
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> JsonSchemaConsts.Types.NUMBER
            StructureKind.LIST -> JsonSchemaConsts.Types.ARRAY
            else -> return property
        }

        if (min != null) {
            val key = when (type) {
                JsonSchemaConsts.Types.STRING -> JsonSchemaConsts.Keys.MIN_LENGTH
                JsonSchemaConsts.Types.ARRAY -> JsonSchemaConsts.Keys.MIN_ITEMS
                else -> JsonSchemaConsts.Keys.MIN
            }
            result[key] = JsonPrimitive(min)
        }

        if (max != null) {
            val key = when (type) {
                JsonSchemaConsts.Types.STRING -> JsonSchemaConsts.Keys.MAX_LENGTH
                JsonSchemaConsts.Types.ARRAY -> JsonSchemaConsts.Keys.MAX_ITEMS
                else -> JsonSchemaConsts.Keys.MAX
            }
            result[key] = JsonPrimitive(max)
        }

        return JsonObject(result)
    }

    override fun processObject(context: GenerationContext): JsonObject {
        check(context.descriptor !in context.currentDefPath) {
            """
            Recursion detected in type definitions while generating JSON schema.
            This usually means you have recursive type where one of the fields in a class has a type of this class itself
            or its base class when using ${this::class.simpleName} generator, which is not supported by this generator.
            
            Consider some possible solutions:
            1. Use other JSON schema generator that supports such classes and if the format it produces is supported by the LLM you're using.
            2. Remove recursive type references.
            
            Current definition is ${context.descriptor.serialName} at path ${context.currentDefPath.map { it.serialName }}
            """.trimIndent()
        }

        // If this type was already processed, get it from the collection
        val schema = if (context.descriptor in context.processedTypeDefs) {
            context.processedTypeDefs.getValue(context.descriptor)
        } else { // Otherwise process and add it to the collection
            // Process all properties
            val properties = buildJsonObject {
                for (i in 0 until context.descriptor.elementsCount) {
                    val propertyName = context.descriptor.getElementName(i)
                    val propertyDescriptor = context.descriptor.getElementDescriptor(i)

                    val elementMin = context.getElementMin(i)
                    val elementMax = context.getElementMax(i)

                    val propertyContext = context.copy(
                        descriptor = propertyDescriptor,
                        currentDefPath = context.currentDefPath + context.descriptor,
                        currentDescription = context.getElementDescription(i)
                            ?: context.copy(descriptor = propertyDescriptor).getTypeDescription()
                    )

                    val processedProperty = if (elementMin != null || elementMax != null) {
                        val baseProperty = process(propertyContext)
                        applyConstraints(baseProperty, propertyDescriptor.kind, elementMin, elementMax)
                    } else {
                        process(propertyContext)
                    }

                    put(propertyName, processedProperty)
                }
            }

            // Process required
            val required = buildJsonArray {
                // Add all non-optional properties
                for (i in 0 until context.descriptor.elementsCount) {
                    if (!context.descriptor.isElementOptional(i)) {
                        add(context.descriptor.getElementName(i))
                    }
                }
            }

            // Build type definition
            buildJsonObject {
                put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.OBJECT)
                put(JsonSchemaConsts.Keys.PROPERTIES, properties)
                put(JsonSchemaConsts.Keys.REQUIRED, required)
                // Specify explicitly that additional unknown keys should not be provided
                put(JsonSchemaConsts.Keys.ADDITIONAL_PROPERTIES, false)
            }.also {
                // Also add it to the collection of processed types.
                context.processedTypeDefs[context.descriptor] = it
            }
        }

        // Add specific description from the context to the generated schema object
        return buildJsonObject {
            schema.forEach { (key, value) -> put(key, value) }
            putDescription(context.currentDescription)
        }
    }

    override fun processClassDiscriminator(context: GenerationContext): JsonObject {
        throw UnsupportedOperationException("Class discriminator is not supported by ${this::class.simpleName} generator")
    }

    override fun processPolymorphic(context: GenerationContext): JsonObject {
        throw UnsupportedOperationException("Polymorphic types are not supported by ${this::class.simpleName} generator")
    }
}
