# Module koog-spring-ai-starter-model-embedding

Adapts a Spring AI `EmbeddingModel` into a Koog `LLMEmbeddingProvider`.

### Overview

This starter bridges Spring AI's embedding model abstraction with the Koog agent framework.
It auto-configures a Koog `LLMEmbeddingProvider` (`SpringAiLLMEmbeddingProvider`) that
delegates to a Spring AI `EmbeddingModel`.

### Using in your project

Add the dependency alongside any Spring AI model starter (e.g., Ollama):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.koog:koog-agents-jvm:$koogVersion")
    implementation("ai.koog:koog-spring-ai-starter-model-embedding:$koogVersion")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
}
```

Modifying your Spring Boot properties is not necessary, below are the default settings:

```properties
# application.properties
spring.ai.model.embedding=ollama
koog.spring.ai.embedding.enabled=true
```

If you have a single `EmbeddingModel` bean, everything works automatically —
the adapter wraps it into a Koog `LLMEmbeddingProvider`.

### Example of usage

Inject the `LLMEmbeddingProvider` and use it for vector embedding operations:

```kotlin
@Service
class MyEmbeddingService(private val embeddingProvider: LLMEmbeddingProvider) {

    suspend fun getEmbedding(text: String): List<Double> {
        return embeddingProvider.embed(text, OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
    }
}
```

Or provide your own `LLMEmbeddingProvider` bean to override the auto-configured adapter entirely.

### Configuration properties (`koog.spring.ai.embedding`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the embedding auto-configuration |
| `embedding-model-bean-name` | `String?` | `null` | Bean name of the `EmbeddingModel` to use (for multi-model contexts) |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking model calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher (0 = no limit) |

### Dispatcher types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`. When `dispatcher.parallelism` is greater than 0, uses `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.

### Multi-model contexts

When multiple `EmbeddingModel` beans are registered, specify which one to use:

```properties
koog.spring.ai.embedding.embedding-model-bean-name=openAiEmbeddingModel
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Extension points

- **Custom `LLMEmbeddingProvider`**: Register your own bean to override the auto-configured adapter entirely.
