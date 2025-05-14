# Module prompt-llm

A module that provides abstractions and implementations for working with Large Language Models (LLMs) from various providers.

### Overview

The prompt-llm module defines core abstractions for working with Large Language Models (LLMs) in a provider-agnostic way. It includes:

- **LLMCapability**: A sealed class hierarchy that defines various capabilities that LLMs can support, such as:
  - Temperature adjustment for controlling response randomness
  - Tools integration for external system interaction
  - Vision processing for handling visual data
  - Embedding generation for vector representations
  - Completion for text generation tasks
  - Schema support for structured data (JSON with Simple and Full variants)
  - Speculation for exploratory responses

- **LLMProvider**: A sealed class hierarchy that defines supported LLM providers, including:
  - Google
  - OpenAI
  - Anthropic
  - Meta
  - Alibaba
  - OpenRouter

- **LLModel**: A data class that represents a specific LLM with its provider, unique identifier, and supported capabilities.

- **OllamaModels**: A collection of predefined LLM configurations for specific models from providers like Meta and Alibaba, with their respective capabilities.

This module serves as a foundation for interacting with different LLM providers in a unified way, allowing applications to work with various models while abstracting away provider-specific details.

### Example of usage

```kotlin
// Creating a custom LLM configuration
val myCustomModel = LLModel(
    provider = LLMProvider.OpenAI,
    id = "gpt-4-turbo",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.Schema.JSON.Full
    )
)

// Using a predefined model
val metaModel = OllamaModels.Meta.LLAMA_3_2

// Checking if a model supports a specific capability
val supportsTools = myCustomModel.capabilities.contains(LLMCapability.Tools) // true
```
