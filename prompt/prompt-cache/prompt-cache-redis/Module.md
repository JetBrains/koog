# Module prompt-cache-redis

A Redis-based implementation of the PromptCache interface for storing prompt execution results in a Redis database.

### Overview

The `prompt-cache-redis` module provides a Redis-based caching mechanism for storing and retrieving prompt execution results. It implements the `PromptCache` interface defined in the `prompt-cache-model` module. This implementation stores cache entries in a Redis database, with features like:

- Time-to-live (TTL) for cache entries
- Redis connection management
- Error handling with custom exceptions
- Logging of cache hits and misses
- Serialization of prompts and responses to JSON

### Using in your project

To use the Redis-based prompt cache in your project, add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-cache-redis:$version")
}
```

Then, create an instance of `RedisPromptCache` with a Redis client:

```kotlin
// Create a Redis client
val client = RedisClient.create("redis://localhost:6379")

// Create a Redis-based cache with a prefix and TTL
val cache = RedisPromptCache(client, "my-cache:", 1.days)
```

Alternatively, you can use the factory pattern:

```kotlin
// Format: "redis:<uri>:<prefix>:<ttl_seconds>"
val cache = RedisPromptCache.create("redis:redis://localhost:6379:my-cache:86400")
```

### Using in tests

For testing purposes, you can use an embedded Redis server:

```kotlin
class MyTest {
    private lateinit var redisServer: RedisServer
    private lateinit var cache: RedisPromptCache

    @BeforeTest
    fun setup() {
        // Start an embedded Redis server
        redisServer = RedisServer.builder().port(6379).build()
        redisServer.start()

        // Create a Redis client
        val client = RedisClient.create("redis://localhost:6379")

        // Create a Redis-based cache with a prefix and TTL
        cache = RedisPromptCache(client, "test-cache:", 1.hours)
    }

    @AfterTest
    fun cleanup() {
        // Close the cache connection
        cache.close()

        // Stop the embedded Redis server
        redisServer.stop()
    }

    // Your tests here
}
```

### Example of usage

```kotlin
fun main() = runBlocking {
    // Create a Redis client
    val client = RedisClient.create("redis://localhost:6379")

    // Create a Redis-based cache with a prefix and TTL
    val cache = RedisPromptCache(client, "example-cache:", 1.days)

    try {
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
    } finally {
        // Close the cache connection
        cache.close()
    }
}
```
