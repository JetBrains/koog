# Structured output

## Introduction

The Structured Output API provides a way to ensure that responses from Large Language Models (LLMs) 
conform to specific data structures.
This is crucial for building reliable AI applications where you need predictable, well-formatted data rather than free-form text.

This page explains how to use this API to define data structures, generate schemas, and 
request structured responses from LLMs.

## Key components and concepts

The Structured Output API consists of several key components:

1. **Data structure definition**: Kotlin data classes annotated with kotlinx.serialization and LLM-specific annotations.
2. **JSON Schema generation**: tools to generate JSON schemas from Kotlin data classes.
3. **Structured LLM requests**: methods to request responses from LLMs that conform to the defined structures.
4. **Response handling**: processing and validating the structured responses.

## Defining data structures

The first step in using the Structured Output API is to define your data structures using Kotlin data classes.

### Basic structure

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
-->
```kotlin
@Serializable
@SerialName("WeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class WeatherForecast(
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String,
    @property:LLMDescription("Chance of precipitation in percentage")
    val precipitation: Int
)
```
<!--- KNIT example-structured-data-01.kt -->

### Key annotations

- `@Serializable`: required for kotlinx.serialization to work with the class.
- `@SerialName`: specifies the name to use during serialization.
- `@LLMDescription`: provides a description of the class for the LLM. For field annotations, use `@property:LLMDescription`.

### Supported features

The API supports a wide range of data structure features:

#### Nested classes

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
-->
```kotlin
@Serializable
@SerialName("WeatherForecast")
data class WeatherForecast(
    // Other fields
    @property:LLMDescription("Coordinates of the location")
    val latLon: LatLon
) {
    @Serializable
    @SerialName("LatLon")
    data class LatLon(
        @property:LLMDescription("Latitude of the location")
        val lat: Double,
        @property:LLMDescription("Longitude of the location")
        val lon: Double
    )
}
```
<!--- KNIT example-structured-data-02.kt -->

#### Collections (lists and maps)

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherNews(val temperature: Double)

@Serializable
data class WeatherSource(val url: Url)
-->
```kotlin
@Serializable
@SerialName("WeatherForecast")
data class WeatherForecast(
    // Other fields
    @property:LLMDescription("List of news articles")
    val news: List<WeatherNews>,
    @property:LLMDescription("Map of weather sources")
    val sources: Map<String, WeatherSource>
)
```
<!--- KNIT example-structured-data-03.kt -->

#### Enums

<!--- INCLUDE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
-->
```kotlin
@Serializable
@SerialName("Pollution")
enum class Pollution { Low, Medium, High }
```
<!--- KNIT example-structured-data-04.kt -->

#### Polymorphism with sealed classes

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
-->
```kotlin
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
}
```
<!--- KNIT example-structured-data-05.kt -->

### Providing examples

You can provide examples to help the LLM understand the expected format:

<!--- INCLUDE
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData03.WeatherNews
import ai.koog.agents.example.exampleStructuredData03.WeatherSource
import io.ktor.http.*
-->
```kotlin
val exampleForecasts = listOf(
  WeatherForecast(
    news = listOf(WeatherNews(0.0), WeatherNews(5.0)),
    sources = mutableMapOf(
      "openweathermap" to WeatherSource(Url("https://api.openweathermap.org/data/2.5/weather")),
      "googleweather" to WeatherSource(Url("https://weather.google.com"))
    )
    // Other fields
  ),
  WeatherForecast(
    news = listOf(WeatherNews(25.0), WeatherNews(35.0)),
    sources = mutableMapOf(
      "openweathermap" to WeatherSource(Url("https://api.openweathermap.org/data/2.5/weather")),
      "googleweather" to WeatherSource(Url("https://weather.google.com"))
    )
  )
)

```
<!--- KNIT example-structured-data-06.kt -->

## Requesting structured responses

There are three main layers where you can use structured output in Koog:

1. **Prompt executor layer**: Make direct LLM calls using a prompt executor
2. **Agent LLM context layer**: Use within agent sessions for conversational contexts
3. **Node layer**: Create reusable agent nodes with structured output capabilities

### Layer 1: Prompt executor

The prompt executor layer provides the most direct way to make structured LLM calls. Use the `executeStructured` method for single, standalone requests:

This method executes a prompt and ensures the response is properly structured by:

- Automatically selecting the best structured output approach based on [model capabilities](./model-capabilities.md)
- Injecting structured output instructions into the original prompt when needed
- Using native structured output support when available
- Providing automatic error correction through an auxiliary LLM when parsing fails

Here is an example of using the `executeStructured` method:

<!--- INCLUDE
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData06.exampleForecasts
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.structure.executeStructured
import ai.koog.prompt.structure.StructureFixingParser
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Define a simple, single-provider prompt executor
val promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_KEY"))

// Make an LLM call that returns a structured response
val structuredResponse = promptExecutor.executeStructured<WeatherForecast>(
        // Define the prompt (both system and user messages)
        prompt = prompt("structured-data") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, provide a realistic but fictional forecast.
                """.trimIndent()
            )
            user(
              "What is the weather forecast for Amsterdam?"
            )
        },
        // Define the main model that will execute the request
        model = OpenAIModels.Chat.GPT4oMini,
        // Optional: provide examples to help the model understand the format
        examples = exampleForecasts,
        // Optional: provide a fixing parser for error correction
        fixingParser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4o,
            retries = 3
        )
    )
```
<!--- KNIT example-structured-data-07.kt -->

The `executeStructured` method takes the following arguments:

| Name           | Data type              | Required | Default       | Description                                                                                                     |
|----------------|------------------------|----------|---------------|-----------------------------------------------------------------------------------------------------------------|
| `prompt`       | Prompt                 | Yes      |               | The prompt to execute. For more information, see [Prompt API](prompt-api.md).                                   |
| `model`        | LLModel                | Yes      |               | The main model to execute the prompt.                                                                           |
| `examples`     | List<T>                | No       | `emptyList()` | Optional list of examples to help the model understand the expected format.                                     |
| `fixingParser` | StructureFixingParser? | No       | `null`        | Optional parser that handles malformed responses by using an auxiliary LLM to intelligently fix parsing errors. |

The method returns a `Result<StructuredResponse<T>>` containing either the successfully parsed structured data or an error.

### Layer 2: Agent LLM context

The agent LLM context layer allows you to request structured responses within agent sessions. This is useful for building conversational agents that need structured data at specific points in their flow.

Use the `requestLLMStructured` method within a `writeSession` for agent-based interactions:


<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData06.exampleForecasts
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructureFixingParser

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val structuredResponse = llm.writeSession {
    requestLLMStructured<WeatherForecast>(
        examples = exampleForecasts,
        fixingParser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4o,
            retries = 3
        )
    )
}
```
<!--- KNIT example-structured-data-08.kt -->

The `fixingParser` parameter specifies a configuration for handling malformed responses through auxiliary LLM processing during retries. This helps ensure that you always get a valid response.

#### Integrating with agent strategies

You can integrate structured data processing into your agent strategies:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
-->
```kotlin
val agentStrategy = strategy("weather-forecast") {
    val setup by nodeLLMRequest()

    val getStructuredForecast by node<Message.Response, String> { _ ->
        val structuredResponse = llm.writeSession {
            requestLLMStructured<WeatherForecast>(
                fixingParser = StructureFixingParser(
                    model = OpenAIModels.Chat.GPT4o,
                    retries = 3
                )
            )
        }

        """
        Response structure:
        $structuredResponse
        """.trimIndent()
    }

    edge(nodeStart forwardTo setup)
    edge(setup forwardTo getStructuredForecast)
    edge(getStructuredForecast forwardTo nodeFinish)
}
```
<!--- KNIT example-structured-data-09.kt -->

### Layer 3: Node layer

The node layer provides the highest level of abstraction for structured output in agent workflows. Use `nodeLLMRequestStructured` to create reusable agent nodes that handle structured data.

This creates an agent node that:
- Accepts a `String` input (user message)
- Appends the message to the LLM prompt
- Requests structured output from the LLM
- Returns `Result<StructuredResponse<MyStruct>>`

#### Node layer example

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData06.exampleForecasts
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.StructureFixingParser
-->
```kotlin
val agentStrategy = strategy("weather-forecast") {
    val setup by node<Unit, String> { _ ->
        "Please provide a weather forecast for Amsterdam"
    }
    
    // Create a structured output node using delegate syntax
    val getWeatherForecast by nodeLLMRequestStructured<WeatherForecast>(
        name = "forecast-node",
        examples = exampleForecasts,
        fixingParser = StructureFixingParser(
            model = OpenAIModels.Chat.GPT4o,
            retries = 3
        )
    )
    
    val processResult by node<Result<StructuredResponse<WeatherForecast>>, String> { result ->
        when {
            result.isSuccess -> {
                val forecast = result.getOrNull()?.data
                "Weather forecast: $forecast"
            }
            result.isFailure -> {
                "Failed to get structured forecast: ${result.exceptionOrNull()?.message}"
            }
            else -> "Unknown result state"
        }
    }

    edge(nodeStart forwardTo setup)
    edge(setup forwardTo getWeatherForecast)
    edge(getWeatherForecast forwardTo processResult)
    edge(processResult forwardTo nodeFinish)
}
```
<!--- KNIT example-structured-data-10.kt -->

#### Full code sample

Here is a full example of using the Structured Output API:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
-->
```kotlin
// Note: Import statements are omitted for brevity
@Serializable
@SerialName("SimpleWeatherForecast")
@LLMDescription("Simple weather forecast for a location")
data class SimpleWeatherForecast(
    @property:LLMDescription("Location name")
    val location: String,
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String
)

val token = System.getenv("OPENAI_KEY") ?: error("Environment variable OPENAI_KEY is not set")

fun main(): Unit = runBlocking {
    // Create sample forecasts
    val exampleForecasts = listOf(
        SimpleWeatherForecast(
            location = "New York",
            temperature = 25,
            conditions = "Sunny"
        ),
        SimpleWeatherForecast(
            location = "London",
            temperature = 18,
            conditions = "Cloudy"
        )
    )

    // Generate JSON Schema
    val forecastStructure = JsonStructure.create<SimpleWeatherForecast>(
        schemaGenerator = BasicJsonSchemaGenerator.Default,
        examples = exampleForecasts
    )

    // Define the agent strategy
    val agentStrategy = strategy("weather-forecast") {
        val setup by nodeLLMRequest()
  
        val getStructuredForecast by node<Message.Response, String> { _ ->
            val structuredResponse = llm.writeSession {
                requestLLMStructured<SimpleWeatherForecast>()
            }
  
            """
            Response structure:
            $structuredResponse
            """.trimIndent()
        }
  
        edge(nodeStart forwardTo setup)
        edge(setup forwardTo getStructuredForecast)
        edge(getStructuredForecast forwardTo nodeFinish)
    }


    // Configure and run the agent
    val agentConfig = AIAgentConfig(
        prompt = prompt("weather-forecast-prompt") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, provide a realistic but fictional forecast.
                """.trimIndent()
            )
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 5
    )

    val runner = AIAgent(
        promptExecutor = simpleOpenAIExecutor(token),
        toolRegistry = ToolRegistry.EMPTY,
        strategy = agentStrategy,
        agentConfig = agentConfig
    )

    runner.run("Get weather forecast for Paris")
}
```
<!--- KNIT example-structured-data-11.kt -->

## Advanced usage

The examples above demonstrate the simplified API that automatically selects the best structured output approach based on model capabilities. 
For more control over the structured output process, you can use the advanced API with manual schema creation and provider-specific configurations.

### Manual schema creation and configuration

Instead of relying on automatic schema generation, you can create schemas explicitly using `JsonStructure.create` and configure structured output behavior manually via the `StructuredOutput` class.

The key difference is that instead of passing simple parameters like `examples` and `fixingParser`, you create a `StructuredRequestConfig` object that allows fine-grained control over:

- **Schema generation**: Choose specific generators (Standard, Basic, or Provider-specific)
- **Output modes**: Native structured output support vs Manual prompting
- **Provider mapping**: Different configurations for different LLM providers
- **Fallback strategies**: Default behavior when provider-specific config is unavailable

<!--- INCLUDE
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData06.exampleForecasts
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.structure.executeStructured
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import ai.koog.prompt.structure.StructuredRequestConfig

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Create different schema structures with different generators
val genericStructure = JsonStructure.create<WeatherForecast>(
    schemaGenerator = StandardJsonSchemaGenerator,
    examples = exampleForecasts
)

val openAiStructure = JsonStructure.create<WeatherForecast>(
    schemaGenerator = OpenAIBasicJsonSchemaGenerator,
    examples = exampleForecasts
)

val promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_KEY"))

// The advanced API uses StructuredRequestConfig instead of simple parameters
val structuredResponse = promptExecutor.executeStructured(
    prompt = prompt("structured-data") {
        system("You are a weather forecasting assistant.")
        user("What is the weather forecast for Amsterdam?")
    },
    model = OpenAIModels.Chat.GPT4oMini,
    config = StructuredRequestConfig(
        byProvider = mapOf(
            LLMProvider.OpenAI to StructuredRequest.Native(openAiStructure),
        ),
        default = StructuredRequest.Manual(genericStructure),
        fixingParser = StructureFixingParser(
            model = AnthropicModels.Haiku_3_5,
            retries = 2
        )
    )
)
```
<!--- KNIT example-structured-data-12.kt -->

### Schema generators

Different schema generators are available depending on your needs:

- **StandardJsonSchemaGenerator**: Full JSON Schema with support for polymorphism, definitions, and recursive references
- **BasicJsonSchemaGenerator**: Simplified schema without polymorphism support, compatible with more models  
- **Provider-specific generators**: Optimized schemas for specific LLM providers (OpenAI, Google, etc.)

### Usage across all layers

The advanced configuration works consistently across all three layers of the API. The method names remain the same, only the parameter changes from simple arguments to the more advanced `StructuredOutputConfig`:

- **Prompt executor**: `executeStructured(prompt, model, config: StructuredRequestConfig<T>)`
- **Agent LLM context**: `requestLLMStructured(config: StructuredRequestConfig<T>)`
- **Node layer**: `nodeLLMRequestStructured(config: StructuredRequestConfig<T>)`

The simplified API (using just `examples` and `fixingParser` parameters) is recommended for most use cases, while the advanced API provides additional control when needed.

## Best practices

1. **Use clear descriptions**: provide clear and detailed descriptions using `@LLMDescription` annotations to help the LLM understand the expected data.

2. **Provide examples**: include examples of valid data structures to guide the LLM.

3. **Handle errors gracefully**: implement proper error handling to deal with cases where the LLM might not produce a valid structure.

4. **Use appropriate schema types**: select the appropriate schema format and type based on your needs and the capabilities of the LLM you are using.

5. **Test with different models**: different LLMs may have varying abilities to follow structured formats, so test with multiple models if possible.

6. **Start simple**: begin with simple structures and gradually add complexity as needed.

7. **Use polymorphism Carefully**: while the API supports polymorphism with sealed classes, be aware that it can be more challenging for LLMs to handle correctly.
