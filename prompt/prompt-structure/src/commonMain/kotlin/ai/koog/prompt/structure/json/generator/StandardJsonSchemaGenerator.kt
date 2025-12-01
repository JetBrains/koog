package ai.koog.prompt.structure.json.generator

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Full implementation of [GenericJsonSchemaGenerator] to generate advanced generic [LLMParams.Schema.JSON.Standard].
 * Generates LLM-agnostic schema not tied to any specific provider format.
 * For LLMs requiring custom formats when using native structured output, consider provider-specific generators instead.
 *
 * It is recommended to use [StandardJsonSchemaGenerator.Default] companion object, which provides a default instance,
 * instead of creating new instances manually.
 */
public open class StandardJsonSchemaGenerator : GenericJsonSchemaGenerator() {
    /**
     * Default instance of [StandardJsonSchemaGenerator].
     * Prefer to use it instead of creating new instates manually.
     *
     * @see [StandardJsonSchemaGenerator]
     */
    public companion object Default : StandardJsonSchemaGenerator()

    /**
     * Generates generic [LLMParams.Schema.JSON.Standard]
     */
    override fun generate(
        json: Json,
        name: String,
        serializer: KSerializer<*>,
        descriptionOverrides: Map<String, String>,
        excludedProperties: Set<String>,
    ): LLMParams.Schema.JSON.Standard {
        val descriptorKind = serializer.descriptor.kind

        require(
            descriptorKind is PolymorphicKind ||
                descriptorKind in listOf(StructureKind.CLASS, StructureKind.OBJECT, StructureKind.MAP)
        ) {
            "Only object-like types are supported for ${this::class.simpleName} generator, got $descriptorKind"
        }

        val context = GenerationContext(
            json = json,
            descriptor = serializer.descriptor,
            processedTypeDefs = mutableMapOf(),
            currentDefPath = listOf(),
            descriptionOverrides = descriptionOverrides,
            excludedProperties = excludedProperties,
            currentDescription = null
        )

        // Initial generated schema, it's still missing a few things that are added below
        val generatedSchema = process(context)

        // Check that no types have identical serial names
        val duplicatedSerialNames = context.processedTypeDefs.keys
            .map { it.serialName }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }

        require(duplicatedSerialNames.isEmpty()) {
            """
            Found duplicated serial type names, but for a proper type definitions processing by ${this::class.simpleName} generator they should be unique.
            Duplicates: $duplicatedSerialNames
            """.trimIndent()
        }

        // Prepare a proper type definitions map with serial names as type names
        val typeDefinitions = context.processedTypeDefs.mapKeys { (descriptor, _) -> descriptor.serialName }

        // Post-process generated schema to create a full schema
        val schema = buildJsonObject {
            put(JsonSchemaConsts.Keys.ID, name)

            // Add type definitions if any were generated
            if (context.processedTypeDefs.isNotEmpty()) {
                put(JsonSchemaConsts.Keys.DEFS, JsonObject(typeDefinitions))
            }

            generatedSchema.entries.forEach { (key, value) -> put(key, value) }
        }

        return LLMParams.Schema.JSON.Standard(
            name = name,
            schema = schema,
        )
    }

    /**
     * Checks if current type is nullable and updates "type" key value to the union of the actual type and "null" per JSON schema.
     */
    protected fun JsonObject.asNullableType(context: GenerationContext): JsonObject {
        val schema = toMutableMap()

        // Specify nullable types as union as per JSON schema specs
        if (context.descriptor.isNullable) {
            schema[JsonSchemaConsts.Keys.TYPE] = JsonArray(
                listOf(
                    schema.getValue(JsonSchemaConsts.Keys.TYPE),
                    JsonPrimitive(JsonSchemaConsts.Types.NULL)
                )
            )
        }

        return JsonObject(schema)
    }

    override fun processString(context: GenerationContext): JsonObject {
        return super.processString(context).asNullableType(context)
    }

    override fun processBoolean(context: GenerationContext): JsonObject {
        return super.processBoolean(context).asNullableType(context)
    }

    override fun processInteger(context: GenerationContext): JsonObject {
        return super.processInteger(context).asNullableType(context)
    }

    override fun processNumber(context: GenerationContext): JsonObject {
        return super.processNumber(context).asNullableType(context)
    }

    override fun processEnum(context: GenerationContext): JsonObject {
        return super.processEnum(context).asNullableType(context)
    }

    override fun processList(context: GenerationContext): JsonObject {
        return super.processList(context).asNullableType(context)
    }

    override fun processMap(context: GenerationContext): JsonObject {
        return super.processMap(context).asNullableType(context)
    }

    override fun processObject(context: GenerationContext): JsonObject = buildJsonObject {
        /*
         Check if this type is already on the processing stack to prevent infinite recursion.
         Only put a ref in this case without triggering the processing again, since the object itself is already being processed anyway.
         */
        if (context.descriptor !in context.currentDefPath) {
            super.processObject(context)
            val schema = context.processedTypeDefs.getValue(context.descriptor).toMutableMap()

            // Add description for a type itself
            context.getTypeDescription()?.let { description ->
                schema[JsonSchemaConsts.Keys.DESCRIPTION] = JsonPrimitive(description)
            }

            context.processedTypeDefs[context.descriptor] = JsonObject(schema)
        }

        // If this object property should be nullable, using "oneOf" and additional "null" type to indicate this.
        if (context.descriptor.isNullable) {
            put(
                JsonSchemaConsts.Keys.ONE_OF,
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                JsonSchemaConsts.Keys.REF,
                                "${JsonSchemaConsts.Keys.REF_PREFIX}${context.descriptor.serialName}"
                            )
                        }
                    )

                    add(
                        buildJsonObject {
                            put(JsonSchemaConsts.Keys.TYPE, JsonPrimitive(JsonSchemaConsts.Types.NULL))
                        }
                    )
                }
            )
        } else { // Otherwise put a ref object directly
            put(
                JsonSchemaConsts.Keys.REF,
                "${JsonSchemaConsts.Keys.REF_PREFIX}${context.descriptor.serialName}"
            )
        }

        // Add description for this property
        putDescription(context.currentDescription)
    }

    override fun processPolymorphic(context: GenerationContext): JsonObject = buildJsonObject {
        val classDiscriminatorMode = context.json.configuration.classDiscriminatorMode
        val classDiscriminator = context.json.configuration.classDiscriminator

        // Provide an array of all possible schemas for polymorphic types
        put(
            JsonSchemaConsts.Keys.ONE_OF,
            buildJsonArray {
                context.descriptor
                    .getPolymorphicDescriptors(context.json)
                    .forEach { polymorphicDescriptor ->
                        processObject(context.copy(descriptor = polymorphicDescriptor))

                        // Modify polymorphic subtypes, if already processed
                        context.processedTypeDefs[polymorphicDescriptor]?.toMutableMap()?.let { schema ->
                            // Add class discriminators, if enabled
                            if (classDiscriminatorMode in listOf(
                                    ClassDiscriminatorMode.ALL_JSON_OBJECTS,
                                    ClassDiscriminatorMode.POLYMORPHIC
                                )
                            ) {
                                val updatedProperties =
                                    schema.getValue(JsonSchemaConsts.Keys.PROPERTIES).jsonObject.toMutableMap()

                                updatedProperties[classDiscriminator] =
                                    processClassDiscriminator(context.copy(descriptor = polymorphicDescriptor))

                                schema[JsonSchemaConsts.Keys.PROPERTIES] = JsonObject(updatedProperties)

                                schema[JsonSchemaConsts.Keys.REQUIRED] = JsonArray(
                                    (
                                        schema.getValue(JsonSchemaConsts.Keys.REQUIRED).jsonArray.toList() +
                                            JsonPrimitive(classDiscriminator)
                                        ).distinct()
                                )
                            }

                            context.processedTypeDefs[polymorphicDescriptor] = JsonObject(schema)
                        }

                        // Add ref to subtype
                        add(
                            buildJsonObject {
                                put(
                                    JsonSchemaConsts.Keys.REF,
                                    "${JsonSchemaConsts.Keys.REF_PREFIX}${polymorphicDescriptor.serialName}"
                                )
                            }
                        )
                    }

                // If this object property is nullable, add additional "null" type
                if (context.descriptor.isNullable) {
                    add(
                        buildJsonObject {
                            put(JsonSchemaConsts.Keys.TYPE, JsonPrimitive(JsonSchemaConsts.Types.NULL))
                        }
                    )
                }
            }
        )

        // Add description for this property
        putDescription(context.currentDescription)
    }

    override fun processClassDiscriminator(context: GenerationContext): JsonObject = buildJsonObject {
        put(JsonSchemaConsts.Keys.CONST, context.descriptor.serialName)
    }

    override fun processJsonElement(context: GenerationContext): JsonObject {
        return if (context.descriptor.isNullable) {
            buildJsonObject {
                put(
                    JsonSchemaConsts.Keys.ONE_OF,
                    buildJsonArray {
                        add(buildJsonObject { /* empty schema for "any type" */ })
                        add(buildJsonObject { put(JsonSchemaConsts.Keys.TYPE, JsonSchemaConsts.Types.NULL) })
                    }
                )
                putDescription(context.currentDescription)
            }
        } else {
            buildJsonObject {
                // Empty object represents "any type" in JSON schema
                putDescription(context.currentDescription)
            }
        }
    }
}
