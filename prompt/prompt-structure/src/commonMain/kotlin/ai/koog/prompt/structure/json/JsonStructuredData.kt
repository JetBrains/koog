package ai.koog.prompt.structure.json

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.structure
import ai.koog.prompt.text.TextContentBuilderBase
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
) : StructuredData<TStruct>(id, examples, jsonSchema) {

    /**
     * Represents the type of JSON schema that can be utilized for structured data definition.
     * This defines the level of detail or complexity included in the schema.
     */
    public enum class JsonSchemaType {
        /**
         * Represents the complete schema type in the enumeration, typically used to indicate
         * that the JSON schema should be fully applied or adhered to without simplification.
         */
        FULL,

        /**
         * Represents a simplified schema type used within the JsonSchemaType enumeration.
         * This type is typically used for scenarios where a minimal representation of the schema is sufficient.
         */
        SIMPLE
    }

    override fun parse(text: String): TStruct = structureLanguage.parse(text, serializer)
    override fun pretty(value: TStruct): String = structureLanguage.pretty(value, serializer)

    override fun definition(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*> = builder.apply {
        +"DEFINITION OF $id"
        +"The $id format is defined only and solely with JSON, without any additional characters, backticks or anything similar."
        newline()

        +"You must adhere to the following JSON schema:"
        +structureLanguage.pretty(jsonSchema.schema)

        when {
            examples.isEmpty() -> {}
            examples.size == 1 -> {
                +"Here is an example of a valid response:"
                structure(structureLanguage, examples.first(), serializer)
            }

            else -> {
                +"Here are some examples of valid responses:"
                examples.forEach {
                    structure(structureLanguage, it, serializer)
                }
            }
        }

        newline()
    }

    /**
     * Companion object for the `JsonStructuredData` class, providing utility methods to facilitate the
     * creation of JSON structures with associated schema generation.
     */
    public companion object {
        // TODO: Class.simpleName is the only reason to make the function inline, perhaps we can hide most of the implementation
        /**
         * Factory method to create JSON structure with auto-generated JSON schema.
         *
         * Example usage:
         * ```kotlin
         * @Serializable
         * @SerialName("LatLon")
         * @LLMDescription("Coordinates of the location in latitude and longitude format")
         * data class LatLon(
         *     @LLMDescription("Latitude of the location")
         *     val lat: Double,
         *     @LLMDescription("Longitude of the location")
         *     val lon: Double
         * )
         *
         * @Serializable
         * @SerialName("WeatherDatapoint")
         * @LLMDescription("Weather datapoint for a given timestamp in the given location")
         * data class WeatherDatapoint(
         *     @LLMDescription("Forecast timestamp")
         *     val timestampt: Long,
         *     @LLMDescription("Forecast temperature in Celsius")
         *     val temperature: Double,
         *     @LLMDescription("Precipitation in mm/h")
         *     val precipitation: Double,
         * )
         *
         * @Serializable
         * @SerialName("Weather")
         * data class Weather(
         *     @LLMDescription("Country code of the location")
         *     val countryCode: String,
         *     @LLMDescription("City name of the location")
         *     val cityName: String,
         *     @LLMDescription("Coordinates of the location")
         *     val latLon: LatLon,
         *     val forecast: List<WeatherDatapoint>,
         * )
         *
         * val weatherStructure = JsonStructuredData.createJsonStructure<WeatherForecast>(
         *     // some models don't work well with full json schema, so you may try simple, but it has more limitations (no polymorphism!)
         *     schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
         *     schemaType = JsonStructuredData.JsonSchemaType.FULL,
         *     descriptionOverrides = mapOf(
         *         // type descriptions
         *         "Weather" to "Weather forecast for a given location", // the class doesn't have description annotation, this will add description
         *         "WeatherDatapoint" to "Weather data at a given time", // the class has description annotation, this will override description
         *
         *         // property descriptions
         *         "Weather.forecast" to "List of forecasted weather conditions for a given location", // the property doesn't have description annotation, this will add description
         *         "Weather.countryCode" to "Country code of the location in the ISO2 format", // the property has description annotation, this will override description
         *     )
         * )
         * ```
         *
         * @param id Unique identifier for the structure.
         * @param serializer Serializer used for converting the data to and from JSON.
         * @param json JSON configuration instance used for serialization.
         * @param schemaFormat Format of the generated schema, can be simple or detailed.
         * @param maxDepth Maximum recursion depth when generating schema to prevent infinite recursion for circular references.
         * @param descriptionOverrides Optional map of serial class names and property names to descriptions.
         * If a property/type is already described with [LLMDescription] annotation, value from the map will override this description.
         * @param examples List of example data items that conform to the structure, used for demonstrating valid formats.
         * @param schemaType Type of JSON schema to generate, determines the level of detail in the schema.
         */
        public inline fun <reified T> createJsonStructure(
            id: String = T::class.simpleName ?: error("Class name is required for JSON structure"),
            serializer: KSerializer<T> = serializer<T>(),
            json: Json = JsonStructureLanguage.defaultJson,
            schemaFormat: JsonSchemaGenerator.SchemaFormat = JsonSchemaGenerator.SchemaFormat.Simple,
            maxDepth: Int = 20,
            descriptionOverrides: Map<String, String> = emptyMap(),
            examples: List<T> = emptyList(),
            schemaType: JsonSchemaType = JsonSchemaType.SIMPLE
        ): StructuredData<T> {
            val structureLanguage = JsonStructureLanguage(json)
            val schema = JsonSchemaGenerator(
                json,
                schemaFormat,
                maxDepth
            ).generate(id, serializer, descriptionOverrides)

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
    }
}
