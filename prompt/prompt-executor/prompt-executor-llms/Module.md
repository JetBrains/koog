# Module prompt-executor-llms

Implementations of PromptExecutor for executing prompts with Large Language Models (LLMs).

### Overview

This module provides implementations of the `PromptExecutor` interface for executing prompts with Large Language Models (LLMs). It includes:

- `SingleLLMPromptExecutor`: Executes prompts using a single LLM client
- `MultiLLMPromptExecutor`: Executes prompts across multiple LLM providers with fallback capabilities

These executors handle both standard and streaming execution of prompts, delegating the actual LLM interaction to the provided LLM clients.

### Using in your project

To use this module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-llms:$version")
}
```

### Example of usage

```kotlin
// Example with SingleLLMPromptExecutor
val openAIClient = OpenAIClient(apiKey = "your-api-key")
val singleExecutor = SingleLLMPromptExecutor(openAIClient)

// Example with MultiLLMPromptExecutor
val anthropicClient = AnthropicClient(apiKey = "your-anthropic-key")
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OPENAI to openAIClient,
    LLMProvider.ANTHROPIC to anthropicClient
)

// Execute a prompt
val prompt = Prompt {
    systemMessage("You are a helpful assistant.")
    userMessage("Tell me about Kotlin.")
}

val model = LLModel.GPT_4
val responses = executor.execute(prompt, model, emptyList())
```
