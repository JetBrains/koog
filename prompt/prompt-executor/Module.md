# Module prompt:prompt-executor

A comprehensive framework for executing prompts against various Large Language Models (LLMs) with support for multiple providers, local execution, and advanced features.

### Overview

The prompt-executor module provides a unified interface for executing prompts against various Large Language Models (LLMs). It consists of several sub-modules:

- **prompt-executor-model**: Core interfaces and models for executing prompts against language models
- **prompt-executor-cached**: Caching implementation for prompt execution
- **prompt-executor-clients**: Client implementations for various LLM providers and a retry logic decorator
- **prompt-executor-llms**: Implementations of PromptExecutor for executing prompts with LLMs
- **prompt-executor-llms-all**: Unified access to multiple LLM providers for prompt execution
- **prompt-executor-ollama**: Client implementation for executing prompts using Ollama, a local LLM service

The module supports both synchronous and streaming execution modes, with or without tool assistance, and provides fallback capabilities when using multiple LLM providers.

### Using in your project

Add the dependencies for the specific sub-modules you need:

```kotlin
dependencies {
    // Core interfaces and models
    implementation("ai.koog.prompt:prompt-executor-model:$version")

    // Client implementations
    implementation("ai.koog.prompt:prompt-executor-clients:$version")

    // LLM executors
    implementation("ai.koog.prompt:prompt-executor-llms:$version")

    // For unified access to multiple providers
    implementation("ai.koog.prompt:prompt-executor-llms-all:$version")

    // For local LLM execution with Ollama
    implementation("ai.koog.prompt:prompt-executor-ollama:$version")
}
```

### Example of usage

```kotlin
// Create a prompt executor with multiple LLM providers
val openAIClient = OpenAILLMClient("your-openai-api-key")
val anthropicClient = AnthropicLLMClient("your-anthropic-api-key")
val multiExecutor = MultiLLMPromptExecutor(openAIClient, anthropicClient)

// Create a prompt
val prompt = prompt {
    systemMessage("You are a helpful assistant.")
    userMessage("Explain quantum computing in simple terms.")
}

// Execute the prompt
val response = multiExecutor.execute(
    prompt = prompt,
    model = LLModel.GPT_4
)

println(response)

// Streaming execution
multiExecutor.executeStreaming(prompt, LLModel.GPT_4).collect { chunk ->
    print(chunk)
}
```
