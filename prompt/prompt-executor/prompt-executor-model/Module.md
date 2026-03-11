# Module prompt-executor-model

Core interfaces and models for executing prompts against language models.

### Overview

This module defines the fundamental interfaces and models for executing prompts against language models. It provides the `PromptExecutor` interface which serves as the foundation for all prompt execution implementations, supporting both synchronous and streaming execution modes, with or without tool assistance.

Additionally, this module provides implementations of the `PromptExecutor` interface for executing prompts with Large Language Models (LLMs). It includes:

- `SingleLLMPromptExecutor`: Executes prompts using a single LLM client
- `MultiLLMPromptExecutor`: Executes prompts across multiple LLM providers with fallback capabilities
- `RoutingLLMPromptExecutor`: Routes requests across multiple clients per provider


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
class MyPromptExecutor : PromptExecutor() {
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

These executors handle both standard and streaming execution of prompts, delegating the actual LLM interaction to the provided LLM clients.

```kotlin
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider

// Example with SingleLLMPromptExecutor
val openAIClient = OpenAILLMClient(apiKey = "your-api-key")
val singleExecutor = SingleLLMPromptExecutor(openAIClient)

// Example with MultiLLMPromptExecutor
val anthropicClient = AnthropicLLMClient(apiKey = "your-anthropic-key")
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient
)

// Execute a prompt
val prompt = prompt("example") {
    system("You are a helpful assistant.")
    user("Tell me about Kotlin.")
}

val model = OpenAIModels.Chat.GPT4o
val responses = multiExecutor.execute(prompt, model)
```
