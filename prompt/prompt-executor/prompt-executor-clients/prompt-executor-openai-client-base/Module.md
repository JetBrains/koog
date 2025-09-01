# Module prompt-executor-openai-client-base

Abstract base client and data models for OpenAI-compatible LLM client implementations.

### Overview

This module provides the foundational components for building OpenAI-compatible LLM clients. It includes:

- **AbstractOpenAILLMClient**: Abstract base class that implements common OpenAI API functionality
- **OpenAI Data Models**: Complete set of serializable data classes for OpenAI API requests and responses
- **Multimodal Content Support**: Handles text, images, audio, and file attachments
- **Tool Integration**: Full support for function calling and tool execution
- **Streaming Support**: Built-in streaming capabilities with server-sent events

This module serves as the foundation for concrete OpenAI client implementations and can be extended to support OpenAI-compatible APIs like Azure OpenAI, OpenRouter, and other providers that follow the OpenAI API format.

### Key Components

#### AbstractOpenAILLMClient
- Base implementation for OpenAI-compatible clients
- Handles HTTP communication, authentication, and request/response processing
- Supports both synchronous and streaming execution modes
- Includes multimodal content processing (text, images, audio, files)
- Provides tool calling functionality with automatic serialization

#### Data Models
- **OpenAIChatCompletionRequest/Response**: Complete request/response models
- **OpenAIMessage**: Message types (System, User, Assistant, Tool, Developer)
- **Content & ContentPart**: Multimodal content support
- **OpenAITool**: Function calling definitions and responses
- **OpenAIAudio**: Audio configuration and response handling

### Using in your project

This module is typically not used directly but as a dependency for concrete OpenAI client implementations:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-openai-model:$version")
}
```

### Creating a Custom OpenAI-Compatible Client

```kotlin
class CustomOpenAIClient(
    apiKey: String,
    baseUrl: String = "https://api.openai.com/v1"
) : AbstractOpenAILLMClient(
    apiKey = apiKey,
    settings = object : OpenAIBasedSettings(
        baseUrl = baseUrl,
        chatCompletionsPath = "/chat/completions"
    ) {}
)

// Usage
suspend fun main() {
    val client = CustomOpenAIClient(
        apiKey = System.getenv("API_KEY"),
        baseUrl = "https://custom-openai-api.com/v1"
    )
    
    val response = client.execute(
        prompt = prompt {
            system("You are a helpful assistant")
            user("Hello!")
        },
        model = MyCustomModel("gpt-4o")
    )
    
    println(response.first().content)
}
```

### Supported Features

| Feature | Support | Notes |
|---------|---------|-------|
| Text Generation |  | Complete text generation support |
| Streaming |  | Server-sent events with Flow API |
| Function Calling |  | Full tool integration with serialization |
| Multimodal Input |  | Images, audio, documents via attachments |
| Audio Output |  | Audio generation for compatible models |
| Multiple Choices |  | Support for n > 1 completions |
| Temperature Control |  | Temperature and other sampling parameters |
| Token Usage |  | Detailed token usage statistics |

### Extension Points

When extending `AbstractOpenAILLMClient`:

1. **Custom Settings**: Override `OpenAIBasedSettings` for provider-specific configuration
2. **Authentication**: Customize HTTP client setup for different auth methods  
3. **Error Handling**: Override error processing for provider-specific error formats
4. **Model Capabilities**: Define model capabilities and validation
5. **Request Modification**: Customize request building for API variations

This module ensures consistency across different OpenAI-compatible providers while allowing flexibility for provider-specific customizations.
