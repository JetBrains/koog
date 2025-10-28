# Module prompt-executor-mistralai-client

A client implementation for executing prompts using Mistral AI models with support for vision, embeddings, moderation, and custom parameters.

### Overview

This module provides a client implementation for the Mistral AI API, allowing you to execute prompts using Mistral AI models with full control over generation parameters, structured outputs, and multimodal content.

### Supported Models

#### Chat Models

| Name                 | Speed  | Context | Input Support       | Output Support | Price (per 1M tokens) |
|----------------------|--------|---------|---------------------|----------------|-----------------------|
| [MistralMedium31]    | Medium | 128K    | Text, Images, Tools | Text, Tools    | $0.4-$2               |
| [MistralLarge21]     | Medium | 128K    | Text, Tools         | Text, Tools    | $2-$8                 |
| [MistralSmall2]      | Fast   | 32K     | Text, Tools         | Text, Tools    | $0.2-$0.6             |
| [MagistralMedium12]  | Medium | 128K    | Text, Images, Tools | Text, Tools    | $0.4-$2               |
| [Codestral]          | Fast   | 256K    | Text, Tools         | Text, Tools    | $0.2-$0.6             |
| [DevstralMedium]     | Medium | 128K    | Text, Tools         | Text, Tools    | $0.4-$2               |
| [Ministral3B]        | Fast   | 128K    | Text, Tools         | Text, Tools    | $0.04-$0.16           |

#### Embedding Models

| Model             | Speed  | Dimensions | Context | Input Support | Price (per 1M tokens) |
|-------------------|--------|------------|---------|---------------|-----------------------|
| [MistralEmbed]    | Medium | 1024       | 8K      | Text          | $0.1                  |
| [CodestralEmbed]  | Medium | 1024       | 8K      | Text, Code    | $0.1                  |

#### Moderation Models

| Model                | Speed  | Context | Input Support | Output Support | Price (per 1M tokens) |
|----------------------|--------|---------|---------------|----------------|-----------------------|
| [MistralModeration]  | Fast   | 8K      | Text          | Categories     | $0.01                 |

### Media Content Support

| Content Type | Supported Formats | Max Size | Notes                                   |
|--------------|-------------------|----------|-----------------------------------------|
| Images       | PNG, JPEG, WebP   | 20MB     | Base64 encoded or URL (vision models)   |
| Documents    | PDF               | 20MB     | Base64 encoded only (vision models)     |
| Audio        | ❌ Not supported   | -        | -                                       |
| Video        | ❌ Not supported   | -        | -                                       |

**Important Details:**

- **Images & Documents**: Supported by MistralMedium31 and MagistralMedium12 models
- **Vision**: Requires models with Vision.Image capability
- **Embeddings**: Text and code embedding support through dedicated models
- **Moderation**: Content safety detection for text content

### Model-Specific Parameters Support

The client supports Mistral AI-specific parameters through `MistralAIParams` class:

```kotlin
val mistralParams = MistralAIParams(
    temperature = 0.7,
    maxTokens = 1000,
    topP = 0.9,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    stop = listOf("\n", "END"),
    randomSeed = 42,
    parallelToolCalls = true,
    safePrompt = false,
    promptMode = "reasoning"
)
```

**Key Parameters:**
- **temperature** (0.0-2.0): Controls randomness in generation
- **topP** (0.0-1.0): Nucleus sampling parameter
- **frequencyPenalty** (-2.0-2.0): Reduces repetition of frequent tokens
- **presencePenalty** (-2.0-2.0): Encourages new topics/tokens
- **stop**: Stop sequences (0-4 items) to halt generation
- **randomSeed**: Seed for deterministic generation
- **parallelToolCalls**: Allow multiple tool calls in parallel
- **safePrompt**: Inject safety prompt before conversations
- **promptMode**: Toggle reasoning mode and system prompt behavior

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-mistralai-client:$version")
}
```

Configure the client with your API key:

```kotlin
val mistralClient = MistralAILLMClient(
    apiKey = "your-mistral-api-key",
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = MistralAILLMClient(
        apiKey = System.getenv("MISTRAL_API_KEY"),
    )

    // Basic example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = MistralAIModels.Chat.MistralMedium31,
    )

    // Advanced example with custom parameters
    val advancedResponse = client.execute(
        prompt = prompt {
            system("You are a helpful coding assistant")
            user("Write a Python function to calculate factorial")
        },
        model = MistralAIModels.Chat.Codestral,
        params = MistralAIParams(
            temperature = 0.3,
            maxTokens = 2000,
            frequencyPenalty = 0.1,
            topP = 0.95,
            stop = listOf("```\n\n"),
            randomSeed = 42
        )
    )

    println(response)
    println(advancedResponse)
}
```

### Multimodal Examples

```kotlin
// Vision: Image analysis with MistralMedium31
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = MistralAIModels.Chat.MistralMedium31,
    params = MistralAIParams(
        temperature = 0.3,
        maxTokens = 1000
    )
)

// Document processing with MagistralMedium12
val documentResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this document")
            document("/path/to/document.pdf")
        }
    },
    model = MistralAIModels.Chat.MagistralMedium12,
    params = MistralAIParams(
        temperature = 0.5,
        maxTokens = 2000,
        promptMode = "reasoning"
    )
)

// Mixed content with vision model
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare this image with the document:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf")
            text("What are the key differences?")
        }
    },
    model = MistralAIModels.Chat.MagistralMedium12,
    params = MistralAIParams(
        temperature = 0.5,
        maxTokens = 4000
    )
)
```

### Additional Examples

```kotlin
// Structured output with JSON schema
val structuredResponse = client.execute(
    prompt = prompt {
        system("Extract key information as JSON")
        user("John Doe, age 30, works as software engineer at TechCorp")
    },
    model = MistralAIModels.Chat.MistralMedium31,
    params = MistralAIParams(
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

// Multiple choices generation
val choices = client.executeMultipleChoices(
    prompt = prompt {
        system("You are a creative assistant")
        user("Give me three different opening lines for a story")
    }.withUpdatedParams {
        numberOfChoices = 3
        temperature = 0.8
    },
    model = MistralAIModels.Chat.MistralLarge21
)

// Embedding example
val embedding = client.embed(
    text = "This is a sample text for embedding",
    model = MistralAIModels.Embeddings.MistralEmbed
)

// Code embedding
val codeEmbedding = client.embed(
    text = "function factorial(n) { return n <= 1 ? 1 : n * factorial(n-1); }",
    model = MistralAIModels.Embeddings.CodestralEmbed
)

// Moderation example
val moderationResult = client.moderate(
    prompt = prompt {
        user("This is a test message for content safety check")
    },
    model = MistralAIModels.Moderation.MistralModeration
)

if (moderationResult.isHarmful) {
    println("Content flagged as harmful")
    moderationResult.categories.forEach { (category, result) ->
        if (result.detected) {
            println("Category: $category, Score: ${result.confidenceScore}")
        }
    }
}

// Coding with Devstral Medium (enterprise coding model)
val codingResponse = client.execute(
    prompt = prompt {
        system("You are an expert software engineer")
        user("Refactor this code to use async/await pattern:\n\n$codeSnippet")
    },
    model = MistralAIModels.Chat.DevstralMedium,
    params = MistralAIParams(
        temperature = 0.2,
        maxTokens = 3000
    )
)

// Edge deployment with Ministral 3B
val edgeResponse = client.execute(
    prompt = prompt {
        system("You are a helpful assistant")
        user("What is the capital of France?")
    },
    model = MistralAIModels.Chat.Ministral3B,
    params = MistralAIParams(
        temperature = 0.5,
        maxTokens = 100
    )
)

// Reasoning with MagistralMedium12
val reasoningResponse = client.execute(
    prompt = prompt {
        system("Solve this step by step")
        user("If a train travels 120 km in 2 hours, what's its average speed?")
    },
    model = MistralAIModels.Chat.MagistralMedium12,
    params = MistralAIParams(
        temperature = 0.5,
        promptMode = "reasoning"
    )
)

// Streaming example
client.executeStreaming(
    prompt = prompt {
        user("Write a short story about AI")
    },
    model = MistralAIModels.Chat.MistralLarge21
).collect { chunk ->
    print(chunk)
}
```