# Module prompt:prompt-executor:prompt-executor-ollama

A client implementation for executing prompts using Ollama, a local LLM service.

### Overview

This module provides a client implementation for the Ollama API, allowing you to execute prompts using locally hosted language models. It handles request formatting, response parsing, and supports both streaming and non-streaming execution modes, as well as tool calling functionality.

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.grazie.koan:prompt-executor-ollama:$koanVersion")
}
```

Configure the client with your Ollama server:

```kotlin
val ollamaExecutor = OllamaPromptExecutor.default() // Uses default localhost:11434
// Or with custom configuration
val customClient = OllamaClient(baseUrl = "http://your-ollama-server:11434")
val ollamaExecutor = OllamaPromptExecutor(customClient)
```

### Using in tests

For testing, you can create a mock implementation:

```kotlin
val mockOllamaClient = MockOllamaClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
val mockExecutor = OllamaPromptExecutor(mockOllamaClient)
```

### Example of usage

```kotlin
suspend fun main() {
    val executor = OllamaPromptExecutor.default()

    val prompt = prompt {
        systemMessage("You are a helpful assistant.")
        userMessage("Write a short poem about programming.")
    }

    val response = executor.execute(
        prompt = prompt,
        model = OllamaModels.Llama3.Llama3_8B
    )

    println(response)
}
```
