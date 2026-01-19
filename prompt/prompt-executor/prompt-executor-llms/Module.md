# Module prompt-executor-llms

Implementations of PromptExecutor for executing prompts with Large Language Models (LLMs).

### Overview

This module provides implementations of the `PromptExecutor` interface for executing prompts with Large Language Models (LLMs). It includes:

- `SingleLLMPromptExecutor`: Executes prompts using a single LLM client
- `MultiLLMPromptExecutor`: Executes prompts across multiple LLM providers with fallback capabilities
- `MultiModelLLMPromptExecutor`*: Executes prompts across multiple LLM models with model-specific client routing and fallback strategy

These executors handle both standard and streaming execution of prompts, delegating the actual LLM interaction to the provided LLM clients.

*Info: `MultiModelLLMPromptExecutor` solves a specific problem with Azure AI Services where the model parameter is ignored and the actual model is determined by the deployment in the base-uri. New model with Azure AI provider requires a new deployment and client, so to switch models you need to switch deployments. This executor allows you to map models to specific clients (deployments) and route requests accordingly.

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

val multiModelExecutor = MultiModelLLMPromptExecutor(
    llmClients = mapOf(
        OpenAIModels.Chat.GPT4o to gpt4Client,
        OpenAIModels.CostOptimized.GPT4oMini to gpt4oMiniClient,
    ),
    fallback = MultiModelLLMPromptExecutor.FallbackPromptExecutorSettings(
        fallbackModel = OpenAIModels.Chat.GPT4o,
        fallbackClient = gpt4Client
    )
)

// Execute a prompt
val prompt = Prompt {
    systemMessage("You are a helpful assistant.")
    userMessage("Tell me about Kotlin.")
}

val model = LLModel.GPT_4
val responses = executor.execute(prompt, model, emptyList())
```
