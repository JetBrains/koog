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
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.text.text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This is example how to use the simple structured output API. Unlike the full (advanced) version, it does not require
 * specifying struct definitions and structured output modes manually. It attempts to find the best approach to provide
 * a structured output based on the defined LLM capabilities.
 *
 * Check advanced examples for more details on how you can control this process.
 *
 * This example uses the models supporting full JSON schema capability, meaning they understand natively a subset of
 * JSON schema specification when asked to provide a structured response.
 * That's why more complex structure features are demonstrated, such as polymorphism.
 */

@Serializable
@SerialName("WeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class WeatherForecast(
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

data class WeatherForecastRequest(
    val city: String,
    val country: String
)

private val json = Json {
    prettyPrint = true
}

suspend fun main() {
    val openAIClient = OpenAILLMClient(ApiKeyService.openAIApiKey)
    val anthropicClient = AnthropicLLMClient(ApiKeyService.anthropicApiKey)
    val googleClient = GoogleLLMClient(ApiKeyService.googleApiKey)

    val agentStrategy = strategy<WeatherForecastRequest, WeatherForecast>("weather-forecast") {
        val prepareRequest by node<WeatherForecastRequest, String> { request ->
            text {
                +"Requesting forecast for"
                +"City: ${request.city}"
                +"Country: ${request.country}"
            }
        }

        /*
     Simple API, let it figure out the optimal approach to get structured output itself.
     So only the structure has to be supplied.
         */
        val getStructuredForecast by nodeLLMRequestStructured<WeatherForecast>(
            /*
         Optional: If the model you are using does not support native structured output, you can provide examples to help
         it better understand the format.
             */
            examples = listOf(),
            /*
         Optional: If the model provides inaccurate structure leading to serialization exceptions, you can try fixing
         parser to attempt to fix malformed output.
             */
            fixingParser = StructureFixingParser(
                fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                retries = 2
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
        // model = OpenAIModels.Chat.GPT4o,
        // model = AnthropicModels.Sonnet_3_7,
        maxAgentIterations = 5
    )

    MultiLLMPromptExecutor(
        LLMProvider.OpenAI to openAIClient,
        LLMProvider.Anthropic to anthropicClient,
        LLMProvider.Google to googleClient,
    ).use { executor ->
        val agent = AIAgent<WeatherForecastRequest, WeatherForecast>(
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

        val result: WeatherForecast = agent.run(WeatherForecastRequest(city = "New York", country = "USA"))
        println("Agent run result: $result")
    }
}
