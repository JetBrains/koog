# Module prompt-executor-dashscope-client

A client implementation for executing prompts using Alibaba Cloud's DashScope API with Qwen models. Provides OpenAI-compatible interface for seamless integration.

### Overview

This module provides a client implementation for the DashScope API, allowing you to execute prompts using Alibaba's Qwen models. It leverages OpenAI-compatible endpoints and handles authentication, request formatting, response parsing, and tool integration specific to DashScope's API requirements.

### Model-Specific Parameters Support

#### DashScope Parameters

The client supports DashScope-specific parameters through `DashscopeParams` class:

```kotlin
val dashscopeParams = DashscopeParams(
    temperature = 0.7,        // Controls randomness (0.0 to 2.0)
    maxTokens = 1000,         // Maximum tokens to generate
    toolChoice = LLMParams.ToolChoice.AUTO  // Tool usage control
)
```

### API Endpoints

The client connects to DashScope using OpenAI-compatible endpoints:

- **China mainland**: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- **International**: `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-dashscope-client:$version")
}
```

Configure the client with your API key:

```kotlin
val dashscopeClient = DashscopeLLMClient(
    apiKey = "your-dashscope-api-key",
)
```

### Configuration Options

```kotlin
// Default configuration (China mainland)
val client = DashscopeLLMClient(
    apiKey = "your-api-key"
)

// International configuration
val internationalClient = DashscopeLLMClient(
    apiKey = "your-api-key",
    settings = DashscopeClientSettings(
        baseUrl = "https://dashscope-intl.aliyuncs.com/",
        timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 60_000,
            connectTimeoutMillis = 30_000,
            socketTimeoutMillis = 60_000
        )
    )
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = DashscopeLLMClient(
        apiKey = System.getenv("DASHSCOPE_API_KEY"),
    )

    // Basic text completion
    val response = client.execute(
        prompt = prompt {
            system("你是一个有用的AI助手")
            user("你好，请介绍一下自己")
        },
        model = DashscopeModels.QWEN_PLUS,
        params = LLMParams(
            temperature = 0.7,
            maxTokens = 1000
        )
    )

    println(response)
}
```

### Tool Usage Examples

```kotlin
// Using tools with DashScope
val responseWithTools = client.execute(
    prompt = prompt {
        system("You are a helpful assistant with access to tools")
        user("What's the weather like in Beijing?")
    },
    model = DashscopeModels.QWEN_PLUS,
    params = LLMParams(
        temperature = 0.5,
        toolChoice = LLMParams.ToolChoice.AUTO
    ),
    tools = listOf(weatherTool) // Your defined tools
)

// Streaming response
val streamingResponse = client.executeStream(
    prompt = prompt {
        user("Write a short story about a robot learning to paint")
    },
    model = DashscopeModels.QWEN_FLASH,
    params = LLMParams(
        temperature = 0.8,
        maxTokens = 2000
    )
)

streamingResponse.collect { chunk ->
    print(chunk)
}

// Using speculation for complex reasoning
val speculationResponse = client.execute(
    prompt = prompt {
        system("Analyze the following problem step by step")
        user("How can we optimize database query performance for a social media platform?")
    },
    model = DashscopeModels.QWEN_PLUS, // Supports speculation capability
    params = LLMParams(
        temperature = 0.3,
        maxTokens = 3000
    )
)
```

### Advanced Configuration

```kotlin
// Custom timeout configuration
val customClient = DashscopeLLMClient(
    apiKey = "your-api-key",
    settings = DashscopeClientSettings(
        baseUrl = "https://dashscope.aliyuncs.com/",
        chatCompletionsPath = "compatible-mode/v1/chat/completions",
        timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000, // 2 minutes
            connectTimeoutMillis = 30_000,  // 30 seconds
            socketTimeoutMillis = 120_000   // 2 minutes
        )
    ),
    baseClient = HttpClient {
        // Custom HTTP client configuration
    }
)
```
