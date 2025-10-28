# Module prompt:prompt-executor:prompt-executor-clients

A collection of client implementations for executing prompts using various LLM providers and retry logic features.

### Overview

This module provides client implementations for different LLM providers, allowing you to execute prompts using various
models with support for multimodal content including images, audio, video, and documents. The module includes 
**production-ready retry logic** through the `RetryingLLMClient` decorator, which adds automatic error handling and
resilience to any client implementation.

The module consists of:

**Core Functionality:**
- **LLMClient interface**: Base interface for all LLM client implementations
- **RetryingLLMClient**: Decorator that adds retry logic with configurable policies
- **RetryConfig**: Flexible retry configuration with predefined settings for different use cases

**Provider-Specific Sub-modules:**
1. **prompt-executor-anthropic-client**: Client implementation for Anthropic's Claude models with image and document support
2. **prompt-executor-openai-client**: Client implementation for OpenAI's GPT models with image and audio capabilities
3. **prompt-executor-google-client**: Client implementation for Google Gemini models with comprehensive multimodal support
4. **prompt-executor-mistralai-client**: Client implementation for Mistral AI models with vision, embeddings, and moderation support
5. **prompt-executor-openrouter-client**: Client implementation for OpenRouter's API with image, audio, and document support
6. **prompt-executor-bedrock-client**: Client implementation for AWS Bedrock with support for multiple model providers (JVM only)
7. **prompt-executor-ollama-client**: Client implementation for local Ollama models

Each client handles authentication, request formatting, response parsing, and media content encoding specific to its
respective API requirements.

### Using in your project

Add the dependency for the specific client you want to use:

```kotlin
dependencies { 
   // For Anthropic 
   implementation("ai.koog.prompt:prompt-executor-anthropic-client:$version")

   // For Bedrock
   implementation("ai.koog.prompt:prompt-executor-bedrock-client:$version")

   // For DeepSeek
   implementation("ai.koog.prompt:prompt-executor-deepseek-client:$version")

   // For Google Gemini
   implementation("ai.koog.prompt:prompt-executor-google-client:$version")

   // For MistralAI
   implementation("ai.koog.prompt:prompt-executor-mistralai-client:$version")

   // For Ollama 
   implementation("ai.koog.prompt:prompt-executor-ollama-client:$version")

   // For OpenAI
   implementation("ai.koog.prompt:prompt-executor-openai-client:$version")

   // For OpenRouter 
   implementation("ai.koog.prompt:prompt-executor-openrouter-client:$version")
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
    ProviderType.MISTRALAI -> MistralAILLMClient(
        apiKey = System.getenv("MISTRALAI_API_KEY"),
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

### Retry Logic

Wrap any client with `RetryingLLMClient` to add automatic retry capabilities:

```kotlin
val baseClient = OpenAILLMClient(apiKey = System.getenv("OPENAI_API_KEY"))
val resilientClient = RetryingLLMClient(
    delegate = baseClient,
    config = RetryConfig.PRODUCTION  // Or CONSERVATIVE, AGGRESSIVE, DISABLED
)

val response = resilientClient.execute(prompt, model)

resilientClient.executeStreaming(prompt, model).collect { chunk ->
    print(chunk)
}
```

**Retry Configurations:**
- `RetryConfig.PRODUCTION` - Recommended for production (3 attempts, balanced delays)
- `RetryConfig.CONSERVATIVE` - Fewer retries, longer delays (3 attempts, 2s initial delay)
- `RetryConfig.AGGRESSIVE` - More retries, shorter delays (5 attempts, 500ms initial delay)
- `RetryConfig.DISABLED` - No retries (1 attempt)

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
| Mistral AI       | ✅      | ❌     | ❌     | ✅         |
| OpenRouter       | ✅      | ✅     | ❌     | ✅         |
| Ollama           | ✅      | ❌     | ❌     | ❌         |
