# Module koog-spring-ai-starter-vector-store

Adapts a Spring AI `VectorStore` into Koog's `KoogVectorStore`, providing ingestion, retrieval, and deletion capabilities.

### Overview

This starter bridges Spring AI VectorStore implementations with Koog's `rag-base` storage abstractions.
It auto-configures a `SpringAiKoogVectorStore` adapter that delegates to a Spring AI `VectorStore`
and exposes it as a `KoogVectorStore` — a unified interface combining:

- `WriteStorage<DocumentWithMetadata>` — add and update documents
- `SearchStorage<DocumentWithMetadata, SimilaritySearchRequest>` — similarity search with filtering and score thresholds
- `FilteringDeletionStorage` — delete by IDs or by filter expression

The adapter uses a `DocumentWithMetadata` model whose metadata values are restricted to primitive types
(String, Number, Boolean) to match Spring AI `Document` metadata constraints.

> **Note:** Namespace scoping is **not implemented**. Passing a non-null `namespace` to any method will throw `IllegalArgumentException`.

### Using in your project

Add the dependency alongside any Spring AI VectorStore starter (e.g., PgVector):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.koog:koog-spring-ai-starter-vector-store:$koogVersion")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
}
```

### Example of usage

Inject `KoogVectorStore` (or any of its super-interfaces) directly into your Spring components:

```kotlin
@Service
class MyKnowledgeBase(
    private val vectorStore: KoogVectorStore,
) {

    suspend fun ingest(text: String): List<String> {
        return vectorStore.add(
            listOf(DocumentWithMetadata(content = text, metadata = mapOf("source" to "user")))
        )
    }

    suspend fun search(query: String): List<SearchResult<DocumentWithMetadata>> {
        return vectorStore.search(SimilaritySearchRequest(queryText = query, limit = 5))
    }

    suspend fun remove(ids: List<String>) {
        vectorStore.delete(ids)
    }
}
```

### Auto-configuration behavior

- If a single `VectorStore` bean exists in the context, it is used automatically.
- If multiple `VectorStore` beans exist, set `koog.spring.ai.vectorstore.vector-store-bean-name` to select one.
- When `vector-store-bean-name` is set, the single-candidate path is suppressed — exactly one `KoogVectorStore` is created via the named path.
- The dispatcher for blocking VectorStore calls defaults to `AUTO`, which uses Spring's `AsyncTaskExecutor` if available, otherwise falls back to `Dispatchers.IO`.

### Configuration properties (`koog.spring.ai.vectorstore`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the VectorStore auto-configuration |
| `vector-store-bean-name` | `String?` | `null` | Bean name of the `VectorStore` to use when multiple stores are present |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking VectorStore calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher |

### Dispatcher types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`. When `dispatcher.parallelism` is greater than 0, uses `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.

### Multi-store contexts

When multiple `VectorStore` beans are registered, specify which one to use:

```properties
koog.spring.ai.vectorstore.vector-store-bean-name=pgVectorStore
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Current limitations

- Spring AI's `VectorStore` contract exposes similarity search only.
- Spring AI's `VectorStore` contract has no update API. The adapter emulates updates as `delete(ids)` followed by `add(documents)`, which is not transactional.
- Spring AI's `VectorStore` contract has no portable read-by-id API, so `LookupStorage` is intentionally not implemented.
- The `delete(filterExpression)` method returns an empty list because Spring AI does not return deleted IDs.
