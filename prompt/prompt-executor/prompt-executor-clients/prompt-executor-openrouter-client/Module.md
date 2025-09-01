# Module prompt-executor-openrouter-client

A client implementation for executing prompts using OpenRouter's API to access various LLM providers with multimodal support and advanced custom parameters.

### Overview

This module provides a client implementation for the OpenRouter API, allowing you to execute prompts using multiple LLM providers through a single interface with extensive parameter customization. OpenRouter gives access to models from different providers including OpenAI, Anthropic, Google, and others. The client supports multimodal content including images, audio, and documents, plus advanced routing and provider selection features.

### Supported Models

#### Free/Testing Models

| Model          | Speed | Input Support | Output Support | Notes            |
|----------------|-------|---------------|----------------|------------------|
| Phi4 Reasoning | Fast  | Text, Tools   | Text, Tools    | Free for testing |

#### OpenAI Models

| Model         | Speed     | Input Support       | Output Support | Pricing  |
|---------------|-----------|---------------------|----------------|----------|
| GPT-4         | Medium    | Text, Tools         | Text, Tools    | Variable |
| GPT-4o        | Fast      | Text, Images, Tools | Text, Tools    | Variable |
| GPT-4 Turbo   | Fast      | Text, Images, Tools | Text, Tools    | Variable |
| GPT-3.5 Turbo | Very Fast | Text, Tools         | Text, Tools    | Variable |

#### Anthropic Models

| Model                  | Speed     | Input Support       | Output Support | Pricing  |
|------------------------|-----------|---------------------|----------------|----------|
| Claude 3 Opus          | Medium    | Text, Images, Tools | Text, Tools    | Variable |
| Claude 3 Sonnet        | Fast      | Text, Images, Tools | Text, Tools    | Variable |
| Claude 3 Haiku         | Very Fast | Text, Images, Tools | Text, Tools    | Variable |
| Claude 3 Vision Opus   | Medium    | Text, Images, Tools | Text, Tools    | Variable |
| Claude 3 Vision Sonnet | Fast      | Text, Images, Tools | Text, Tools    | Variable |
| Claude 3 Vision Haiku  | Very Fast | Text, Images, Tools | Text, Tools    | Variable |

#### Google Models

| Model            | Speed  | Input Support       | Output Support | Pricing  |
|------------------|--------|---------------------|----------------|----------|
| Gemini 1.5 Pro   | Medium | Text, Images, Tools | Text, Tools    | Variable |
| Gemini 1.5 Flash | Fast   | Text, Images, Tools | Text, Tools    | Variable |

#### Meta Models

| Model                | Speed  | Input Support | Output Support | Pricing  |
|----------------------|--------|---------------|----------------|----------|
| Llama 3 70B          | Medium | Text, Tools   | Text, Tools    | Variable |
| Llama 3 70B Instruct | Medium | Text, Tools   | Text, Tools    | Variable |

#### Mistral Models

| Model        | Speed  | Input Support | Output Support | Pricing  |
|--------------|--------|---------------|----------------|----------|
| Mistral 7B   | Fast   | Text, Tools   | Text, Tools    | Variable |
| Mixtral 8x7B | Medium | Text, Tools   | Text, Tools    | Variable |

### Media Content Support

| Content Type | Supported Formats    | Max Size          | Notes                           |
|--------------|----------------------|-------------------|---------------------------------|
| Images       | PNG, JPEG, WebP, GIF | No limit enforced | URL or base64 encoded           |
| Audio        | ❌ Not supported      | -                 | No models have audio capability |
| Documents    | PDF only             | No limit enforced | Base64 encoded only             |
| Video        | ❌ Not supported      | -                 | -                               |

**Important Notes:**

- **Audio**: While the client has audio processing code, no models in OpenRouterModels.kt are configured with
  `LLMCapability.Audio`
- **Documents**: Only PDF files are supported despite client having document capability checks
- **Size limits**: No size validation is enforced in the current implementation

### Model-Specific Parameters Support

The client supports extensive OpenRouter-specific parameters through `OpenRouterParams` class:

```kotlin
val openRouterParams = OpenRouterParams(
    temperature = 0.7,
    maxTokens = 1000,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    topK = 40,
    repetitionPenalty = 1.1,
    minP = 0.02,
    topA = 0.8,
    stop = listOf("\n", "END"),
    logprobs = true,
    topLogprobs = 5,
    transforms = listOf("middle-out"),
    models = listOf("openai/gpt-4o", "anthropic/claude-3-sonnet"),
    route = "fallback",
    provider = ProviderPreferences(
        order = listOf("OpenAI", "Anthropic"),
        ignoreUnknownTools = false
    )
)
```

**Key Parameters:**
- **temperature** (0.0-2.0): Controls randomness in generation
- **topP** (0.0-1.0): Nucleus sampling parameter
- **topK** (≥1): Top-K sampling parameter
- **repetitionPenalty** (0.0-2.0): Penalizes token repetition
- **minP** (0.0-1.0): Minimum cumulative probability for token inclusion
- **topA** (0.0-1.0): Temperature scaling based on probability gain
- **transforms**: Context transformation strategies when exceeding token limits
- **models**: List of allowed models for fallback/routing
- **route**: Request routing strategy ("fallback", etc.)
- **provider**: Provider preferences and settings

**Advanced Routing Features:**
- **Model Fallbacks**: Specify multiple models for automatic fallback
- **Provider Preferences**: Control which providers to use and in what order
- **Context Transforms**: Handle long contexts with middle-out truncation
- **Route Selection**: Choose routing strategies for optimal performance

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-openrouter-client:$version")
}
```

Configure the client with your API key:

```kotlin
val openRouterClient = OpenRouterLLMClient(
    apiKey = "your-openrouter-api-key",
)
```

### Using in tests

For testing, you can use a mock implementation:

```kotlin
val mockOpenRouterClient = MockOpenRouterClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = OpenRouterLLMClient(
        apiKey = System.getenv("OPENROUTER_API_KEY"),
    )

    // Basic example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenRouterModels.Claude3Sonnet
    )

    // Advanced example with custom parameters and model fallbacks
    val advancedResponse = client.execute(
        prompt = prompt {
            system("You are a creative writing assistant")
            user("Write a short story about time travel")
        },
        model = OpenRouterModels.GPT4o,
        params = OpenRouterParams(
            temperature = 0.8,
            maxTokens = 2000,
            topP = 0.95,
            topK = 50,
            repetitionPenalty = 1.1,
            frequencyPenalty = 0.3,
            models = listOf(
                "openai/gpt-4o",
                "anthropic/claude-3-sonnet",
                "google/gemini-1.5-pro"
            ),
            route = "fallback",
            transforms = listOf("middle-out"),
            provider = ProviderPreferences(
                order = listOf("OpenAI", "Anthropic", "Google")
            )
        )
    )

    println(response)
}
```

### Advanced Examples

```kotlin
// Structured output with provider fallbacks
val structuredResponse = client.execute(
    prompt = prompt {
        system("Extract information as JSON")
        user("Apple Inc. was founded in 1976 by Steve Jobs")
    },
    model = OpenRouterModels.Claude3Sonnet,
    params = OpenRouterParams(
        temperature = 0.1,
        models = listOf(
            "anthropic/claude-3-sonnet",
            "openai/gpt-4o"
        ),
        route = "fallback",
        schema = jsonSchema {
            object {
                property("company", string())
                property("foundedYear", integer())
                property("founder", string())
            }
        }
    )
)

// Creative generation with advanced sampling
val creativeResponse = client.execute(
    prompt = prompt {
        system("You are a poet")
        user("Write a haiku about artificial intelligence")
    },
    model = OpenRouterModels.GPT4Turbo,
    params = OpenRouterParams(
        temperature = 1.0,
        topP = 0.9,
        topK = 100,
        topA = 0.8,
        minP = 0.05,
        repetitionPenalty = 1.2,
        transforms = listOf(),  // No context transformation
        provider = ProviderPreferences(
            order = listOf("OpenAI"),
            ignoreUnknownTools = true
        )
    )
)

// Long context handling with transformations
val longContextResponse = client.execute(
    prompt = prompt {
        system("Summarize the following long document")
        user("[Very long text content...]")
    },
    model = OpenRouterModels.Claude3Opus,
    params = OpenRouterParams(
        maxTokens = 1000,
        transforms = listOf("middle-out"),  // Truncate from middle when needed
        models = listOf(
            "anthropic/claude-3-opus",
            "openai/gpt-4-turbo"  // Fallback for long context
        )
    )
)
```

### Multimodal Examples

```kotlin
// Image analysis with provider fallbacks and custom parameters
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = OpenRouterModels.GPT4o,
    params = OpenRouterParams(
        temperature = 0.3,
        models = listOf(
            "openai/gpt-4o",
            "anthropic/claude-3-sonnet"  // Vision fallback
        ),
        route = "fallback"
    )
)

// Document processing with advanced routing
val documentResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this document")
            document("/path/to/document.pdf")
        }
    },
    model = OpenRouterModels.Claude3Sonnet,
    params = OpenRouterParams(
        maxTokens = 1500,
        temperature = 0.2,
        transforms = listOf("middle-out"),  // Handle long documents
        provider = ProviderPreferences(
            order = listOf("Anthropic", "OpenAI"),
            ignoreUnknownTools = false
        )
    )
)

// Note: Audio processing is not supported as no models have LLMCapability.Audio
// The following example would fail at runtime:
/*
val audioData = File("/path/to/audio.mp3").readBytes()
val audioResponse = client.execute(
    prompt = prompt {
        user {
            text("Transcribe this audio")
            audio(audioData, "mp3")
        }
    },
    model = OpenRouterModels.GPT4o // This model lacks Audio capability
)
*/

// Mixed content with advanced sampling and multiple model fallbacks
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Analyze this image and document:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf") // Only PDF supported
            text("What insights can you provide?")
        }
    },
    model = OpenRouterModels.Claude3Sonnet,
    params = OpenRouterParams(
        temperature = 0.5,
        topP = 0.9,
        repetitionPenalty = 1.1,
        models = listOf(
            "anthropic/claude-3-sonnet",
            "openai/gpt-4o",
            "google/gemini-1.5-pro"
        ),
        route = "fallback",
        transforms = listOf("middle-out"),
        provider = ProviderPreferences(
            order = listOf("Anthropic", "OpenAI", "Google")
        )
    )
)
```
