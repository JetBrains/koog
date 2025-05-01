package ai.grazie.code.agents.example.structureddata

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.nodeLLMSendStageInput
import ai.grazie.code.prompt.structure.json.JsonSchemaGenerator
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.grazie.code.prompt.structure.json.LLMDescription
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A showcase what structured output can handle and how to use it
 */

// Plain serializable class
@Serializable
@SerialName("WeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class WeatherForecast(
    @LLMDescription("Temperature in Celsius")
    val temperature: Int,
    @LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String,
    @LLMDescription("Chance of precipitation in percentage")
    val precipitation: Int,
    @LLMDescription("Coordinates of the location")
    val latLon: LatLon,
    val pollution: Pollution,
    val alert: WeatherAlert,
    // lists
    @LLMDescription("List of news articles")
    val news: List<WeatherNews>,
    // maps
    @LLMDescription("Map of weather sources")
    val sources: Map<String, WeatherSource>
) {
    // Nested classes
    @Serializable
    @SerialName("LatLon")
    data class LatLon(
        @LLMDescription("Latitude of the location")
        val lat: Double,
        @LLMDescription("Longitude of the location")
        val lon: Double
    )

    // Nested classes in lists...
    @Serializable
    @SerialName("WeatherNews")
    data class WeatherNews(
        @LLMDescription("Title of the news article")
        val title: String,
        @LLMDescription("Link to the news article")
        val link: String
    )

    // ... and maps (but only with string keys)
    @Serializable
    @SerialName("WeatherSource")
    data class WeatherSource(
        @LLMDescription("Name of the weather station")
        val stationName: String,
        @LLMDescription("Authority of the weather station")
        val stationAuthority: String
    )

    // Enums
    @Suppress("unused")
    @SerialName("Pollution")
    @Serializable
    enum class Pollution { Low, Medium, High }

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
            @LLMDescription("Wind speed in km/h")
            val windSpeed: Double // km/h
        ) : WeatherAlert()

        @Serializable
        @SerialName("FloodAlert")
        data class FloodAlert(
            override val severity: Severity,
            override val message: String,
            @LLMDescription("Expected rainfall in mm")
            val expectedRainfall: Double // mm
        ) : WeatherAlert()

        @Serializable
        @SerialName("TemperatureAlert")
        data class TemperatureAlert(
            override val severity: Severity,
            override val message: String,
            @LLMDescription("Temperature threshold in Celsius")
            val threshold: Int, // in Celsius
            @LLMDescription("Whether the alert is a heat warning")
            val isHeatWarning: Boolean
        ) : WeatherAlert()
    }
}

fun main(): Unit = runBlocking {
    val executor: CodePromptExecutor = null!!

    // Optional examples, to help LLM understand the format better
    val exampleForecasts = listOf(
        WeatherForecast(
            temperature = 25,
            conditions = "Sunny",
            precipitation = 0,
            latLon = WeatherForecast.LatLon(lat = 40.7128, lon = -74.006),
            pollution = WeatherForecast.Pollution.Low,
            alert = WeatherForecast.WeatherAlert.StormAlert(
                severity = WeatherForecast.WeatherAlert.Severity.Moderate,
                message = "Possible thunderstorms in the evening",
                windSpeed = 45.5
            ),
            news = emptyList(),
            sources = emptyMap()
        ),
        WeatherForecast(
            temperature = 18,
            conditions = "Cloudy",
            precipitation = 30,
            latLon = WeatherForecast.LatLon(lat = 34.0522, lon = -118.2437),
            pollution = WeatherForecast.Pollution.Medium,
            alert = WeatherForecast.WeatherAlert.StormAlert(
                severity = WeatherForecast.WeatherAlert.Severity.Moderate,
                message = "Possible thunderstorms in the evening",
                windSpeed = 45.5
            ),
            news = listOf(
                WeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                WeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
            sources = mapOf(
                "MeteorologicalWatch" to WeatherForecast.WeatherSource(
                    stationName = "MeteorologicalWatch",
                    stationAuthority = "US Department of Agriculture"
                ),
                "MeteorologicalWatch2" to WeatherForecast.WeatherSource(
                    stationName = "MeteorologicalWatch2",
                    stationAuthority = "US Department of Agriculture"
                )
            )
        ),
        WeatherForecast(
            temperature = 10,
            conditions = "Rainy",
            precipitation = 90,
            latLon = WeatherForecast.LatLon(lat = 37.7739, lon = -122.4194),
            pollution = WeatherForecast.Pollution.Low,
            alert = WeatherForecast.WeatherAlert.FloodAlert(
                severity = WeatherForecast.WeatherAlert.Severity.Severe,
                message = "Heavy rainfall may cause local flooding",
                expectedRainfall = 75.2
            ),
            news = listOf(
                WeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                WeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
            sources = mapOf(
                "MeteorologicalWatch" to WeatherForecast.WeatherSource(
                    stationName = "MeteorologicalWatch",
                    stationAuthority = "US Department of Agriculture"
                ),
            )
        )
    )

    val weatherForecastStructure = JsonStructuredData.createJsonStructure<WeatherForecast>(
        // some models don't work well with json schema, so you may try simple, but it has more limitations (no polymorphism!)
        schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
        examples = exampleForecasts,
        schemaType = JsonStructuredData.JsonSchemaType.SIMPLE
    )

    val agentStrategy = strategy("weather-forecast") {
        stage("weather") {
            val setup by nodeLLMSendStageInput()

            val getStructuredForecast by node<Message.Response, String> { _ ->
                val structuredResponse = llm.writeSession {
                    this.requestLLMStructured(
                        structure = weatherForecastStructure,
                        // the model that would handle coercion if the output does not conform to the requested structure
                        fixingModel = OllamaModels.Meta.LLAMA_3_2,
                    )
                }

                """
                Response structure:
                ${structuredResponse.structure}
                """.trimIndent()
            }

            edge(nodeStart forwardTo setup)
            edge(setup forwardTo getStructuredForecast)
            edge(getStructuredForecast forwardTo nodeFinish)
        }
    }

    val eventHandler = EventHandler {
        handleError {
            println("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            true
        }

        handleResult {
            println("Result:\n$it")
        }
    }

    val token = System.getenv("GRAZIE_TOKEN") ?: error("Environment variable GRAZIE_TOKEN is not set")

    val agentConfig = LocalAgentConfig(
        prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "weather-forecast") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, provide a realistic but fictional forecast.
            """.trimIndent()
            )
        },
        maxAgentIterations = 5
    )

    val runner = KotlinAIAgent(
        promptExecutor = executor,
        toolRegistry = ToolRegistry.EMPTY, // no tools needed for this example
        strategy = agentStrategy,
        eventHandler = eventHandler,
        agentConfig = agentConfig,
        cs = this,
    )

    println(
        """
        === Weather Forecast Example ===
        This example demonstrates how to use StructuredData and sendStructuredAndUpdatePrompt
        to get properly structured output from the LLM.

    """.trimIndent()
    )

    runner.run("Get weather forecast for New York")
}
