# Module prompt-executor-llms-all

A comprehensive module that provides unified access to multiple LLM providers (OpenAI, Anthropic, OpenRouter) for prompt execution.

### Overview

This module aggregates various LLM clients (OpenAI, Anthropic, OpenRouter) and provides a unified interface for working with multiple LLM providers. It includes implementations of `MultiLLMPromptExecutor` and utility functions to create `SingleLLMPromptExecutor` instances for different providers.

### Using in your project

Add the dependency to your build.gradle.kts file:

```kotlin
implementation("ai.koog.prompt:prompt-executor-llms-all:$version")
```

### Using in tests

For testing with multiple LLM providers, you can use the test utilities provided in this module:

```kotlin
// Create a test executor with mock clients
val testExecutor = createTestMultiLLMExecutor()

// Run your tests with the test executor
testWithMultipleLLMs(testExecutor) {
    // Your test code here
}
```

### Example of usage

```kotlin
// Create a MultiLLMPromptExecutor with OpenAI and Anthropic clients
val openAIClient = OpenAILLMClient("your-openai-api-key")
val anthropicClient = AnthropicLLMClient("your-anthropic-api-key")
val googleClient = GoogleLLMClient("your-google-api-key")
val multiExecutor = MultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

// Or use utility functions to create single LLM executors
val openAIExecutor = simpleOpenAIExecutor("your-openai-api-key")
val anthropicExecutor = simpleAnthropicExecutor("your-anthropic-api-key")
val openRouterExecutor = simpleOpenRouterExecutor("your-openrouter-api-key")
```
