# Module prompt:prompt-executor:prompt-executor-clients

A collection of client implementations for executing prompts using various LLM providers.

### Overview

This module provides client implementations for different LLM providers, allowing you to execute prompts using various models. The module includes the following sub-modules:

1. **prompt-executor-anthropic-client**: Client implementation for Anthropic's Claude models
2. **prompt-executor-openai-client**: Client implementation for OpenAI's GPT models
3. **prompt-executor-openrouter-client**: Client implementation for OpenRouter's API, which provides access to multiple LLM providers

Each client handles authentication, request formatting, and response parsing specific to its respective API requirements.

### Using in your project

Add the dependency for the specific client you want to use:

```kotlin
dependencies {
    // For Anthropic
    implementation("ai.koog.prompt:prompt-executor-anthropic-client:$version")

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
    ProviderType.OPENROUTER -> OpenRouterLLMClient(
        apiKey = System.getenv("OPENROUTER_API_KEY"),
    )
}

val response = client.execute(
    prompt = prompts("test") {
        system("You are helpful assistant")
        user("What time is it now?")
    },
    model = chosenModel
)

println(response)
```
