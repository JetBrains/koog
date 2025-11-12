@file:OptIn(ExperimentalUuidApi::class)

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
import ai.koog.prompt.executor.clients.google.structure.GoogleBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.text.text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi

/**
 * This is a more advanced example showing how to configure various parameters of structured output manually, to fine-tune
 * it for your needs when necessary.
 *
 * Structured output that uses "simple" JSON schema.
 * Basic structure support.
 */

@Serializable
@SerialName("SimpleWeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class SimpleWeatherForecast(
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
    @property:LLMDescription("Pollution level")
    val pollution: Pollution,
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
}

data class SimpleWeatherForecastRequest(
    val city: String,
    val country: String
)

private val json = Json {
    prettyPrint = true
}

suspend fun main() {
    val exampleForecasts = listOf(
        SimpleWeatherForecast(
            temperature = 18,
            conditions = "Cloudy",
            precipitation = 30,
            latLon = SimpleWeatherForecast.LatLon(lat = 34.0522, lon = -118.2437),
            pollution = SimpleWeatherForecast.Pollution.Medium,
            news = listOf(
                SimpleWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                SimpleWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
//            sources = mapOf(
//                "MeteorologicalWatch" to SimpleWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch",
//                    stationAuthority = "US Department of Agriculture"
//                ),
//                "MeteorologicalWatch2" to SimpleWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch2",
//                    stationAuthority = "US Department of Agriculture"
//                )
//            )
        ),
        SimpleWeatherForecast(
            temperature = 10,
            conditions = "Rainy",
            precipitation = null,
            latLon = SimpleWeatherForecast.LatLon(lat = 37.7739, lon = -122.4194),
            pollution = SimpleWeatherForecast.Pollution.Low,
            news = listOf(
                SimpleWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                SimpleWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
            ),
//            sources = mapOf(
//                "MeteorologicalWatch" to SimpleWeatherForecast.WeatherSource(
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
    val genericWeatherStructure = JsonStructure.create<SimpleWeatherForecast>(
        // Some models might not work well with json schema, so you may try simple, but it has more limitations (no polymorphism!)
        schemaGenerator = BasicJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    /*
 These are specific structure definitions with schemas in format that particular LLM providers understand in their native
 structured output.
     */

    val openAiWeatherStructure = JsonStructure.create<SimpleWeatherForecast>(
        schemaGenerator = OpenAIBasicJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    val googleWeatherStructure = JsonStructure.create<SimpleWeatherForecast>(
        schemaGenerator = GoogleBasicJsonSchemaGenerator,
        examples = exampleForecasts,
    )

    val agentStrategy =
        strategy<SimpleWeatherForecastRequest, SimpleWeatherForecast>("advanced-simple-weather-forecast") {
            val prepareRequest by node<SimpleWeatherForecastRequest, String> { request ->
                text {
                    +"Requesting forecast for"
                    +"City: ${request.city}"
                    +"Country: ${request.country}"
                }
            }

            @Suppress("DuplicatedCode")
            val getStructuredForecast by nodeLLMRequestStructured(
                config = StructuredRequestConfig(
                    byProvider = mapOf(
                        // Native modes leveraging native structured output support in models, with custom definitions for LLM providers that might have different format.
                        LLMProvider.OpenAI to StructuredRequest.Native(openAiWeatherStructure),
                        LLMProvider.Google to StructuredRequest.Native(googleWeatherStructure),
                        // Anthropic does not support native structured output yet.
                        LLMProvider.Anthropic to StructuredRequest.Manual(genericWeatherStructure),
                    ),

                    // Fallback manual structured output mode, via explicit prompting with additional message, not native model support
                    default = StructuredRequest.Manual(genericWeatherStructure),

                    // Helper parser to attempt a fix if a malformed output is produced.
                    fixingParser = StructureFixingParser(
                        model = AnthropicModels.Haiku_3_5,
                        retries = 2,
                    ),
                )
            )

            nodeStart then prepareRequest then getStructuredForecast
            edge(getStructuredForecast forwardTo nodeFinish transformed { it.getOrThrow().data })
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

    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
        LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey),
        LLMProvider.Google to GoogleLLMClient(ApiKeyService.googleApiKey),
    )

    promptExecutor.use { executor ->
        val agent = AIAgent<SimpleWeatherForecastRequest, SimpleWeatherForecast>(
            promptExecutor = executor,
            strategy = agentStrategy, // no tools needed for this example
            agentConfig = agentConfig
        ) {
            handleEvents {
                onAgentExecutionFailed { ctx ->
                    println("An error occurred: ${ctx.throwable.message}\n${ctx.throwable.stackTraceToString()}")
                }
            }
        }

        println(
            """
            === Simple Weather Forecast Example ===
            This example demonstrates how to use structured output with simple schema support
            to get properly structured output from the LLM.
            """.trimIndent()
        )

        val result: SimpleWeatherForecast = agent.run(SimpleWeatherForecastRequest(city = "New York", country = "USA"))
        println("Agent run result: $result")
    }
}
