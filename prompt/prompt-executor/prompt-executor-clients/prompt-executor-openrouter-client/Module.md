# Module prompt-executor-openrouter-client

A client implementation for executing prompts using OpenRouter's API to access various LLM providers.

### Overview

This module provides a client implementation for the OpenRouter API, allowing you to execute prompts using multiple LLM providers through a single interface. OpenRouter gives access to models from different providers including OpenAI, Anthropic, and others.

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
        apiKey = System.getenv("OPEN_ROUTER_API_KEY"),
    )

    val response = client.execute(
        prompt = prompts("test") {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenRouterModels.Phi4Reasoning
    )

    println(response)
}
```
