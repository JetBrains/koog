# Module prompt-model

A core module that defines data models and parameters for controlling language model behavior.

### Overview

The prompt-model module provides essential data structures for configuring and controlling language model interactions. It includes the `LLMParams` class which encapsulates parameters like temperature, speculation, schema, and tool choice options. The module also defines structured data schemas and tool choice behaviors that can be used to customize language model responses.

### Example of usage

```kotlin
// Create parameters for a deterministic response
val deterministicParams = LLMParams(
    temperature = 0.0
)

// Create parameters with a schema for structured output
val structuredParams = LLMParams(
    temperature = 0.7,
    schema = LLMParams.Schema.JSON.Simple(
        name = "PersonInfo",
        schema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "age" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                "skills" to JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                ))
            ))
        ))
    )
)

// Configure the model to use a specific tool
val toolParams = LLMParams(
    toolChoice = LLMParams.ToolChoice.Named("calculator")
)

// Configure the model to not use any tools
val noToolsParams = LLMParams(
    toolChoice = LLMParams.ToolChoice.None
)
```
