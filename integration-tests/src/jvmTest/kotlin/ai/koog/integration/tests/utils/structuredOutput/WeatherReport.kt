package ai.koog.integration.tests.utils.structuredOutput

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
@SerialName("WeatherReport")
@LLMDescription("Weather report for a specific location")
data class WeatherReport(
    @property:LLMDescription("Name of the city")
    val city: String,
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    @property:LLMDescription("Brief weather description")
    val description: String,
    @property:LLMDescription("Humidity percentage")
    val humidity: Int
)

@OptIn(InternalStructuredOutputApi::class)
fun getStructure(schemaGenerator: StandardJsonSchemaGenerator) = JsonStructure.create<WeatherReport>(
    json = Json,
    schemaGenerator = schemaGenerator,
    descriptionOverrides = mapOf(
        "WeatherReport.city" to "Name of the city or location",
        "WeatherReport.temperature" to "Current temperature in Celsius degrees"
    ),
    examples = listOf(
        WeatherReport("Moscow", 20, "Sunny", 50)
    )
)

fun getFixingParser(model: LLModel): StructureFixingParser {
    return StructureFixingParser(
        model = model,
        retries = 3
    )
}

fun getNativeConfig(schemaGenerator: StandardJsonSchemaGenerator) = StructuredRequestConfig(
    default = StructuredRequest.Native(getStructure(schemaGenerator)),
)

fun getManualConfig(schemaGenerator: StandardJsonSchemaGenerator) = StructuredRequestConfig(
    default = StructuredRequest.Manual(getStructure(schemaGenerator)),
)

val weatherStructuredOutputPrompt = Prompt.build("test-structured-json") {
    system {
        +"You are a weather forecasting assistant."
        +"When asked for a weather forecast, provide a realistic but fictional forecast."
    }
    user(
        "What is the weather forecast for London? Please provide temperature, description, and humidity if available."
    )
}

// Asserts weather report fields are valid
fun checkWeatherStructuredOutputResponse(result: Result<StructuredResponse<WeatherReport>>) {
    val response = result.getOrThrow().data
    assertNotNull(response)

    assertEquals("London", response.city, "City should be London, got: ${response.city}")
    assertTrue(
        response.temperature in -50..60,
        "Temperature should be realistic, got: ${response.temperature}"
    )
    assertTrue(response.description.isNotBlank(), "Description should not be empty")
    assertTrue(
        response.humidity >= 0,
        "Humidity should be a valid percentage, got: ${response.humidity}"
    )
}
