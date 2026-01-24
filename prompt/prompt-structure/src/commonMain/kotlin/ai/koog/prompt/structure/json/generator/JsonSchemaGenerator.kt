package ai.koog.prompt.structure.json.generator

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.getPolymorphicDescriptors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON schema generator from Kotlin serializable classes, to be used with LLM structured output functionality.
 * This generator creates JSON schemas that can be included in LLM prompts to encourage structured outputs
 * that match your Kotlin data models. The generated schemas help LLMs understand the expected response format.
 */
public abstract class JsonSchemaGenerator {
    /**
     * Holds intermediate and stateful information about the current schema generation process.
     *
     * @property json [Json] instance used for serialization, provides required meta info needed for schema generation that
     * is not available in [SerialDescriptor].
     * @property descriptor The [SerialDescriptor] currently being processed.
     * @property processedTypeDefs A mutable map of [SerialDescriptor] to [JsonObject], maintaining schema fragments for
     * previously processed types to avoid redundant schema generation.
     * @property currentDefPath A list of [SerialDescriptor] representing the current path in the descriptor hierarchy
     * during schema generation processing.
     * @property descriptionOverrides A map of user-defined properties and types descriptions to override [LLMDescription]
     * from the provided class.
     * @property excludedProperties A set of property names to exclude from the schema generation.
     * @property currentDescription Description for the current element
     */
    public data class GenerationContext(
        public val json: Json,
        public val descriptor: SerialDescriptor,
        public val processedTypeDefs: MutableMap<SerialDescriptor, JsonObject>,
        public val currentDefPath: List<SerialDescriptor>,
        public val descriptionOverrides: Map<String, String>,
        public val excludedProperties: Set<String>,
        public val currentDescription: String?,
    ) {
        /**
         * Helper method that gets description for [descriptor] type from [descriptionOverrides] map
         * or [LLMDescription] annotation.
         *
         * @return Description or `null` if no description is specified.
         */
        public fun getTypeDescription(): String? {
            val typeDescriptionOverride = descriptionOverrides[descriptor.serialName]
            val typeDescriptionAnnotation = descriptor.annotations
                .filterIsInstance<LLMDescription>()
                .firstOrNull()
                ?.description

            return typeDescriptionOverride ?: typeDescriptionAnnotation
        }

        /**
         * Helper method that gets description for an element in [descriptor] from [descriptionOverrides] map
         * or [LLMDescription] annotation.
         *
         * @param index Index of the element in [descriptor]
         *
         * @return Description or `null` if no description is specified.
         */
        public fun getElementDescription(index: Int): String? {
            val elementName = descriptor.getElementName(index)
            val elementAnnotations = descriptor.getElementAnnotations(index)

            val lookupKey = "${descriptor.serialName}.$elementName"
            val elementDescriptionOverride = descriptionOverrides[lookupKey]
            val elementDescriptionAnnotation = elementAnnotations
                .filterIsInstance<LLMDescription>()
                .firstOrNull()
                ?.description

            return elementDescriptionOverride ?: elementDescriptionAnnotation
        }
    }

    /**
     * Generates a JSON schema representation based on the provided input parameters.
     * Should call [process] to process the schema tree.
     *
     * @param json [Json] instance used for serialization, provides required meta info needed for schema generation that
     * is not available in [SerialDescriptor]
     * @param name The name of the schema.
     * @param serializer The serializer for the type for which the schema has to be generated.
     * @param descriptionOverrides A map containing overrides for [LLMDescription].
     * @param excludedProperties A set of property names to exclude from the schema generation.
     */
    public abstract fun generate(
        json: Json,
        name: String,
        serializer: KSerializer<*>,
        descriptionOverrides: Map<String, String>,
        excludedProperties: Set<String> = emptySet(),
    ): LLMParams.Schema.JSON

    /**
     * Implements the visitor pattern by visiting [GenerationContext] to process [GenerationContext.descriptor] and
     * generate a corresponding JSON schema definition.
     *
     * This method processes the provided serial descriptor based on its structural or type-specific characteristics
     * to produce a JSON object representing the schema fragment. Usually, it matches on [SerialDescriptor.kind] and
     * invokes a suitable concrete visit implementation from the methods below.
     *
     * @return a JSON object representing the schema definition for the given descriptor.
     */
    protected abstract fun process(context: GenerationContext): JsonObject

    protected abstract fun processString(context: GenerationContext): JsonObject
    protected abstract fun processBoolean(context: GenerationContext): JsonObject
    protected abstract fun processInteger(context: GenerationContext): JsonObject
    protected abstract fun processNumber(context: GenerationContext): JsonObject
    protected abstract fun processEnum(context: GenerationContext): JsonObject
    protected abstract fun processList(context: GenerationContext): JsonObject
    protected abstract fun processMap(context: GenerationContext): JsonObject
    protected abstract fun processObject(context: GenerationContext): JsonObject
    protected abstract fun processPolymorphic(context: GenerationContext): JsonObject
    protected abstract fun processClassDiscriminator(context: GenerationContext): JsonObject
    protected abstract fun processJsonElement(context: GenerationContext): JsonObject
}

/**
 * Utility function to get all subtype serial descriptors from a given polymorphic serial descriptor, that can handle both
 * [PolymorphicKind.OPEN] and [PolymorphicKind.SEALED] kinds.
 *
 * @param this Serial descriptor of a polymorphic type.
 * @param json [Json] instance
 */
public fun SerialDescriptor.getPolymorphicDescriptors(json: Json): List<SerialDescriptor> {
    /*
      Reference links:
      1. https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization.descriptors/
      2. Useful comment explaining why just serializerModule.getPolymorphicDescriptors() won't work on sealed classes
         https://github.com/Kotlin/kotlinx.serialization/blob/438fb8eab0350fd8238d13798a6e8c3edc2b8b24/core/commonMain/src/kotlinx/serialization/descriptors/ContextAware.kt#L19
     */

    val subclassDescriptor = when (kind) {
        /*
          Sealed descriptor contains fixed fields: TYPE_KEY on the first position (not interesting for us here) and "value"
          on the second — collection of subtype descriptors.
          The latter is what we need.
          Spent more time on this than I want to admit…
         */
        is PolymorphicKind.SEALED -> {
            // Check that "value" element containing subclasses descriptors is present
            require(elementNames.toList().getOrNull(1) == "value") {
                "Expected second element to be 'value', got: ${elementNames.toList()}"
            }

            val subclassesDescriptor = elementDescriptors
                .toList()
                .getOrNull(1)
                ?: throw IllegalArgumentException("Cannot find subclasses descriptor")

            subclassesDescriptor.elementDescriptors.toList()
        }

        is PolymorphicKind.OPEN -> {
            json.serializersModule.getPolymorphicDescriptors(this)
        }

        else -> throw IllegalArgumentException("Unsupported descriptor type: $kind")
    }

    return subclassDescriptor.sortedBy { it.serialName } // to get predictable ordering
}

/**
 * Recursively counts the number of occurrences of the specified [JsonElement] within this [JsonElement].
 *
 * @param this The [JsonElement] to search within.
 * @param value The [JsonElement] to search for.
 */
public fun JsonElement.countElements(value: JsonElement): Int {
    return when {
        this == value -> 1
        this is JsonObject -> values.sumOf { it.countElements(value) }
        this is JsonArray -> sumOf { it.countElements(value) }
        else -> 0
    }
}
