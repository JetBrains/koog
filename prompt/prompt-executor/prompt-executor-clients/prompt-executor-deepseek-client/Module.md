# Module prompt-executor-deepseek-client

A client implementation for executing prompts using DeepSeek's GPT models with support for custom parameters.

### Overview

This module provides a client implementation for the DeepSeek API, allowing you to execute prompts using DeepSeek models with full control over generation parameters, reasoning, and structured outputs.

### Supported Models

| Name               | Speed  | Price       | Input       | Output      |
|--------------------|--------|-------------|-------------|-------------|
| [DeepSeekChat]     | Fast   | $0.27-$1.1  | Text, Tools | Text, Tools |
| [DeepSeekReasoner] | Medium | $0.55-$2.19 | Text, Tools | Text, Tools |


### Model-Specific Parameters Support

The client supports DeepSeek-specific parameters through `DeepSeekParams` class:

```kotlin
val deepSeekParams = DeepSeekParams(
    temperature = 0.7,
    maxTokens = 1000,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    topK = 40,
    stop = listOf("\n", "END"),
    logprobs = true,
    topLogprobs = 5,
    includeThoughts = true,
    thinkingBudget = 2000
)
```

**Key Parameters:**
- **temperature** (0.0-2.0): Controls randomness in generation
- **topP** (0.0-1.0): Nucleus sampling parameter
- **frequencyPenalty** (-2.0-2.0): Reduces repetition of frequent tokens
- **presencePenalty** (-2.0-2.0): Encourages new topics/tokens
- **includeThoughts**: Request model reasoning traces (reasoning models)
- **thinkingBudget**: Token limit for internal reasoning
- **logprobs**: Include log probabilities for generated tokens
- **stop**: Stop sequences to halt generation

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-deepseek-client:$version")
}
```

Configure the client with your API key:

```kotlin
val deepseekClient = DeepSeekLLMClient(
    apiKey = "your-deepseek-api-key",
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = DeepSeekLLMClient(
        apiKey = System.getenv("DEEPSEEK_API_KEY"),
    )

    // Basic example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = DeepSeekModels.DeepSeekChat,
    )

    // Advanced example with custom parameters
    val advancedResponse = client.execute(
        prompt = prompt {
            system("You are a helpful coding assistant")
            user("Write a Python function to calculate factorial")
        },
        model = DeepSeekModels.DeepSeekReasoner,
        params = DeepSeekParams(
            temperature = 0.3,
            maxTokens = 2000,
            includeThoughts = true,
            thinkingBudget = 1000,
            frequencyPenalty = 0.1,
            topP = 0.95,
            stop = listOf("```\n\n")
        )
    )

    println(response)
    println(advancedResponse)
}
```

### Additional Examples

```kotlin
// Structured output with JSON schema
val structuredResponse = client.execute(
    prompt = prompt {
        system("Extract key information as JSON")
        user("John Doe, age 30, works as software engineer at TechCorp")
    },
    model = DeepSeekModels.DeepSeekChat,
    params = DeepSeekParams(
        temperature = 0.1,
        schema = jsonSchema {
            object {
                property("name", string())
                property("age", integer())
                property("occupation", string())
                property("company", string())
            }
        }
    )
)

// Reasoning with thinking budget
val reasoningResponse = client.execute(
    prompt = prompt {
        system("Solve this step by step")
        user("If a train travels 120 km in 2 hours, what's its average speed?")
    },
    model = DeepSeekModels.DeepSeekReasoner,
    params = DeepSeekParams(
        temperature = 0.5,
        includeThoughts = true,
        thinkingBudget = 500,
        topLogprobs = 3,
        logprobs = true
    )
)
```
