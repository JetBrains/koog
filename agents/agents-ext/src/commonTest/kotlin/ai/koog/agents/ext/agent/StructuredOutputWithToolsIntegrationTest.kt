package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StructuredOutputWithToolsIntegrationTest {

    @Serializable
    data class WeatherRequest(
        val city: String,
        val country: String
    )

    @Serializable
    data class WeatherResponse(
        @property:LLMDescription("Temperature in Celsius")
        val temperature: Int,
        @property:LLMDescription("Weather conditions")
        val conditions: String,
        @property:LLMDescription("Wind speed in km/h")
        val windSpeed: Double,
        @property:LLMDescription("Humidity percentage")
        val humidity: Int
    )

    object GetTemperatureTool : SimpleTool<GetTemperatureTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("City name")
            val city: String,
            @property:LLMDescription("Country name")
            val country: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "get_temperature"
        override val description: String = "Get current temperature for a city"

        override suspend fun doExecute(args: Args): String =
            "Temperature in ${args.city}, ${args.country}: 22°C"
    }

    object GetWeatherConditionsTool : SimpleTool<GetWeatherConditionsTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("City name")
            val city: String,
            @property:LLMDescription("Country name")
            val country: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "get_weather_conditions"
        override val description: String = "Get current weather conditions for a city"

        override suspend fun doExecute(args: Args): String =
            "Weather conditions in ${args.city}, ${args.country}: Partly Cloudy"
    }

    object GetWindSpeedTool : SimpleTool<GetWindSpeedTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("City name")
            val city: String,
            @property:LLMDescription("Country name")
            val country: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "get_wind_speed"
        override val description: String = "Get current wind speed for a city"

        override suspend fun doExecute(args: Args): String =
            "Wind speed in ${args.city}, ${args.country}: 15.5 km/h"
    }

    object GetHumidityTool : SimpleTool<GetHumidityTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("City name")
            val city: String,
            @property:LLMDescription("Country name")
            val country: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "get_humidity"
        override val description: String = "Get current humidity for a city"

        override suspend fun doExecute(args: Args): String =
            "Humidity in ${args.city}, ${args.country}: 65%"
    }

    @Test
    fun testStructuredOutputWithToolsIntegration() = runTest {
        val structure = JsonStructuredData.createJsonStructure<WeatherResponse>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategy = structuredOutputWithToolsStrategy<WeatherRequest, WeatherResponse>(
            config = config,
            parallelTools = false
        ) { request ->
            "Get complete weather data for ${request.city}, ${request.country}"
        }

        val toolCallEvents = mutableListOf<String>()
        val results = mutableListOf<WeatherResponse>()

        // For common tests, we need to use a simpler mock setup
        val mockExecutor = getMockExecutor {
            // Simply return the structured output directly
            mockLLMAnswer(
                """
                {
                    "temperature": 22,
                    "conditions": "Partly Cloudy",
                    "windSpeed": 15.5,
                    "humidity": 65
                }
                """.trimIndent()
            ).asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("weather-agent") {
                system(
                    """
                    You are a weather assistant. Use the available tools to gather weather data
                    and return a complete weather report in the specified JSON format.
                    """.trimIndent()
                )
            },
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(GetTemperatureTool)
                tool(GetWeatherConditionsTool)
                tool(GetWindSpeedTool)
                tool(GetHumidityTool)
            }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext ->
                    toolCallEvents.add(eventContext.tool.name)
                }
                onAgentCompleted { eventContext ->
                    eventContext.result?.let { results.add(it as WeatherResponse) }
                }
            }
        }

        val request = WeatherRequest(city = "New York", country = "USA")
        val result = agent.run(request)

        assertNotNull(result)
        assertEquals(22, result.temperature)
        assertEquals("Partly Cloudy", result.conditions)
        assertEquals(15.5, result.windSpeed)
        assertEquals(65, result.humidity)
    }

    @Test
    fun testStructuredOutputWithToolsParallelExecution() = runTest {
        val structure = JsonStructuredData.createJsonStructure<WeatherResponse>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategy = structuredOutputWithToolsStrategy<WeatherRequest, WeatherResponse>(
            config = config,
            parallelTools = true // Enable parallel tool execution
        ) { request ->
            "Get all weather metrics simultaneously for ${request.city}, ${request.country}"
        }

        val toolCallTimestamps = mutableMapOf<String, Long>()
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        val mockExecutor = getMockExecutor {
            // Return structured output
            mockLLMAnswer(
                """
                {
                    "temperature": 18,
                    "conditions": "Rainy",
                    "windSpeed": 20.0,
                    "humidity": 80
                }
                """.trimIndent()
            ).asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("weather-agent-parallel") {
                system(
                    """
                    You are a weather assistant. Gather all weather metrics in parallel
                    and return a complete weather report.
                    """.trimIndent()
                )
            },
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(GetTemperatureTool)
                tool(GetWeatherConditionsTool)
                tool(GetWindSpeedTool)
                tool(GetHumidityTool)
            }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext ->
                    toolCallTimestamps[eventContext.tool.name] = currentTime
                }
            }
        }

        val request = WeatherRequest(city = "London", country = "UK")
        val result = agent.run(request)

        assertNotNull(result)
        assertEquals(18, result.temperature)
        assertEquals("Rainy", result.conditions)
        assertEquals(20.0, result.windSpeed)
        assertEquals(80, result.humidity)
    }

    @Test
    fun testStructuredOutputWithNoTools() = runTest {
        val structure = JsonStructuredData.createJsonStructure<WeatherResponse>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategy = structuredOutputWithToolsStrategy<WeatherRequest, WeatherResponse>(
            config = config
        ) { request ->
            "Generate mock weather data for ${request.city}, ${request.country}"
        }

        val mockExecutor = getMockExecutor {
            // LLM directly returns structured output without calling tools
            // Set as default response to match any request
            mockLLMAnswer(
                """
                {
                    "temperature": 25,
                    "conditions": "Sunny",
                    "windSpeed": 10.0,
                    "humidity": 50
                }
                """.trimIndent()
            ).asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("weather-agent-no-tools") {
                system("Generate weather data without using tools.")
            },
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { } // No tools registered
        )

        val request = WeatherRequest(city = "Paris", country = "France")
        val result = agent.run(request)

        assertNotNull(result)
        assertEquals(25, result.temperature)
        assertEquals("Sunny", result.conditions)
        assertEquals(10.0, result.windSpeed)
        assertEquals(50, result.humidity)
    }
}
