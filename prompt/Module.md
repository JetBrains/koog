# Module prompt

The prompt module provides a flexible framework for creating, managing, and executing structured prompts for language model interactions.

### Overview

This module consists of several submodules that work together to provide comprehensive prompt handling capabilities:

- **prompt-model**: Core data structures and DSL for creating structured prompts
- **prompt-executor**: Components for executing prompts against various language models
- **prompt-llm**: Interfaces and models for language model capabilities and providers
- **prompt-cache**: Caching mechanisms for prompt responses to improve performance
- **prompt-markdown**: Utilities for working with Markdown in prompts
- **prompt-structure**: Tools for parsing and generating structured data from prompts
- **prompt-xml**: XML handling utilities for prompt content

The module enables creating well-structured prompts with a Kotlin DSL, executing them against different LLM providers, and processing the responses in various formats.

### Using in your project

Add the necessary dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core prompt functionality
    implementation("ai.koog.prompt:prompt-model:$version")

    // For executing prompts
    implementation("ai.koog.prompt:prompt-executor-model:$version")
    implementation("ai.koog.prompt:prompt-executor-llms:$version")

    // Optional modules based on your needs
    implementation("ai.koog.prompt:prompt-cache-model:$version")
    implementation("ai.koog.prompt:prompt-markdown:$version")
    implementation("ai.koog.prompt:prompt-structure:$version")
}
```

### Using in tests

For unit tests, you can use the same dependencies as in your main project. Consider creating mock implementations of the `PromptExecutor` interface to test your code without making actual LLM calls:

```kotlin
// Example of a mock prompt executor for testing
class MockPromptExecutor : PromptExecutor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        // Return predefined responses based on the prompt
        return listOf(Message.Response("Mock response for: ${prompt.id}"))
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        // Return a flow with predefined responses
        return flowOf("Mock streaming response for: ${prompt.id}")
    }
}
```

### Example of usage

```kotlin
// Create a prompt using the DSL
val prompt = prompt("example-prompt") {
    system("You are a helpful assistant specialized in Kotlin programming.")
    user("How do I implement a singleton in Kotlin?")
}

// Execute the prompt with a language model
val response = promptExecutor.execute(prompt, OpenAIModels.GPT_4)

println("Response: $response")
```
