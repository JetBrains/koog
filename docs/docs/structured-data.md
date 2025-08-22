# Structured data processing

## Introduction

The Structured Data Processing API provides a way to ensure that responses from Large Language Models (LLMs) 
conform to specific data structures.
This is crucial for building reliable AI applications where you need predictable, well-formatted data rather than free-form text.

This page explains how to use the Structured Data Processing API to define data structures, generate schemas, and 
request structured responses from LLMs.

## Key components and concepts

The Structured Data Processing API consists of several key components:

1. **Data structure definition**: Kotlin data classes annotated with kotlinx.serialization and LLM-specific annotations.
2. **JSON Schema generation**: tools to generate JSON schemas from Kotlin data classes.
3. **Structured LLM requests**: methods to request responses from LLMs that conform to the defined structures.
4. **Response handling**: processing and validating the structured responses.

## Defining data structures

The first step in using the Structured Data Processing API is to define your data structures using Kotlin data classes.

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

## Generating JSON schemas

Once you have defined your data structures, you can generate JSON schemas from them using the `JsonStructuredData` class:

<!--- INCLUDE
import ai.koog.agents.example.exampleStructuredData03.WeatherForecast
import ai.koog.agents.example.exampleStructuredData07.exampleForecasts
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.JsonStructuredData
-->
```kotlin
val weatherForecastStructure = JsonStructuredData.createJsonStructure<WeatherForecast>(
    schemaGenerator = BasicJsonSchemaGenerator.Default,
    examples = exampleForecasts
)
```
<!--- KNIT example-structured-data-06.kt -->

### Schema format options

- `JsonSchema`: standard JSON Schema format.
- `Simple`: a simplified schema format that may work better with some models but has limitations such as no 
polymorphism support.

### Schema type options

The following schema types are supported

* `SIMPLE`: a simplified schema type:
    - Supports only standard JSON fields
    - Does not support definitions, URL references, and recursive checks
    - **Does not support polymorphism**
    - Supported by a larger number of language models
    - Used for simpler data structures

* `FULL`: a more comprehensive schema type:
    - Supports advanced JSON Schema capabilities, including definitions, URL references, and recursive checks
    - **Supports polymorphism**: can work with sealed classes or interfaces and their implementations
    - Supported by fewer language models
    - Used for complex data structures with inheritance hierarchies

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
<!--- KNIT example-structured-data-07.kt -->

## Requesting structured responses

There are two ways to request structured responses in Koog:

- Make a single LLM call using a prompt executor and its `executeStructured` or `executeStructuredOneShot` methods.
- Create structured output requests for agent use cases and integration into agent strategies. 

### Using a prompt executor

To make a single LLM call that returns a structured output, use a prompt executor and its `executeStructured` method.
This method executes a prompt and ensures the response is properly structured by applying automatic output coercion. The
 method enhances structured output parsing reliability by:

- Injecting structured output instructions into the original prompt.
- Executing the enriched prompt to receive a raw response.
- Using a separate LLM call to parse or coerce the response if direct parsing fails.

Unlike `[execute(prompt, structure)]` which simply attempts to parse the raw response and fails if the format does not
match exactly, this method actively works to transform unstructured or malformed outputs into the expected structure
through additional LLM processing.

Here is an example of using the `executeStructured` method:

<!--- INCLUDE
import ai.koog.agents.example.exampleStructuredData06.weatherForecastStructure
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.structure.executeStructured
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
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
val structuredResponse = promptExecutor.executeStructured(
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
        model = OpenAIModels.CostOptimized.GPT4oMini,
        // Provide the structured data configuration
        config = StructuredOutputConfig(
            default = StructuredOutput.Manual(weatherForecastStructure),
            fixingParser = StructureFixingParser(
                fixingModel = OpenAIModels.Chat.GPT4o,
                retries = 3
            )
        )
    )
```
<!--- KNIT example-structured-data-08.kt -->

The example relies on an already [generated JSON schema](#generating-json-schemas) named `weatherForecastStructure` that is based on a [defined data structure](#defining-data-structures) and [examples](#providing-examples).

The `executeStructured` method takes the following arguments:

| Name          | Data type      | Required | Default                   | Description                                                                                                                                    |
|---------------|----------------|----------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `prompt`      | Prompt         | Yes      |                           | The prompt to execute. For more information, see [Prompt API](prompt-api.md).                                                                  |
| `structure`   | StructuredData | Yes      |                           | The structured data definition with schema and parsing logic. For more information, see [Defining data structures](#defining-data-structures). |
| `mainModel`   | LLModel        | Yes      |                           | The main model to execute the prompt.                                                                                                          |
| `retries`     | Integer        | No       | `1`                       | The number of attempts to parse the response into a proper structured output.                                                                  |
| `fixingModel` | LLModel        | No       | `OpenAIModels.Chat.GPT4o` | The model that handles output coercion - transformation of malformed outputs into the expected structure.                                      |

In addition to `executeStructured`, you can also use the `executeStructuredOneShot` method with a prompt executor. The 
main difference is that `executeStructuredOneShot` does not handle coercion automatically, so you would have to manually
transform malformed outputs into proper structured ones.

The `executeStructuredOneShot` method takes the following arguments:

| Name        | Data type      | Required | Default | Description                                                   |
|-------------|----------------|----------|---------|---------------------------------------------------------------|
| `prompt`    | Prompt         | Yes      |         | The prompt to execute.                                        |
| `structure` | StructuredData | Yes      |         | The structured data definition with schema and parsing logic. |
| `model`     | LLModel        | Yes      |         | The model to execute the prompt.                              |

### Structured data responses for agent use cases

To request a structured response from an LLM, use the `requestLLMStructured` method within a `writeSession`:


<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.example.exampleStructuredData06.weatherForecastStructure
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
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
    this.requestLLMStructured(
        config = StructuredOutputConfig(
            default = StructuredOutput.Manual(weatherForecastStructure),
            fixingParser = StructureFixingParser(
                fixingModel = OpenAIModels.Chat.GPT4o,
                retries = 3
            )
        )
    )
}
```
<!--- KNIT example-structured-data-09.kt -->

The `fixingModel` parameter specifies the language model to use for reparsing or error correction during retries. This helps ensure that you always get a valid response.

#### Integrating with agent strategies

You can integrate structured data processing into your agent strategies:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.example.exampleStructuredData06.weatherForecastStructure
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.StructureFixingParser
-->
```kotlin
val agentStrategy = strategy("weather-forecast") {
    val setup by nodeLLMRequest()

    val getStructuredForecast by node<Message.Response, String> { _ ->
        val structuredResponse = llm.writeSession {
            this.requestLLMStructured(
                config = StructuredOutputConfig(
                    default = StructuredOutput.Manual(weatherForecastStructure),
                    fixingParser = StructureFixingParser(
                        fixingModel = OpenAIModels.Chat.GPT4o,
                        retries = 3
                    )
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
<!--- KNIT example-structured-data-10.kt -->

#### Full code sample

Here is a full example of using the Structured Data Processing API:

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
import ai.koog.prompt.structure.json.JsonStructuredData
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
    val forecastStructure = JsonStructuredData.createJsonStructure<SimpleWeatherForecast>(
        schemaGenerator = BasicJsonSchemaGenerator.Default,
        examples = exampleForecasts
    )

    // Define the agent strategy
    val agentStrategy = strategy("weather-forecast") {
        val setup by nodeLLMRequest()
  
        val getStructuredForecast by node<Message.Response, String> { _ ->
            val structuredResponse = llm.writeSession {
                this.requestLLMStructured<SimpleWeatherForecast>()
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

## Best practices

1. **Use clear descriptions**: provide clear and detailed descriptions using `@LLMDescription` annotations to help the LLM understand the expected data.

2. **Provide examples**: include examples of valid data structures to guide the LLM.

3. **Handle errors gracefully**: implement proper error handling to deal with cases where the LLM might not produce a valid structure.

4. **Use appropriate schema types**: select the appropriate schema format and type based on your needs and the capabilities of the LLM you are using.

5. **Test with different models**: different LLMs may have varying abilities to follow structured formats, so test with multiple models if possible.

6. **Start simple**: begin with simple structures and gradually add complexity as needed.

7. **Use polymorphism Carefully**: while the API supports polymorphism with sealed classes, be aware that it can be more challenging for LLMs to handle correctly.
