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

### Key annotations

- `@Serializable`: required for kotlinx.serialization to work with the class.
- `@SerialName`: specifies the name to use during serialization.
- `@LLMDescription`: provides a description of the class for the LLM. For field annotations, use `@property:LLMDescription`.

### Supported features

The API supports a wide range of data structure features:

#### Nested classes

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

#### Collections (lists and maps)

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

#### Enums

```kotlin
@Serializable
@SerialName("Pollution")
enum class Pollution { Low, Medium, High }
```

#### Polymorphism with sealed classes

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

## Generating JSON schemas

Once you have defined your data structures, you can generate JSON schemas from them using the `JsonStructuredData` class:

```kotlin
val weatherForecastStructure = JsonStructuredData.createJsonStructure<WeatherForecast>(
    schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
    examples = exampleForecasts,
    schemaType = JsonStructuredData.JsonSchemaType.SIMPLE
)
```

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

```kotlin
val exampleForecasts = listOf(
    WeatherForecast(
        temperature = 25,
        conditions = "Sunny",
        precipitation = 0,
        // Other fields
    ),
    WeatherForecast(
        temperature = 18,
        conditions = "Cloudy",
        precipitation = 30,
        // Other fields
    )
)
```

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
        // Provide the expected data structure to the LLM
        structure = weatherForecastStructure,
        // Define the main model that will execute the request
        mainModel = OpenAIModels.CostOptimized.GPT4oMini,
        // Set the maximum number of retries to get a proper structured response
        retries = 5,
        // Set the LLM used for output coercion (transformation of malformed outputs)
        fixingModel = OpenAIModels.Chat.GPT4o
    )
```

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

```kotlin
val structuredResponse = llm.writeSession {
    this.requestLLMStructured(
        structure = weatherForecastStructure,
        fixingModel = OpenAIModels.Chat.GPT4o,
    )
}
```

The `fixingModel` parameter specifies the language model to use for reparsing or error correction during retries. This helps ensure that you always get a valid response.

#### Integrating with agent strategies

You can integrate structured data processing into your agent strategies:

```kotlin
val agentStrategy = strategy("weather-forecast") {
    val setup by nodeLLMRequest()

    val getStructuredForecast by node<Message.Response, String> { _ ->
        val structuredResponse = llm.writeSession {
            this.requestLLMStructured(
                structure = forecastStructure,
                fixingModel = OpenAIModels.Chat.GPT4o,
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

#### Full code sample

Here is a full example of using the Structured Data Processing API:

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
        schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
        examples = exampleForecasts,
        schemaType = JsonStructuredData.JsonSchemaType.SIMPLE
    )

    // Define the agent strategy
    val agentStrategy = strategy("weather-forecast") {
        val setup by nodeLLMRequest()
  
        val getStructuredForecast by node<Message.Response, String> { _ ->
            val structuredResponse = llm.writeSession {
                this.requestLLMStructured(
                    structure = forecastStructure,
                    fixingModel = OpenAIModels.Chat.GPT4o,
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

## Best practices

1. **Use clear descriptions**: provide clear and detailed descriptions using `@LLMDescription` annotations to help the LLM understand the expected data.

2. **Provide examples**: include examples of valid data structures to guide the LLM.

3. **Handle errors gracefully**: implement proper error handling to deal with cases where the LLM might not produce a valid structure.

4. **Use appropriate schema types**: select the appropriate schema format and type based on your needs and the capabilities of the LLM you are using.

5. **Test with different models**: different LLMs may have varying abilities to follow structured formats, so test with multiple models if possible.

6. **Start simple**: begin with simple structures and gradually add complexity as needed.

7. **Use polymorphism Carefully**: while the API supports polymorphism with sealed classes, be aware that it can be more challenging for LLMs to handle correctly.
