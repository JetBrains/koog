# Module prompt:prompt-executor:prompt-executor-clients

A collection of client implementations for executing prompts using various LLM providers.

### Overview

This module provides client implementations for different LLM providers, allowing you to execute prompts using various
models with support for multimodal content including images, audio, video, and documents. The module includes the
following sub-modules:

1. **prompt-executor-anthropic-client**: Client implementation for Anthropic's Claude models with image and document
   support
2. **prompt-executor-openai-client**: Client implementation for OpenAI's GPT models with image and audio capabilities
3. **prompt-executor-google-client**: Client implementation for Google Gemini models with comprehensive multimodal
   support (audio, image, video, documents)
4. **prompt-executor-openrouter-client**: Client implementation for OpenRouter's API with image, audio, and document
   support
5. **prompt-executor-ollama-client**: Client implementation for local Ollama models

Each client handles authentication, request formatting, response parsing, and media content encoding specific to its
respective API requirements.

### Using in your project

Add the dependency for the specific client you want to use:

```kotlin
dependencies {
    // For Anthropic
    implementation("ai.koog.prompt:prompt-executor-anthropic-client:$version")

    // For OpenAI
    implementation("ai.koog.prompt:prompt-executor-openai-client:$version")

    // For Google Gemini
    implementation("ai.koog.prompt:prompt-executor-google-client:$version")

    // For OpenRouter
    implementation("ai.koog.prompt:prompt-executor-openrouter-client:$version")

    // For Ollama
    implementation("ai.koog.prompt:prompt-executor-ollama-client:$version")
}
```

### Using in tests

For testing, you can use mock implementations provided by each client module:

```kotlin
// Mock Anthropic client
val mockAnthropicClient = MockAnthropicClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)

// Mock OpenAI client
val mockOpenAIClient = MockOpenAIClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)

// Mock OpenRouter client
val mockOpenRouterClient = MockOpenRouterClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

### Example of usage

```kotlin
// Choose the client implementation based on your needs
val client = when (providerType) {
    ProviderType.ANTHROPIC -> AnthropicLLMClient(
        apiKey = System.getenv("ANTHROPIC_API_KEY"),
    )
    ProviderType.OPENAI -> OpenAILLMClient(
        apiKey = System.getenv("OPENAI_API_KEY"),
    )
    ProviderType.GOOGLE -> GoogleLLMClient(
        apiKey = System.getenv("GEMINI_API_KEY"),
    )
    ProviderType.OPENROUTER -> OpenRouterLLMClient(
        apiKey = System.getenv("OPENROUTER_API_KEY"),
    )
}

val response = client.execute(
    prompt = prompt {
        system("You are helpful assistant")
        user("What time is it now?")
    },
    model = chosenModel
)

println(response)
```

### Multimodal Content Support

All clients now support multimodal content through the unified MediaContent API:

```kotlin
// Image analysis example
val response = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            attachments {
                image("/path/to/image.jpg")
            }
        }
    },
    model = visionModel
)

// Document processing example  
val response = client.execute(
    prompt = prompt {
        user {
            text("Summarize this document")
            attachments {
                document("/path/to/document.pdf")
            }
        }
    },
    model = documentModel
)

// Audio transcription (supported by Google and OpenAI)
val audioData = File("/path/to/audio.mp3").readBytes()
val response = client.execute(
    prompt = prompt {
        user {
            text("Transcribe this audio")
            attachments {
                audio(audioData, "mp3")
            }
        }
    },
    model = audioModel
)

// Mixed media content
val response = client.execute(
    prompt = prompt {
        user {
            text("Compare the image with the document content:")
            attachments {
               image("/path/to/screenshot.png")
               document("/path/to/report.pdf")
            }
            text("What are the key differences?")
        }
    },
    model = multimodalModel
)
```

### Supported Media Types by Provider

| Provider         | Images | Audio | Video | Documents |
|------------------|--------|-------|-------|-----------|
| Anthropic Claude | ✅      | ❌     | ❌     | ✅         |
| OpenAI GPT       | ✅      | ✅     | ❌     | ❌         |
| Google Gemini    | ✅      | ✅     | ✅     | ✅         |
| OpenRouter       | ✅      | ✅     | ❌     | ✅         |
| Ollama           | ✅      | ❌     | ❌     | ❌         |
