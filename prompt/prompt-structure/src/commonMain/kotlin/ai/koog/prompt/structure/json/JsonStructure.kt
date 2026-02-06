package ai.koog.prompt.structure.json

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.Structure
import ai.koog.prompt.structure.StructuredOutputPrompts
import ai.koog.prompt.structure.json.generator.JsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.prompt.text.TextContentBuilderBase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Represents a structure for handling and interacting with structured data of a specified type.
 *
 * @param TStruct The type of data to be structured.
 * @property id A unique identifier for the structure.
 * @property schema Schema of this structure
 * @property examples A list of example data items that conform to the structure.
 * @property serializer The serializer used to convert the data to and from JSON.
 * @property json [kotlinx.serialization.json.Json] instance to perform de/serialization.
 * @param definitionPrompt Prompt with definition, explaining the structure to the LLM.
 * Default is [JsonStructure.defaultDefinitionPrompt]
 * @param examplesPrompt Prompt with examples of valid formats for the structured data.
 * Default is [StructuredOutputPrompts.examplesPrompt]
*/
public class JsonStructure<TStruct>(
    id: String,
    schema: LLMParams.Schema.JSON,
    examples: List<TStruct>,
    public val serializer: KSerializer<TStruct>,
    public val json: Json,
    private val definitionPrompt: (
        builder: TextContentBuilderBase<*>,
        structure: JsonStructure<TStruct>
    ) -> TextContentBuilderBase<*> = ::defaultDefinitionPrompt,
    private val examplesPrompt: (
        builder: TextContentBuilderBase<*>,
        structure: JsonStructure<TStruct>,
    ) -> TextContentBuilderBase<*> = StructuredOutputPrompts::examplesPrompt,
) : Structure<TStruct, LLMParams.Schema.JSON>(id, schema, examples) {

    override fun parse(text: String): TStruct = json.decodeFromString(serializer, text.trim().stripMarkdown())

    override fun pretty(value: TStruct): String = json.encodeToString(serializer, value)

    override fun definition(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*> = definitionPrompt(builder, this)

    override fun examples(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*> = examplesPrompt(builder, this)

    // LLMs often enclose JSON output in Markdown code blocks.
    // Stripping them saves extra LLM call which would be required to fix the string.
    private fun String.stripMarkdown() = when {
        startsWith("```json") -> removePrefix("```json").removeSuffix("```")
        startsWith("```") -> removePrefix("```").removeSuffix("```")
        startsWith("`") -> removePrefix("`").removeSuffix("`")
        startsWith("~~~json") -> removePrefix("~~~json").removeSuffix("~~~").trim()
        startsWith("~~~") -> removePrefix("~~~").removeSuffix("~~~").trim()
        else -> this
    }

    /**
     * Companion object for the [JsonStructure] class, providing utility methods to facilitate the
     * creation of JSON structures with associated schema generation.
     */
    public companion object {
        /**
         * Default [Json] instance for [JsonStructure.json]
         */
        public val defaultJson: Json = Json {
            prettyPrint = true
            explicitNulls = false
            isLenient = true
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
            classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
        }

        /**
         * Default prompt explaining the structure of [JsonStructure] to the LLM.
         */
        public fun <TStruct> defaultDefinitionPrompt(
            builder: TextContentBuilderBase<*>,
            structuredData: JsonStructure<TStruct>
        ): TextContentBuilderBase<*> = builder.apply {
            with(structuredData) {
                markdown {
                    h3("DEFINITION OF $id")

                    +"The $id format is defined only and solely with JSON, without any additional characters, comments, backticks or anything similar."
                    br()

                    +"You must adhere to the following JSON schema:"
                    +json.encodeToString(schema.schema)
                    br()

                    StructuredOutputPrompts.examplesPrompt(this, structuredData)

                    h2("RESULT")
                    +"Provide ONLY the resulting JSON, WITHOUT ANY free text comments, backticks, or other symbols."
                    +"Output should start with { and end with }"

                    newline()
                }
            }
        }

        /**
         * Factory method to create JSON structure with auto-generated JSON schema.
         *
         * Example usage:
         * ```kotlin
         * @Serializable
         * @SerialName("LatLon")
         * @LLMDescription("Coordinates of the location in latitude and longitude format")
         * data class LatLon(
         *     @property:LLMDescription("Latitude of the location")
         *     val lat: Double,
         *     @property:LLMDescription("Longitude of the location")
         *     val lon: Double
         * )
         *
         * @Serializable
         * @SerialName("WeatherDatapoint")
         * @LLMDescription("Weather datapoint for a given timestamp in the given location")
         * data class WeatherDatapoint(
         *     @property:LLMDescription("Forecast timestamp")
         *     val timestampt: Long,
         *     @property:LLMDescription("Forecast temperature in Celsius")
         *     val temperature: Double,
         *     @property:LLMDescription("Precipitation in mm/h")
         *     val precipitation: Double,
         * )
         *
         * @Serializable
         * @SerialName("Weather")
         * data class Weather(
         *     @property:LLMDescription("Country code of the location")
         *     val countryCode: String,
         *     @property:LLMDescription("City name of the location")
         *     val cityName: String,
         *     @property:LLMDescription("Coordinates of the location")
         *     val latLon: LatLon,
         *     val forecast: List<WeatherDatapoint>,
         * )
         *
         * val weatherStructure = JsonStructure.create<WeatherForecast>(
         *     id = "WeatherForecast",
         *     serializer = WeatherForecast.serializer(),
         *     // some models don't work well with full json schema, so you may try simple, but it has more limitations (e.g. limited polymorphism)
         *     schemaGenerator = FullJsonSchemaGenerator,
         *     descriptionOverrides = mapOf(
         *         // type descriptions
         *         "Weather" to "Weather forecast for a given location", // the class doesn't have description annotation, this will add description
         *         "WeatherDatapoint" to "Weather data at a given time", // the class has description annotation, this will override description
         *
         *         // property descriptions
         *         "Weather.forecast" to "List of forecasted weather conditions for a given location", // the property doesn't have description annotation, this will add description
         *         "Weather.countryCode" to "Country code of the location in the ISO2 format", // the property has description annotation, this will override description
         *     ),
         *     // You can also exclude properties from schema generation completely
         *     excludedProperties = setOf("Weather.cityName"),
         * )
         * ```
         *
         * @param id Unique identifier for the structure.
         * @param serializer Serializer used for converting the data to and from JSON.
         * @param json JSON configuration instance used for serialization.
         * @param schemaGenerator JSON schema generator
         * @param descriptionOverrides Optional map of serial class names and property names to descriptions.
         * If a property/type is already described with [ai.koog.agents.core.tools.annotations.LLMDescription] annotation, value from the map will override this description.
         * @param excludedProperties Optional set of property names to exclude from the schema generation.
         * @param examples List of example data items that conform to the structure, used for demonstrating valid formats.
         * @param definitionPrompt Prompt with definition, explaining the structure to the LLM when the manual mode for
         * structured output is used. Default is [JsonStructure.defaultDefinitionPrompt]
         */
        public fun <TStruct> create(
            id: String,
            serializer: KSerializer<TStruct>,
            json: Json = defaultJson,
            schemaGenerator: JsonSchemaGenerator = StandardJsonSchemaGenerator.Default,
            descriptionOverrides: Map<String, String> = emptyMap(),
            excludedProperties: Set<String> = emptySet(),
            examples: List<TStruct> = emptyList(),
            definitionPrompt: (
                builder: TextContentBuilderBase<*>,
                structuredData: JsonStructure<TStruct>
            ) -> TextContentBuilderBase<*> = ::defaultDefinitionPrompt
        ): JsonStructure<TStruct> {
            return JsonStructure(
                id = id,
                schema = schemaGenerator.generate(json, id, serializer, descriptionOverrides, excludedProperties),
                examples = examples,
                serializer = serializer,
                json = json,
                definitionPrompt = definitionPrompt,
            )
        }

        /**
         *
         * Factory method to create JSON structure with auto-generated JSON schema.
         *
         * This is a convenience inline overload that automatically deduces `id` and `serializer` from passed type.
         * Check non-inline version of `createJsonStructure` for detailed information.
         *
         * @param json JSON configuration instance used for serialization.
         * @param schemaGenerator JSON schema generator.
         * Make sure to select the correct one for the LLM you are using this structure with, since different models have
         * slightly different formats.
         * @param descriptionOverrides Optional map of serial class names and property names to descriptions.
         * If a property/type is already described with [ai.koog.agents.core.tools.annotations.LLMDescription] annotation, value from the map will override this description.
         * @param excludedProperties Optional set of property names to exclude from the schema generation.
         * @param examples List of example data items that conform to the structure, used for demonstrating valid formats.
         * @param definitionPrompt Prompt with definition, explaining the structure to the LLM when the manual mode for
         * structured output is used. Default is [JsonStructure.defaultDefinitionPrompt]
         */
        public inline fun <reified TStruct> create(
            json: Json = defaultJson,
            schemaGenerator: JsonSchemaGenerator = StandardJsonSchemaGenerator.Default,
            descriptionOverrides: Map<String, String> = emptyMap(),
            excludedProperties: Set<String> = emptySet(),
            examples: List<TStruct> = emptyList(),
            noinline definitionPrompt: (
                builder: TextContentBuilderBase<*>,
                structuredData: JsonStructure<TStruct>
            ) -> TextContentBuilderBase<*> = ::defaultDefinitionPrompt
        ): JsonStructure<TStruct> {
            val serializer = serializer<TStruct>()

            return create(
                id = serializer.descriptor.serialName.substringAfterLast("."),
                serializer = serializer,
                json = json,
                schemaGenerator = schemaGenerator,
                descriptionOverrides = descriptionOverrides,
                excludedProperties = excludedProperties,
                examples = examples,
                definitionPrompt = definitionPrompt,
            )
        }
    }
}
