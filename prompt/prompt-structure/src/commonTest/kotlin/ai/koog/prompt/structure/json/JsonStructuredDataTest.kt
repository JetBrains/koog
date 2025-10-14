package ai.koog.prompt.structure.json

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.prompt.text.text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonStructuredDataTest {

    val weatherInfoName = "WeatherInfo"

    @Serializable
    @SerialName("WeatherInfo")
    @LLMDescription("Weather information for a location")
    data class WeatherInfo(
        @property:LLMDescription("Name of the city")
        val city: String,
        @property:LLMDescription("Temperature in Celsius")
        val temperature: Int,
        @property:LLMDescription("Weather description")
        val description: String,
        @property:LLMDescription("Humidity percentage")
        val humidity: Int? = null
    )

    @Serializable
    @SerialName("ComplexData")
    @LLMDescription("Complex data structure with nested elements")
    data class ComplexData(
        @property:LLMDescription("List of weather info")
        val locations: List<WeatherInfo>,
        @property:LLMDescription("Summary text")
        val summary: String,
        @property:LLMDescription("Metadata map")
        val metadata: Map<String, String> = emptyMap()
    )

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testInlineJsonStructureCreation() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        assertEquals(weatherInfoName, structure.id)
        assertEquals(serializer<WeatherInfo>(), structure.serializer)
        assertNotNull(structure.schema)
    }

    @Test
    fun testCreateJsonStructureWithStandardGenerator() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>(
            schemaGenerator = StandardJsonSchemaGenerator.Default
        )

        assertEquals(weatherInfoName, structure.id)
        assertNotNull(structure.schema)
        assertIs<LLMParams.Schema.JSON.Standard>(structure.schema)
        assertEquals(structure.serializer, serializer<WeatherInfo>())
    }

    @Test
    fun testCreateJsonStructureWithBasicGenerator() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>(
            schemaGenerator = BasicJsonSchemaGenerator.Default
        )

        assertEquals(weatherInfoName, structure.id)
        assertNotNull(structure.schema)
        assertIs<LLMParams.Schema.JSON.Basic>(structure.schema)
        assertEquals(structure.serializer, serializer<WeatherInfo>())
    }

    @Test
    fun testJsonStructureWithCustomDescriptions() {
        val descriptionOverrides = mapOf(
            "WeatherInfo.city" to "Name of the city (custom)",
            "WeatherInfo.temperature" to "Temperature in Celsius (custom)"
        )

        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>(
            schemaGenerator = StandardJsonSchemaGenerator.Default,
            descriptionOverrides = descriptionOverrides
        )

        val schemaJson = json.encodeToString(structure.schema.schema)
        assertTrue(schemaJson.contains("Name of the city (custom)"))
        assertTrue(schemaJson.contains("Temperature in Celsius (custom)"))
    }

    @Test
    fun testJsonStructureWithExamples() {
        val examples = listOf(
            WeatherInfo("London", 15, "Rainy", 80),
            WeatherInfo("Paris", 22, "Sunny", 65)
        )

        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>(
            examples = examples
        )

        assertEquals(2, structure.examples.size)
        assertEquals("London", structure.examples[0].city)
        assertEquals("Paris", structure.examples[1].city)
    }

    @Test
    fun testJsonStructureParsing() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val jsonString = """{"city": "Tokyo", "temperature": 25, "description": "Clear", "humidity": 60}"""

        val parsed = structure.parse(jsonString)

        assertEquals("Tokyo", parsed.city)
        assertEquals(25, parsed.temperature)
        assertEquals("Clear", parsed.description)
        assertEquals(60, parsed.humidity)
    }

    @Test
    fun testJsonStructureSerialization() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val data = WeatherInfo("Berlin", 20, "Cloudy", 70)

        val serialized = structure.pretty(data)

        assertTrue(serialized.contains("Berlin"))
        assertTrue(serialized.contains("20"))
        assertTrue(serialized.contains("Cloudy"))
        assertTrue(serialized.contains("70"))
    }

    @Test
    fun testComplexJsonStructure() {
        val structure = JsonStructuredData.createJsonStructure<ComplexData>(
            schemaGenerator = StandardJsonSchemaGenerator.Default
        )

        val complexData = ComplexData(
            locations = listOf(
                WeatherInfo("London", 15, "Rainy", 80),
                WeatherInfo("Paris", 22, "Sunny", 65)
            ),
            summary = "Mixed weather conditions",
            metadata = mapOf("source" to "weather-api", "version" to "1.0")
        )

        val serialized = structure.pretty(complexData)
        val parsed = structure.parse(serialized)

        assertEquals(2, parsed.locations.size)
        assertEquals("London", parsed.locations[0].city)
        assertEquals("Paris", parsed.locations[1].city)
        assertEquals("Mixed weather conditions", parsed.summary)
        assertEquals("weather-api", parsed.metadata["source"])
    }

    @Test
    fun testJsonStructureWithNullableFieldsParseNullable() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()

        val jsonWithNull = """{"city": "Amsterdam", "temperature": 15, "description": "Overcast"}"""
        val parsedWithNull = structure.parse(jsonWithNull)
        assertEquals(null, parsedWithNull.humidity)
    }

    @Test
    fun testJsonStructureWithAllFieldsParseNullable() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val jsonWithHumidity = """{"city": "Amsterdam", "temperature": 15, "description": "Overcast", "humidity": 75}"""
        val parsedWithHumidity = structure.parse(jsonWithHumidity)
        assertEquals(75, parsedWithHumidity.humidity)
    }

    @Test
    fun testJsonStructureDefinitionContent() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>(
            examples = listOf(WeatherInfo("Example", 20, "Example weather", 50))
        )

        val definition = text { structure.definition(this) }

        assertTrue(definition.contains("DEFINITION OF WeatherInfo"))
        assertTrue(definition.contains("JSON schema"))
        assertTrue(definition.contains("EXAMPLES"))
        assertTrue(definition.contains("RESULT"))
        assertTrue(definition.contains("WITHOUT ANY free text comments"))
        assertTrue(definition.contains("start with { and end with }"))
        assertTrue(definition.contains("Example")) // from examples
    }

    @Test
    fun testStructuredOutputModes() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()

        val nativeMode = StructuredOutput.Native(structure)
        assertEquals(structure, nativeMode.structure)

        val manualMode = StructuredOutput.Manual(structure)
        assertEquals(structure, manualMode.structure)
    }

    @Test
    fun testStructuredOutputConfig() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val nativeOutput = StructuredOutput.Native(structure)
        val manualOutput = StructuredOutput.Manual(structure)

        val config = StructuredOutputConfig(
            default = manualOutput,
            byProvider = mapOf(
                LLMProvider.OpenAI to nativeOutput
            )
        )

        assertEquals(manualOutput, config.default)
        assertEquals(nativeOutput, config.byProvider[LLMProvider.OpenAI])
    }

    @Test
    fun testJsonWithMarkdownBlockParsing() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val jsonString =
            """```
            {
                "city": "London",
                "temperature": 15,
                "description": "Cloudy",
                "humidity": 80
            }
            ```
            """.trimMargin()

        val parsed = structure.parse(jsonString)

        assertEquals("London", parsed.city)
        assertEquals(15, parsed.temperature)
        assertEquals("Cloudy", parsed.description)
        assertEquals(80, parsed.humidity)
    }

    @Test
    fun testJsonWithMarkdownLanguageBlockParsing() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val jsonString =
            """```json
            {
                "city": "London",
                "temperature": 15,
                "description": "Cloudy",
                "humidity": 80
            }
            ```
            """.trimMargin()

        val parsed = structure.parse(jsonString)

        assertEquals("London", parsed.city)
        assertEquals(15, parsed.temperature)
        assertEquals("Cloudy", parsed.description)
        assertEquals(80, parsed.humidity)
    }

    @Test
    fun testJsonWithSingleLineMarkdownParsing() {
        val structure = JsonStructuredData.createJsonStructure<WeatherInfo>()
        val jsonString = """`{"city": "Dublin","temperature": 12,"description": "Rainy","humidity": 90}`""".trimMargin()

        val parsed = structure.parse(jsonString)

        assertEquals("Dublin", parsed.city)
        assertEquals(12, parsed.temperature)
        assertEquals("Rainy", parsed.description)
        assertEquals(90, parsed.humidity)
    }
}
