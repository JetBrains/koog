package ai.koog.prompt.structure.json.generator

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Simple implementation of [GenericJsonSchemaGenerator] that produces [LLMParams.Schema.JSON.Basic].
 * Generates LLM-agnostic schema not tied to any specific provider format.
 * For LLMs requiring custom formats when using native structured output, consider provider-specific generators instead.
 *
 * It is recommended to use [BasicJsonSchemaGenerator.Default] companion object, which provides a default instance,
 * instead of creating new instances manually.
 */
public open class BasicJsonSchemaGenerator : GenericJsonSchemaGenerator() {

    /**
     * Default instance of [BasicJsonSchemaGenerator].
     * Prefer to use it instead of creating new instates manually.
     *
     * @see [BasicJsonSchemaGenerator]
     */
    public companion object Default : BasicJsonSchemaGenerator()

    /**
     * Generates generic [LLMParams.Schema.JSON.Basic]
     */
    override fun generate(
        json: Json,
        name: String,
        serializer: KSerializer<*>,
        descriptionOverrides: Map<String, String>,
        excludedProperties: Set<String>,
    ): LLMParams.Schema.JSON.Basic {
        val descriptorKind = serializer.descriptor.kind

        require(descriptorKind in listOf(StructureKind.CLASS, StructureKind.OBJECT, StructureKind.MAP)) {
            "Only object-like and no polymorphic types are supported for ${this::class.simpleName} generator, got $descriptorKind"
        }

        val context = GenerationContext(
            json = json,
            descriptor = serializer.descriptor,
            processedTypeDefs = mutableMapOf(),
            currentDefPath = listOf(),
            descriptionOverrides = descriptionOverrides,
            excludedProperties = excludedProperties,
            currentDescription = null,
        )

        val schema = process(context)

        return LLMParams.Schema.JSON.Basic(
            name = name,
            schema = schema,
        )
    }

    /**
     * Checks if current type is nullable and appends "nullable" key.
     */
    protected fun JsonObject.asNullableType(context: GenerationContext): JsonObject {
        val schema = toMutableMap()

        // Specify nullable types as union as per JSON schema specs
        if (context.descriptor.isNullable) {
            schema[JsonSchemaConsts.Keys.NULLABLE] = JsonPrimitive(true)
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

    override fun processObject(context: GenerationContext): JsonObject {
        return super.processObject(context).asNullableType(context)
    }
}
