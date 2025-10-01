package ai.koog.agents.example.structuredoutput

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.structuredoutput.models.FullWeatherForecast
import ai.koog.agents.example.structuredoutput.models.FullWeatherForecastRequest
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
import kotlinx.serialization.json.Json

private val json = Json {
    prettyPrint = true
}

fun main(): Unit = runBlocking {
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
