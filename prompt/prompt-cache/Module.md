# Module prompt:prompt-cache

The prompt-cache module provides functionality for caching prompt execution results to improve performance and reduce redundant API calls.

### Overview

This module consists of three main components:
- **prompt-cache-model**: Core interfaces and data structures for prompt caching
- **prompt-cache-files**: File-based implementation of the prompt cache
- **prompt-cache-redis**: Redis-based implementation of the prompt cache

The module allows for efficient caching of prompt execution results, which can significantly reduce costs and latency when working with LLM APIs by avoiding redundant calls for identical prompts.

### Using in your project

Add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core interfaces
    implementation("ai.koog.prompt:prompt-cache-model:$version")

    // Choose one or more implementations
    implementation("ai.koog.prompt:prompt-cache-files:$version") // For file-based caching
    implementation("ai.koog.prompt:prompt-cache-redis:$version") // For Redis-based caching
}
```

### Using in tests

For unit tests, you can use the in-memory implementation which doesn't require any external dependencies:

```kotlin
// Create an in-memory cache for testing
val cache = ai.koog.prompt.cache.memory.InMemoryPromptCache()
```

### Example of usage

```kotlin
// Create a cache factory
val cacheFactory = PromptCache.Factory.Aggregated(
    FilePromptCache.Factory(Path("/path/to/cache")),
    RedisPromptCache.Companion
)

// Create a cache instance using a configuration string
val cache = cacheFactory.create("file:/path/to/cache:1000") // File cache with 1000 max files
// OR
val cache = cacheFactory.create("redis:redis://localhost:6379:cache-prefix:86400") // Redis cache with 1 day TTL

// Use the cache
val prompt = Prompt.Builder()
    .addSystemMessage("You are a helpful assistant.")
    .addUserMessage("Hello, how are you?")
    .build()

// Try to get a cached response
val cachedResponse = cache.get(prompt)
if (cachedResponse != null) {
    // Use the cached response
    println("Using cached response: ${cachedResponse.first().content}")
} else {
    // Generate a new response
    val response = promptExecutor.execute(prompt)

    // Cache the response for future use
    cache.put(prompt, response)

    println("Generated new response: ${response.first().content}")
}
```
