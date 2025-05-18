# Module prompt-executor-model

Core interfaces and models for executing prompts against language models.

### Overview

This module defines the fundamental interfaces and models for executing prompts against language models. It provides the `PromptExecutor` interface which serves as the foundation for all prompt execution implementations, supporting both synchronous and streaming execution modes, with or without tool assistance.

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-model:$version")
}
```

When implementing a custom prompt executor or working with existing implementations, you'll need to use the interfaces defined in this module:

```kotlin
// Using the PromptExecutor interface
val executor: PromptExecutor = getPromptExecutorImplementation() // obtain an implementation
val result = executor.execute(prompt, model)
```

### Example of usage

```kotlin
// Creating a prompt executor implementation
class MyPromptExecutor : PromptExecutor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        // Implementation details
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        // Implementation details
    }
}

// Using a prompt executor
suspend fun processPrompt(executor: PromptExecutor, prompt: Prompt, model: LLModel) {
    val response = executor.execute(prompt, model)
    println("Response: $response")

    // With streaming
    executor.executeStreaming(prompt, model).collect { chunk ->
        print(chunk)
    }
}
```
