# Module prompt-cache-model

Core interfaces and models for caching prompt execution results with an in-memory implementation.

### Overview

The `prompt-cache-model` module defines the core interfaces and models for caching prompt execution results. It provides:

- The `PromptCache` interface that defines the contract for all cache implementations
- A factory pattern for creating different cache implementations
- An in-memory implementation (`InMemoryPromptCache`) for storing cache entries in memory

The module is designed to be extended with different storage backends, such as file-based or Redis-based caches.

### Using in your project

To use the prompt cache model in your project, add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-cache-model:$version")
}
```

You can then use the `PromptCache` interface and the in-memory implementation:

```kotlin
// Create an in-memory cache with a maximum of 1000 entries
val cache = InMemoryPromptCache(maxEntries = 1000)

// Or use the factory pattern
val cache = InMemoryPromptCache.create("memory:1000")
```

### Using in tests

The in-memory implementation is particularly useful for testing:

```kotlin
class MyTest {
    private lateinit var cache: PromptCache

    @BeforeTest
    fun setup() {
        // Create an unlimited in-memory cache for testing
        cache = InMemoryPromptCache(maxEntries = null)
    }

    // Your tests here
}
```

### Example of usage

```kotlin
fun main() = runBlocking {
    // Create an in-memory cache with a maximum of 1000 entries
    val cache = InMemoryPromptCache(maxEntries = 1000)

    // Create a prompt
    val prompt = Prompt {
        message {
            role = Message.Role.USER
            content = "What is the capital of France?"
        }
    }

    // Try to get a cached response
    val cachedResponse = cache.get(prompt)

    if (cachedResponse != null) {
        println("Found cached response: ${cachedResponse.first().content}")
    } else {
        // Simulate getting a response from an LLM
        val response = listOf(
            Message.Response(
                content = "The capital of France is Paris.",
                role = Message.Role.ASSISTANT
            )
        )

        // Cache the response
        cache.put(prompt, emptyList(), response)
        println("Cached new response: ${response.first().content}")
    }
}
```
