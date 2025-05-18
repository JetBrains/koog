# Module prompt-executor-anthropic-client

A client implementation for executing prompts using Anthropic's Claude models.

### Overview

This module provides a client implementation for the Anthropic API, allowing you to execute prompts using Claude models. It handles authentication, request formatting, and response parsing specific to Anthropic's API requirements.

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-anthropic-client:$version")
}
```

Configure the client with your API key:

```kotlin
val anthropicClient = AnthropicLLMClient(
    apiKey = "your-anthropic-api-key",
)
```

### Using in tests

For testing, you can use a mock implementation:

```kotlin
val mockAnthropicClient = MockAnthropicClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = AnthropicLLMClient(
        apiKey = System.getenv("ANTHROPIC_API_KEY"),
    )

    val response = client.execute(
        prompt = prompts("test") {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = AnthropicModels.Sonnet_3_7
    )

    println(response)
}
```
