# Module prompt-executor-cached

A caching wrapper for PromptExecutor that stores and retrieves responses to avoid redundant LLM calls.

### Overview

The prompt-executor-cached module provides a CachedPromptExecutor implementation that wraps another PromptExecutor and adds caching functionality. It stores responses from LLM calls in a cache and retrieves them when the same prompt is executed again, reducing redundant API calls, saving costs, and improving response times.

### Using in your project

To use the CachedPromptExecutor in your project, you need to provide a PromptCache implementation and a nested PromptExecutor:

```kotlin
// Initialize your cache implementation
val cache: PromptCache = FilePromptCache(cacheDir) 
// Initialize your base executor
val nestedExecutor: PromptExecutor = OpenAIPromptExecutor(apiKey) 
// Create the cached executor
val cachedExecutor = CachedPromptExecutor(cache, nestedExecutor)
```

### Example of usage

```kotlin
// Create a cached executor
val cache = FilePromptCache(cacheDir)
val llmExecutor = OpenAIPromptExecutor(apiKey)
val cachedExecutor = CachedPromptExecutor(cache, llmExecutor)

// Define the model to use
val model = OpenAIModel.GPT_4

// Execute a prompt - will call the LLM and cache the result
val prompt = prompt {
    system("You are a helpful assistant.")
    user("What is the capital of France?")
}
val response = cachedExecutor.execute(prompt, model)

// Execute the same prompt again - will retrieve from cache
val cachedResponse = cachedExecutor.execute(prompt, model)
```
