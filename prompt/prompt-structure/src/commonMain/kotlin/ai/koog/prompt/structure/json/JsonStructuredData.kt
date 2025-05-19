package ai.koog.prompt.structure.json

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.structure.DescriptionMetadata
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.structure
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.text.TextContentBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Represents a structure for handling and interacting with structured data of a specified type.
 *
 * @param TStruct The type of data to be structured.
 * @property id A unique identifier for the structure.
 * @property serializer The serializer used to convert the data to and from JSON.
 * @property examples A list of example data items that conform to the structure.
 * @property structureLanguage Structured data format.
 * @property schema Schema of this structure
 * @property schema Schema guideline for LLM to directly ask LLM API for a structured output.
 */
public class JsonStructuredData<TStruct>(
    id: String,
    private val serializer: KSerializer<TStruct>,
    private val structureLanguage: JsonStructureLanguage,
    examples: List<TStruct>,
    private val jsonSchema: LLMParams.Schema.JSON
): StructuredData<TStruct>(id, examples, jsonSchema) {

    /**
     * Represents the type of JSON schema that can be utilized for structured data definition.
     * This defines the level of detail or complexity included in the schema.
     */
    public enum class JsonSchemaType {
        /**
         * Represents the complete schema type in the enumeration, typically used to indicate
         * that the JSON schema should be fully applied or adhered to without simplification.
         */
        FULL, /**
         * Represents a simplified schema type used within the JsonSchemaType enumeration.
         * This type is typically used for scenarios where a minimal representation of the schema is sufficient.
         */
        SIMPLE
    }

    override fun parse(text: String): TStruct = structureLanguage.parse(text, serializer)
    override fun pretty(value: TStruct): String = structureLanguage.pretty(value, serializer)

    override fun definition(builder: TextContentBuilder): TextContentBuilder = builder.apply {
        +"DEFINITION OF $id"
        +"The $id format is defined only and solely with JSON, without any additional characters, backticks or anything similar."
        newline()

        +"You must adhere to the following JSON schema:"
        +structureLanguage.pretty(jsonSchema.schema)

        +"Here are the examples of valid responses:"
        examples.forEach {
            structure(structureLanguage, it, serializer)
        }
        newline()
    }

    public companion object {
        // TODO: Class.simpleName is the only reason to make the function inline, perhaps we can hide most of the implementation
        /**
         * Factory method to create JSON structure with auto-generated JSON schema.
         */
        public inline fun <reified T> createJsonStructure(
            id: String = T::class.simpleName ?: error("Class name is required for JSON structure"),
            serializer: KSerializer<T> = serializer<T>(),
            json: Json = JsonStructureLanguage.defaultJson,
            schemaFormat: JsonSchemaGenerator.SchemaFormat = JsonSchemaGenerator.SchemaFormat.Simple,
            maxDepth: Int = 20,
            propertyDescriptionOverrides: Map<String, String> = emptyMap(),
            examples: List<T> = emptyList(),
            schemaType: JsonSchemaType = JsonSchemaType.SIMPLE
        ): StructuredData<T> {
            val structureLanguage = JsonStructureLanguage(json)
            val metadata = getDescriptionMetadata(serializer)

            // Use platform-specific implementations to get property descriptions
            val propertyDescriptions = metadata?.allDescriptions().orEmpty()
                .merge(propertyDescriptionOverrides) { _, _, override -> override }

            val schema =
                JsonSchemaGenerator(json, schemaFormat, maxDepth).generate(id, serializer, propertyDescriptions)

            return JsonStructuredData(
                id = id,
                serializer = serializer,
                structureLanguage = structureLanguage,
                examples = examples,
                jsonSchema = when (schemaType) {
                    JsonSchemaType.FULL -> LLMParams.Schema.JSON.Full(id, schema)
                    JsonSchemaType.SIMPLE -> LLMParams.Schema.JSON.Simple(id, schema)
                }
            )
        }

        /**
         * Retrieves description metadata for a given serializer. The metadata includes
         * a description of the class (if annotated) and descriptions of its fields
         * based on the presence of the `LLMDescription` annotation.
         *
         * @param T The type of the serializer.
         * @param serializer The serializer for the type T, used to extract metadata.
         * @return A `DescriptionMetadata` object containing the class and field descriptions,
         *         or `null` if no descriptions are found.
         */
        @PublishedApi
        internal fun <T> getDescriptionMetadata(serializer: KSerializer<T>): DescriptionMetadata? {
            // Try to find the class in the registry
            val className = serializer.descriptor.serialName

            // Check if the class has LLMDescription annotation
            val classDescription = serializer.descriptor.annotations
                .filterIsInstance<LLMDescription>()
                .firstOrNull()
                ?.description

            // Collect field descriptions
            val fieldDescriptions = mutableMapOf<String, String>()
            val descriptor = serializer.descriptor

            for (i in 0 until descriptor.elementsCount) {
                val propertyName = descriptor.getElementName(i)
                val propertyAnnotations = descriptor.getElementAnnotations(i)

                val description = propertyAnnotations
                    .filterIsInstance<LLMDescription>()
                    .firstOrNull()
                    ?.description

                if (description != null) {
                    // Use the format expected by JsonSchemaGenerator: "${descriptor.serialName}.$propertyName"
                    fieldDescriptions["$className.$propertyName"] = description
                }
            }

            // If no class description and no field descriptions, return null
            if (classDescription == null && fieldDescriptions.isEmpty()) {
                return null
            }

            // Create a new DescriptionMetadata object with the class description and field descriptions
            return object : DescriptionMetadata {
                override val className: String = className
                override val classDescription: String? = classDescription
                override val fieldDescriptions: Map<String, String> = fieldDescriptions
            }
        }

        /**
         * Merges [other] into the current map.
         * If the key already exists, it calls [merger] and puts its result,
         * otherwise it simply adds a new pair.
         */
        @PublishedApi
        internal inline fun <K, V> MutableMap<K, V>.mergeInPlace(
            other: Map<K, V>,
            merger: (key: K, first: V, second: V) -> V
        ): MutableMap<K, V> = apply {
            other.forEach { (k, v) ->
                this[k] = get(k)?.let { merger(k, it, v) } ?: v
            }
        }

        /**
         * Non-blocking merge: returns a new map, leaving the original collections unchanged.
         */
        @PublishedApi
        internal inline fun <K, V> Map<K, V>.merge(
            other: Map<K, V>,
            merger: (key: K, first: V, second: V) -> V
        ): Map<K, V> = toMutableMap().mergeInPlace(other, merger)
    }
}
