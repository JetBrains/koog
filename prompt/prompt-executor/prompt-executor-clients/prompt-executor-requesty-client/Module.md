# Module prompt-executor-requesty-client

A client implementation for executing prompts through the Requesty LLM gateway with support for custom parameters.

### Overview

This module provides a client implementation for the Requesty API. Requesty exposes an OpenAI compatible
Chat Completions endpoint that routes to models from OpenAI, Anthropic, Google, DeepSeek and other providers
behind one API key. Model ids are prefixed with the upstream provider, for example `openai/gpt-4o-mini`.

### Supported Models

| Name              | Provider  | Input               | Output      |
|-------------------|-----------|---------------------|-------------|
| [GPT4oMini]       | OpenAI    | Text, Image, Tools  | Text, Tools |
| [GPT4o]           | OpenAI    | Text, Image, Tools  | Text, Tools |
| [GPT4_1]          | OpenAI    | Text, Image, Tools  | Text, Tools |
| [GPT4_1Mini]      | OpenAI    | Text, Image, Tools  | Text, Tools |
| [GPT5]            | OpenAI    | Text, Image, Tools  | Text, Tools |
| [GPT5Mini]        | OpenAI    | Text, Image, Tools  | Text, Tools |
| [ClaudeSonnet4_5] | Anthropic | Text, Image, Tools  | Text, Tools |
| [ClaudeOpus4_5]   | Anthropic | Text, Image, Tools  | Text, Tools |
| [ClaudeHaiku4_5]  | Anthropic | Text, Image, Tools  | Text, Tools |
| [Gemini2_5Flash]  | Google    | Text, Image, Tools  | Text, Tools |
| [Gemini2_5Pro]    | Google    | Text, Image, Tools  | Text, Tools |
| [DeepSeekChat]    | DeepSeek  | Text, Tools         | Text, Tools |

Any other model exposed by Requesty can be used by adding it with `RequestyModels.addCustomModel(...)`
or by creating an `LLModel` with `LLMProvider.Requesty`. The full list is available from `RequestyLLMClient.models()`.

### Model-Specific Parameters Support

The client supports Requesty-specific parameters through the `RequestyParams` class:

```kotlin
val requestyParams = RequestyParams(
    temperature = 0.7,
    maxTokens = 1000,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    stop = listOf("\n", "END"),
    logprobs = true,
    topLogprobs = 5
)
```

**Key Parameters:**
- **temperature** (0.0-2.0): Controls randomness in generation
- **topP** (0.0-1.0): Nucleus sampling parameter
- **frequencyPenalty** (-2.0-2.0): Reduces repetition of frequent tokens
- **presencePenalty** (-2.0-2.0): Encourages new topics/tokens
- **logprobs**: Include log probabilities for generated tokens
- **stop**: Stop sequences to halt generation

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-requesty-client:$version")
}
```

Configure the client with your API key (create one at https://app.requesty.ai/api-keys):

```kotlin
val requestyClient = RequestyLLMClient(
    apiKey = "your-requesty-api-key",
)
```

To route requests through a regional endpoint, override the base URL:

```kotlin
val requestyClient = RequestyLLMClient(
    apiKey = "your-requesty-api-key",
    settings = RequestyClientSettings(baseUrl = "https://router.eu.requesty.ai"),
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = RequestyLLMClient(
        apiKey = System.getenv("REQUESTY_API_KEY"),
    )

    // Basic example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = RequestyModels.GPT4oMini,
    )

    // Advanced example with custom parameters
    val advancedResponse = client.execute(
        prompt = prompt {
            system("You are a helpful coding assistant")
            user("Write a Python function to calculate factorial")
        },
        model = RequestyModels.ClaudeSonnet4_5,
        params = RequestyParams(
            temperature = 0.3,
            maxTokens = 2000,
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
    model = RequestyModels.GPT4oMini,
    params = RequestyParams(
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

// List every model available on the account
val availableModels = client.models()
```
