package ai.grazie.code.agents.example.structureddata

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.annotations.LLMDescription
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.nodeLLMSendStageInput
import ai.grazie.code.prompt.structure.json.JsonSchemaGenerator
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider
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
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String,
    @property:LLMDescription("Chance of precipitation in percentage")
    val precipitation: Int,
    @property:LLMDescription("Coordinates of the location")
    val latLon: LatLon,
    val pollution: Pollution,
    val alert: WeatherAlert,
    // lists
    @property:LLMDescription("List of news articles")
    val news: List<WeatherNews>,
    // maps
    @property:LLMDescription("Map of weather sources")
    val sources: Map<String, WeatherSource>
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

    // ... and maps (but only with string keys)
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
            @property:LLMDescription("Wind speed in km/h")
            val windSpeed: Double // km/h
        ) : WeatherAlert()

        @Serializable
        @SerialName("FloodAlert")
        data class FloodAlert(
            override val severity: Severity,
            override val message: String,
            @property:LLMDescription("Expected rainfall in mm")
            val expectedRainfall: Double // mm
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

fun main(): Unit = runBlocking {

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
                        fixingModel = OpenAIModels.GPT4oMini,
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

    val agentConfig = LocalAgentConfig(
        prompt = prompt(AnthropicModels.Sonnet_3_7, "weather-forecast") {
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
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAIDirectLLMClient(TokenService.openAIToken),
            LLMProvider.Anthropic to AnthropicDirectLLMClient(TokenService.anthropicToken),
        ),
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
