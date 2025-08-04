package ai.koog.prompt.structure.json

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.getPolymorphicDescriptors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON schema generator from Kotlin serializable classes, to be used with LLM structured output functionality.
 *
 * This generator creates JSON schemas that can be included in LLM prompts to encourage structured outputs
 * that match your Kotlin data models. It supports:
 *
 * - Different schema formats (Simple or JsonSchema)
 * - Automatic schema generation from Kotlin serializable classes
 * - Custom property descriptions for better LLM understanding
 * - Nested object structures with configurable depth limits
 * - Polymorphic types via sealed classes
 *
 * The generated schemas help LLMs understand the expected response format, improving parsing reliability
 * and reducing the need for output coercion or error handling.
 */
public class JsonSchemaGenerator(
    private val json: Json,
    private val schemaFormat: SchemaFormat,
    private val maxDepth: Int
) {
    /**
     * Enum class representing the schema format options for generating JSON schemas.
     */
    public enum class SchemaFormat {
        /**
         * Simple format, embed all type definitions.
         * Won't work for more complicated cases, e.g. where subclass refers back again to the base class
         */
        Simple,

        /**
         * This is a very simplified version, not fully according to the JSON schema specification (https://json-schema.org).
         * The full specification is quite complex, and we don't need to support this complexity here.
         */
        JsonSchema
    }

    private companion object {
        const val SCHEMA_KEY = "\$schema"
        const val ID_KEY = "\$id"

        const val TYPE_KEY = "type"
        const val PROPERTIES_KEY = "properties"
        const val REQUIRED_KEY = "required"
        const val NULLABLE_KEY = "nullable"
        const val DESCRIPTION_KEY = "description"

        const val ITEMS_KEY = "items"
        const val ENUM_KEY = "enum"
        const val CONST_KEY = "const"
        const val ADDITIONAL_PROPERTIES_KEY = "additionalProperties"

        const val ONE_OF_KEY = "oneOf"
        const val REF_KEY = "\$ref"
        const val DEFS_KEY = "\$defs"
    }

    private fun createDefRef(defName: String): String = "#/${DEFS_KEY.trimStart('$')}/$defName"

    /**
     * Generate a JSON schema for a serializable class.
     *
     * @param serializer The serializer for the class
     * @param descriptionOverrides Optional map of serial class names and property names to descriptions.
     * If a property/type is already described with [LLMDescription] annotation, value from the map will override this description.
     * @return A JsonObject representing the JSON schema
     */
    public fun generate(
        id: String,
        serializer: KSerializer<*>,
        descriptionOverrides: Map<String, String> = emptyMap()
    ): JsonObject {
        val rootSchema: JsonObject
        val definitions = buildJsonObject {
            rootSchema = generatePropertySchema(
                rootDefsBuilder = this,
                processedDefs = emptySet(),
                descriptionOverrides = descriptionOverrides,
                descriptor = serializer.descriptor,
                currentDepth = 0,
            )
        }

        return when (schemaFormat) {
            SchemaFormat.Simple -> {
                rootSchema
            }

            SchemaFormat.JsonSchema -> {
                buildJsonObject {
                    put(SCHEMA_KEY, JsonPrimitive("http://json-schema.org/draft-07/schema#"))
                    put(ID_KEY, id)

                    // Add type definitions if any were generated (which they should probably be)
                    if (definitions.isNotEmpty()) {
                        put(DEFS_KEY, definitions)
                    }

                    rootSchema.entries.forEach { (key, value) -> put(key, value) }

                    // Some LLMs expect type key to be present at the root of the schema guideline, just $ref` is not enough
                    if (TYPE_KEY !in rootSchema) {
                        put(TYPE_KEY, "object")
                    }
                }
            }
        }
    }

    /**
     * Generate schema for a single property.
     */
    private fun generatePropertySchema(
        rootDefsBuilder: JsonObjectBuilder,
        processedDefs: Set<String>,
        descriptionOverrides: Map<String, String>,
        descriptor: SerialDescriptor,
        currentDepth: Int,
        isPolymorphicSubtype: Boolean = false,
    ): JsonObject {
        check(currentDepth <= maxDepth) { "Maximum depth of $maxDepth exceeded while generating JSON schema" }

        return buildJsonObject {
            when (descriptor.kind) {
                PrimitiveKind.STRING ->
                    put(TYPE_KEY, "string")

                PrimitiveKind.BOOLEAN ->
                    put(TYPE_KEY, "boolean")

                PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                    put(TYPE_KEY, "integer")

                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
                    put(TYPE_KEY, "number")

                SerialKind.ENUM -> {
                    put(TYPE_KEY, "string")
                    put(
                        ENUM_KEY,
                        buildJsonArray {
                            descriptor.elementNames.forEach { add(it) }
                        }
                    )
                }

                StructureKind.LIST -> {
                    val itemDescriptor = descriptor.getElementDescriptor(0)

                    put(TYPE_KEY, "array")
                    put(
                        ITEMS_KEY,
                        generatePropertySchema(
                            rootDefsBuilder = rootDefsBuilder,
                            processedDefs = processedDefs,
                            descriptionOverrides = descriptionOverrides,
                            descriptor = itemDescriptor,
                            currentDepth = currentDepth + 1,
                        )
                    )
                }

                StructureKind.MAP -> {
                    val keyDescriptor = descriptor.getElementDescriptor(0)
                    val valueDescriptor = descriptor.getElementDescriptor(1)

                    // For maps, we support only string keys and values of the element type
                    require(keyDescriptor.kind == PrimitiveKind.STRING) {
                        "JSON schema only supports string keys in maps, found: ${keyDescriptor.serialName}"
                    }

                    put(TYPE_KEY, "object")
                    put(
                        ADDITIONAL_PROPERTIES_KEY,
                        generatePropertySchema(
                            rootDefsBuilder = rootDefsBuilder,
                            processedDefs = processedDefs,
                            descriptionOverrides = descriptionOverrides,
                            descriptor = valueDescriptor,
                            currentDepth = currentDepth + 1,
                        )
                    )
                }

                StructureKind.CLASS, StructureKind.OBJECT -> {
                    if (schemaFormat == SchemaFormat.Simple || descriptor.serialName !in processedDefs) {
                        // Add current object serial name as processed def
                        val updatedProcessedDefs = processedDefs + descriptor.serialName

                        // Process all properties
                        val properties = buildJsonObject {
                            /*
                             Append serial name discriminator const if this type definition was retrieved as a subtype of a polymorphic type.
                             Needed for serialization disambiguation.
                             */
                            if (isPolymorphicSubtype) {
                                put(
                                    json.configuration.classDiscriminator,
                                    buildJsonObject {
                                        put(CONST_KEY, descriptor.serialName)
                                    }
                                )
                            }

                            for (i in 0 until descriptor.elementsCount) {
                                val propertyName = descriptor.getElementName(i)
                                val propertyDescriptor = descriptor.getElementDescriptor(i)
                                val propertyAnnotations = descriptor.getElementAnnotations(i)

                                // Description for a property
                                val lookupKey = "${descriptor.serialName}.$propertyName"
                                val propertyDescriptionOverride = descriptionOverrides[lookupKey]
                                val propertyDescriptionAnnotation = propertyAnnotations
                                    .filterIsInstance<LLMDescription>()
                                    .firstOrNull()
                                    ?.description

                                // Look at the explicit map first, then at the annotation
                                val propertyDescription = propertyDescriptionOverride ?: propertyDescriptionAnnotation

                                put(
                                    propertyName,
                                    JsonObject(
                                        generatePropertySchema(
                                            rootDefsBuilder = rootDefsBuilder,
                                            processedDefs = updatedProcessedDefs,
                                            descriptionOverrides = descriptionOverrides,
                                            descriptor = propertyDescriptor,
                                            currentDepth = currentDepth + 1,
                                        ).let { propertySchema ->
                                            // appending description to a generated property schema, if any is present
                                            if (propertyDescription != null) {
                                                propertySchema + (DESCRIPTION_KEY to JsonPrimitive(propertyDescription))
                                            } else {
                                                propertySchema
                                            }
                                        }
                                    )
                                )
                            }
                        }

                        // Process which are required
                        val required = buildJsonArray {
                            // Serial name is required for polymorphic subtypes
                            if (isPolymorphicSubtype) {
                                add(json.configuration.classDiscriminator)
                            }

                            // Add all non-optional properties
                            for (i in 0 until descriptor.elementsCount) {
                                if (!descriptor.isElementOptional(i)) {
                                    add(descriptor.getElementName(i))
                                }
                            }
                        }

                        // Description for a whole type (definition)
                        val typeDescriptionOverride = descriptionOverrides[descriptor.serialName]
                        val typeDescriptionAnnotation = descriptor.annotations
                            .filterIsInstance<LLMDescription>()
                            .firstOrNull()
                            ?.description

                        // Look at the explicit map first, then at the annotation
                        val typeDescription = typeDescriptionOverride ?: typeDescriptionAnnotation

                        // Build type definition
                        val typeDefinition = buildJsonObject {
                            put(TYPE_KEY, "object")
                            typeDescription?.let { put(DESCRIPTION_KEY, it) }
                            put(PROPERTIES_KEY, properties)
                            put(REQUIRED_KEY, required)
                        }

                        when (schemaFormat) {
                            // Simple - put in the current object
                            SchemaFormat.Simple -> typeDefinition.forEach { (key, value) -> put(key, value) }
                            // JsonSchema - put in the root defs collection
                            SchemaFormat.JsonSchema -> rootDefsBuilder.put(descriptor.serialName, typeDefinition)
                        }
                    }

                    // Return ref to a definition as a result for a JsonSchema format
                    if (schemaFormat == SchemaFormat.JsonSchema) {
                        put(REF_KEY, createDefRef(descriptor.serialName))
                    }
                }

                is PolymorphicKind -> {
                    // Provide an array of all possible schemas for polymorphic types
                    val subtypes = buildJsonArray {
                        getPolymorphicDescriptors(descriptor).forEach { polymorphicDescriptor ->
                            add(
                                generatePropertySchema(
                                    rootDefsBuilder = rootDefsBuilder,
                                    processedDefs = processedDefs,
                                    descriptionOverrides = descriptionOverrides,
                                    descriptor = polymorphicDescriptor,
                                    currentDepth = currentDepth + 1,
                                    isPolymorphicSubtype = true,
                                )
                            )
                        }
                    }

                    put(ONE_OF_KEY, subtypes)
                }

                else -> throw IllegalStateException(
                    "Encountered unsupported type while generating JSON schema: ${descriptor.kind}"
                )
            }

            if (descriptor.isNullable) {
                put(NULLABLE_KEY, true)
            }
        }
    }

    /*
      Reference links:
      1. https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization.descriptors/
      2. Useful comment explaining why just serializerModule.getPolymorphicDescriptors() won't work on sealed classes
         https://github.com/Kotlin/kotlinx.serialization/blob/438fb8eab0350fd8238d13798a6e8c3edc2b8b24/core/commonMain/src/kotlinx/serialization/descriptors/ContextAware.kt#L19
     */
    private fun getPolymorphicDescriptors(polyDescriptor: SerialDescriptor): List<SerialDescriptor> {
        val subclassDescriptor = when (polyDescriptor.kind) {
            /*
              Sealed descriptor contains fixed fields: TYPE_KEY on the first position (not interesting for us here) and "value"
              on the second — collection of subtype descriptors.
              The latter is what we need.
              Spent more time on this than I want to admit…
             */
            is PolymorphicKind.SEALED -> {
                // Check that "value" element containing subclasses descriptors is present
                require(polyDescriptor.elementNames.toList().getOrNull(1) == "value") {
                    "Expected second element to be 'value', got: ${polyDescriptor.elementNames.toList()}"
                }

                val subclassesDescriptor = polyDescriptor.elementDescriptors
                    .toList()
                    .getOrNull(1)
                    ?: throw IllegalArgumentException("Cannot find subclasses descriptor")

                subclassesDescriptor.elementDescriptors.toList()
            }

            is PolymorphicKind.OPEN -> {
                json.serializersModule.getPolymorphicDescriptors(polyDescriptor)
            }

            else -> throw IllegalArgumentException("Unsupported descriptor type: ${polyDescriptor.kind}")
        }

        return subclassDescriptor.sortedBy { it.serialName } // to get predictable ordering
    }
}
