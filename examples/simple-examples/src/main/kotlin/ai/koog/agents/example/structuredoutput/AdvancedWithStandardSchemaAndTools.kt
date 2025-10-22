package ai.koog.agents.example.structuredoutput

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.structuredoutput.models.FullWeatherForecast
import ai.koog.agents.example.structuredoutput.models.FullWeatherForecastRequest
import ai.koog.agents.example.structuredoutput.tools.WeatherTools
import ai.koog.agents.ext.agent.structuredOutputWithToolsStrategy
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.structure.GoogleStandardJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIStandardJsonSchemaGenerator
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.prompt.text.text
import kotlinx.serialization.json.Json

private val json = Json {
    prettyPrint = true
}

suspend fun main() {
    /*
     This structure has a generic schema that is suitable for manual structured output mode.
     But to use native structured output support in different LLM providers you might need to use custom JSON schema generators
     that would produce the schema these providers expect.
     */
    val genericWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        // Some models might not work well with json schema, so you may try simple, but it has more limitations (no polymorphism!)
        schemaGenerator = StandardJsonSchemaGenerator,
        examples = FullWeatherForecast.exampleForecasts,
    )

    println("Generated generic JSON schema:\n${json.encodeToString(genericWeatherStructure.schema.schema)}")
    /*
     These are specific structure definitions with schemas in format that particular LLM providers understand in their native
     structured output.
     */

    val openAiWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        schemaGenerator = OpenAIStandardJsonSchemaGenerator,
        examples = FullWeatherForecast.exampleForecasts,
    )

    val googleWeatherStructure = JsonStructuredData.createJsonStructure<FullWeatherForecast>(
        schemaGenerator = GoogleStandardJsonSchemaGenerator,
        examples = FullWeatherForecast.exampleForecasts,
    )

    val config = StructuredOutputConfig(
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

    val agentStrategy = structuredOutputWithToolsStrategy<FullWeatherForecastRequest, FullWeatherForecast>(
        config
    ) { request ->
        text {
            +"Requesting forecast for"
            +"City: ${request.city}"
            +"Country: ${request.country}"
        }
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("weather-forecast-with-tools") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, use the weather tools to get the weather forecast for the specified city and country.
                """.trimIndent()
            )
        },
        model = OpenAIModels.Chat.GPT4_1,
        maxAgentIterations = 10
    )

    MultiLLMPromptExecutor(
        LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
        LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey),
        LLMProvider.Google to GoogleLLMClient(ApiKeyService.googleApiKey),
    ).use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tools(WeatherTools().asTools())
            }
        ) {
            handleEvents {
                onAgentExecutionFailed { eventContext ->
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
}
