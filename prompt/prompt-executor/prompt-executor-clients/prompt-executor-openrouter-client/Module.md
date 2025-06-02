# Module prompt-executor-openrouter-client

A client implementation for executing prompts using OpenRouter's API to access various LLM providers with multimodal
support.

### Overview

This module provides a client implementation for the OpenRouter API, allowing you to execute prompts using multiple LLM
providers through a single interface. OpenRouter gives access to models from different providers including OpenAI,
Anthropic, Google, and others. The client supports multimodal content including images, audio, and documents.

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

    // Text-only example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenRouterModels.Claude3Sonnet
    )

    println(response)
}
```

### Multimodal Examples

```kotlin
// Image analysis with GPT-4o
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = OpenRouterModels.GPT4o
)

// Document processing with Claude
val documentResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this document")
            document("/path/to/document.pdf")
        }
    },
    model = OpenRouterModels.Claude3Sonnet
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

// Mixed content with image and document
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Analyze this image and document:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf") // Only PDF supported
            text("What insights can you provide?")
        }
    },
    model = OpenRouterModels.Claude3Sonnet
)
```
