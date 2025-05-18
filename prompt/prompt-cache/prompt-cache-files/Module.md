# Module prompt-cache-files

A file-based implementation of the PromptCache interface for storing prompt execution results in the file system.

### Overview

The `prompt-cache-files` module provides a file-based caching mechanism for storing and retrieving prompt execution results. It implements the `PromptCache` interface defined in the `prompt-cache-model` module. This implementation stores cache entries as files on the file system, with features like:

- Maximum file limit with LRU (least recently used) eviction policy
- Serialization of prompts and responses to JSON
- Unique ID generation for cache entries
- Thread-safe operations with mutex locks

### Using in your project

To use the file-based prompt cache in your project, add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-cache-files:$version")
}
```

Then, create an instance of `FilePromptCache` with a storage directory:

```kotlin
val cache = FilePromptCache(Path.of("/path/to/cache/directory"), maxFiles = 3000)
```

Alternatively, you can use the factory pattern:

```kotlin
val factory = FilePromptCache.Factory(Path.of("/path/to/default/cache/directory"))
val cache = factory.create("file:/path/to/cache/directory:3000")
```

### Using in tests

For testing purposes, you can create a temporary directory for the cache:

```kotlin
class MyTest {
    private lateinit var tempDir: Path
    private lateinit var cache: FilePromptCache

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("prompt-cache-test")
        cache = FilePromptCache(tempDir)
    }

    @AfterTest
    fun cleanup() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
    }

    // Your tests here
}
```

### Example of usage

```kotlin
fun main() = runBlocking {
    // Create a file-based cache with a maximum of 1000 files
    val cache = FilePromptCache(Path.of("/tmp/prompt-cache"), maxFiles = 1000)

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
