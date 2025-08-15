package ai.koog.agents.example.structuredoutput

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.structure.GoogleStandardJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.structure.OpenAIStandardJsonSchemaGenerator
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.prompt.text.text
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This is a more advanced example showing how to configure various parameters of structured output manually, to fine-tune
 * it for your needs when necessary.
 *
 * Structured output that uses "full" JSON schema.
 * More advanced features are supported, e.g. polymorphism and recursive type references, and schemas can be more complex.
 */

@Serializable
@SerialName("FullWeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class FullWeatherForecast(
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    // properties with default values
    @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String = "sunny",
    // nullable properties
    @property:LLMDescription("Chance of precipitation in percentage")
    val precipitation: Int?,
    // nested classes
    @property:LLMDescription("Coordinates of the location")
    val latLon: LatLon,
    // enums
    val pollution: Pollution,
    // polymorphism
    val alert: WeatherAlert,
    // lists
    @property:LLMDescription("List of news articles")
    val news: List<WeatherNews>,
//    // maps (string keys only, some providers don't support maps at all)
//    @property:LLMDescription("Map of weather sources")
//    val sources: Map<String, WeatherSource>
) {
    // Nested classes
    @Serializable
    @SerialName("LatLon")
    data class LatLon(
        @property:LLMDescription("Latitude of the location")
        val lat: Double,
        @property:LLMDescription("Longitude of the location")
        val lon: Double
    )

    // Nested classes in lists...
    @Serializable
    @SerialName("WeatherNews")
    data class WeatherNews(
        @property:LLMDescription("Title of the news article")
        val title: String,
        @property:LLMDescription("Link to the news article")
        val link: String
    )

    // ... and maps (but only with string keys!)
    @Suppress("unused")
    @Serializable
    @SerialName("WeatherSource")
    data class WeatherSource(
        @property:LLMDescription("Name of the weather station")
        val stationName: String,
        @property:LLMDescription("Authority of the weather station")
        val stationAuthority: String
    )

    // Enums
    @Suppress("unused")
    @SerialName("Pollution")
    @Serializable
    enum class Pollution {
        @SerialName("None")
        None,

        @SerialName("LOW")
        Low,

        @SerialName("MEDIUM")
        Medium,

        @SerialName("HIGH")
        High
    }

    /*
     Polymorphism:
      1. Closed with sealed classes,
      2. Open: non-sealed classes with subclasses registered in json config
         https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md#registered-subclasses
     */
    @Suppress("unused")
    @Serializable
    @SerialName("WeatherAlert")
    sealed class WeatherAlert {
        abstract val severity: Severity
        abstract val message: String

        @Serializable
        @SerialName("Severity")
        enum class Severity { Low, Moderate, Severe, Extreme }

        @Serializable
        @SerialName("StormAlert")
        data class StormAlert(
            override val severity: Severity,
            override val message: String,
            @property:LLMDescription("Wind speed in km/h")
            val windSpeed: Double
        ) : WeatherAlert()

        @Serializable
        @SerialName("FloodAlert")
        data class FloodAlert(
            override val severity: Severity,
            override val message: String,
            @property:LLMDescription("Expected rainfall in mm")
            val expectedRainfall: Double
        ) : WeatherAlert()

        @Serializable
        @SerialName("TemperatureAlert")
        data class TemperatureAlert(
            override val severity: Severity,
            override val message: String,
            @property:LLMDescription("Temperature threshold in Celsius")
            val threshold: Int, // in Celsius
            @property:LLMDescription("Whether the alert is a heat warning")
            val isHeatWarning: Boolean
        ) : WeatherAlert()
    }
}

data class FullWeatherForecastRequest(
    val city: String,
    val country: String
)

private val json = Json {
    prettyPrint = true
}

fun main(): Unit = runBlocking {
    // Optional examples, to help LLM understand the format better in manual mode
    val exampleForecasts = listOf(
        FullWeatherForecast(
            temperature = 18,
            conditions = "Cloudy",
            precipitation = 30,
            latLon = FullWeatherForecast.LatLon(lat = 34.0522, lon = -118.2437),
            pollution = FullWeatherForecast.Pollution.Medium,
            alert = FullWeatherForecast.WeatherAlert.StormAlert(
                severity = FullWeatherForecast.WeatherAlert.Severity.Moderate,
                message = "Possible thunderstorms in the evening",
                windSpeed = 45.5
            ),
            news = listOf(
                FullWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                FullWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
//            sources = mapOf(
//                "MeteorologicalWatch" to FullWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch",
//                    stationAuthority = "US Department of Agriculture"
//                ),
//                "MeteorologicalWatch2" to FullWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch2",
//                    stationAuthority = "US Department of Agriculture"
//                )
//            )
        ),
        FullWeatherForecast(
            temperature = 10,
            conditions = "Rainy",
            precipitation = null,
            latLon = FullWeatherForecast.LatLon(lat = 37.7739, lon = -122.4194),
            pollution = FullWeatherForecast.Pollution.Low,
            alert = FullWeatherForecast.WeatherAlert.FloodAlert(
                severity = FullWeatherForecast.WeatherAlert.Severity.Severe,
                message = "Heavy rainfall may cause local flooding",
                expectedRainfall = 75.2
            ),
            news = listOf(
                FullWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                FullWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
//            sources = mapOf(
//                "MeteorologicalWatch" to WeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch",
//                    stationAuthority = "US Department of Agriculture"
//                ),
//            )
        )
    )

    /*
     This structure has a generic schema that is suitable for manual structured output mode.
     But to use native structured output support in different LLM providers you might need to use custom JSON schema generators
     that would produce the schema these providers expect.
     */
    val genericWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        // Some models might not work well with json schema, so you may try simple, but it has more limitations (no polymorphism!)
        schemaGenerator = StandardJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    println("Generated generic JSON schema:\n${json.encodeToString(genericWeatherStructure.schema.schema)}")
    /*
     These are specific structure definitions with schemas in format that particular LLM providers understand in their native
     structured output.
     */

    val openAiWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        schemaGenerator = OpenAIStandardJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    val googleWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        schemaGenerator = GoogleStandardJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    val agentStrategy = strategy<FullWeatherForecastRequest, FullWeatherForecast>("advanced-full-weather-forecast") {
        val prepareRequest by node<FullWeatherForecastRequest, String> { request ->
            text {
                +"Requesting forecast for"
                +"City: ${request.city}"
                +"Country: ${request.country}"
            }
        }

        @Suppress("DuplicatedCode")
        val getStructuredForecast by nodeLLMRequestStructured(
            config = StructuredOutputConfig(
                byProvider = mapOf(
                    // Native modes leveraging native structured output support in models, with custom definitions for LLM providers that might have different format.
                    LLMProvider.OpenAI to StructuredOutput.Native(openAiWeatherStructure),
                    LLMProvider.Google to StructuredOutput.Native(googleWeatherStructure),
                    // Anthropic does not support native structured output yet.
                    LLMProvider.Anthropic to StructuredOutput.Manual(genericWeatherStructure),
                ),

                // Fallback manual structured output mode, via explicit prompting with additional message, not native model support
                default = StructuredOutput.Manual(genericWeatherStructure),

                // Helper parser to attempt a fix if a malformed output is produced.
                fixingParser = StructureFixingParser(
                    fixingModel = AnthropicModels.Haiku_3_5,
                    retries = 2,
                ),
            )
        )

        nodeStart then prepareRequest then getStructuredForecast
        edge(getStructuredForecast forwardTo nodeFinish transformed { it.getOrThrow().structure })
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("weather-forecast") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, provide a realistic but fictional forecast.
                """.trimIndent()
            )
        },
        model = GoogleModels.Gemini2_5Flash,
        maxAgentIterations = 5
    )

    val agent = AIAgent<FullWeatherForecastRequest, FullWeatherForecast>(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
            LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey),
            LLMProvider.Google to GoogleLLMClient(ApiKeyService.googleApiKey),
        ),
        strategy = agentStrategy, // no tools needed for this example
        agentConfig = agentConfig
    ) {
        handleEvents {
            onAgentRunError { eventContext ->
                println("An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}")
            }
        }
    }

    println(
        """
        === Full Weather Forecast Example ===
        This example demonstrates how to use structured output with full schema support
        to get properly structured output from the LLM.
        """.trimIndent()
    )

    val result: FullWeatherForecast = agent.run(FullWeatherForecastRequest(city = "New York", country = "USA"))
    println("Agent run result: $result")
}
