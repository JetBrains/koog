# Long-term memory

Feature (Experimental)

The `LongTermMemory` feature adds persistent memory to Koog AI agents via two independent group of settings:
- **Retrieval** — augments LLM prompts with relevant context from a memory storage (Retrieval-Augmented Generation or RAG)
- **Ingestion** — persists conversation messages into a memory storage for future retrieval

## Quick Start

> **Note:** `LongTermMemory` is an experimental API. Annotate your code with `@OptIn(ExperimentalAgentsApi::class)` or add `@file:OptIn(ExperimentalAgentsApi::class)` at the top of your file.

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    val myStorage = InMemoryRecordStorage() // or your vector DB adapter

    @OptIn(ExperimentalAgentsApi::class)
    val agent = AIAgent(
        promptExecutor = executor,
        strategy = singleRunStrategy(),
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry.EMPTY
    ) {
        install(LongTermMemory) {
            retrieval {
                storage = myStorage
                searchStrategy = SimilaritySearchStrategy(topK = 5)
            }
        }
    }

    agent.run("What did we discuss yesterday?")
    ```

=== "Java"

    ```java
    InMemoryRecordStorage myStorage = new InMemoryRecordStorage();

    AIAgent agent = AIAgent.builder()
        .promptExecutor(executor)
        .llmModel(OpenAIModels.Chat.GPT4o)
        .systemPrompt("You are a helpful assistant.")
        .install(LongTermMemory.Feature, config -> {
            config.retrieval(
                new LongTermMemory.RetrievalSettingsBuilder()
                    .withStorage(myStorage)
                    .withSearchStrategy(
                        SearchStrategy.builder().similarity().withTopK(5).build()
                    )
                    .build()
            );
        })
        .build();

    Object result = agent.run("What did we discuss yesterday?");
    ```

## Retrieval Only (RAG)

Use retrieval without ingestion when you have a pre-populated knowledge base:

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    install(LongTermMemory) {
        retrieval {
            storage = myVectorDbStorage
            namespace = "my-collection"  // optional: scope to a specific namespace/collection
            searchStrategy = SimilaritySearchStrategy(topK = 3, similarityThreshold = 0.7)
            promptAugmenter = SystemPromptAugmenter()
        }
    }
    ```

=== "Java"

    ```java
    var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
        .withStorage(myVectorDbStorage)
        .withSearchStrategy(
            SearchStrategy.builder().similarity().withTopK(3).withSimilarityThreshold(0.7).build()
        )
        .withPromptAugmenter(PromptAugmenter.builder().system().build())
        .build();
    ```

### Prompt Augmenters

| Augmenter | Behavior |
|---|---|
| `SystemPromptAugmenter()` | Inserts context as a system message at the start of the prompt (no-op if there is no system message) |
| `UserPromptAugmenter()` | Inserts context as a separate user message before the last user message |
| `PromptAugmenter { prompt, context -> ... }` | Custom augmentation via lambda |

### Query Extractors

By default, the retrieval flow uses the last user message as the search query. You can customize this by providing a `QueryExtractor`:

| Extractor | Behavior |
|---|---|
| `LastUserMessageQueryExtractor()` | Uses the content of the last user message (default) |
| `QueryExtractor { prompt -> ... }` | Custom extraction via lambda |

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    install(LongTermMemory) {
        retrieval {
            storage = myStorage
            queryExtractor = QueryExtractor { prompt ->
                // Combine the last two user messages as the search query
                prompt.messages
                    .filter { it.role == Message.Role.User }
                    .takeLast(2)
                    .joinToString(" ") { it.content }
                    .ifEmpty { null }
            }
        }
    }
    ```

=== "Java"

    ```java
    var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
        .withStorage(myStorage)
        .withQueryExtractor(prompt -> {
            var userMessages = prompt.getMessages().stream()
                .filter(m -> m.getRole() == Message.Role.User)
                .toList();
            if (userMessages.isEmpty()) return null;
            return userMessages.get(userMessages.size() - 1).getContent();
        })
        .build();
    ```

### Search Strategies

| Strategy                                                  | Behavior                 |
|-----------------------------------------------------------|--------------------------|
| `SimilaritySearchStrategy()`                              | Vector similarity semantic search — **default** |
| `query -> new SimilaritySearchRequest(query, 20, 0, 0.0, null)` | Custom search via lambda |

## Ingestion Only

Use ingestion without retrieval to build up a memory storage over time:

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    install(LongTermMemory) {
        ingestion {
            storage = myVectorDbStorage
            namespace = "my-collection"  // optional: scope to a specific namespace/collection
            extractionStrategy = FilteringExtractionStrategy(
                messageRolesToExtract = setOf(Message.Role.User, Message.Role.Assistant)
            )
            timing = IngestionTiming.ON_LLM_CALL
        }
    }
    ```

=== "Java"

    ```java
    var ingestionSettings = new LongTermMemory.IngestionSettingsBuilder()
        .withStorage(myVectorDbStorage)
        .withExtractionStrategy(
            ExtractionStrategy.builder()
                .filtering()
                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                .withLastMessageOnly(false)
                .build()
        )
        .withTiming(IngestionTiming.ON_LLM_CALL)
        .build();
    ```

### Ingestion Timing

| Timing | Behavior |
|---|---|
| `ON_LLM_CALL` | Prompt messages are ingested before each LLM call starts; assistant output is ingested after completion or stream completion. Enables intra-session RAG. |
| `ON_AGENT_COMPLETION` | The final accumulated session prompt/history is ingested once at agent completion. |

## Disabling Automatic Behavior

By default, retrieval and ingestion run automatically (before and after LLM calls, respectively). You can disable automatic behavior while still having access to the configured storage and strategies from within strategy nodes:

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    install(LongTermMemory) {
        retrieval {
            storage = myStorage
            enableAutomaticRetrieval = false  // no automatic prompt augmentation
        }
        ingestion {
            storage = myStorage
            enableAutomaticIngestion = false  // no automatic message persistence
        }
    }
    ```

=== "Java"

    ```java
    config.retrieval(
        new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(myStorage)
            .withEnableAutomaticRetrieval(false)
            .build()
    );
    config.ingestion(
        new LongTermMemory.IngestionSettingsBuilder()
            .withStorage(myStorage)
            .withEnableAutomaticIngestion(false)
            .build()
    );
    ```

This gives you three clean modes:

1. **Full automatic** (default): Install the feature, configure storage — retrieval and ingestion work automatically.
2. **Manual only**: Set `enableAutomaticRetrieval = false` / `enableAutomaticIngestion = false` and use storage and strategies in your graph strategy nodes.
3. **Hybrid**: Combine automatic ingestion with manual retrieval (or vice versa).

## Accessing Long-Term Memory from Strategy Nodes

Use `withLongTermMemory { }` inside a strategy node to directly search or add records:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val myNode by node<String, Unit> {
    withLongTermMemory {
        // Manually add records
        val record = MemoryRecord(content = "important fact")
        ingestionStorage?.add(listOf(record), namespace = "my-namespace")

        // Manually search
        val request = SimilaritySearchRequest(queryText = input, limit = 5)
        val results = retrievalStorage?.search(request, namespace = "my-namespace")
    }
}
```

Use `longTermMemory()` to get the feature instance directly:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val myNode by node<String, Unit> {
    val memory = longTermMemory()
    val storage = memory.ingestionStorage
}
```

## Custom Extraction Strategy

Implement `ExtractionStrategy` to control how messages are transformed before storage:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val summarizingExtractor = ExtractionStrategy { messages ->
    messages
        .filter { it.role == Message.Role.Assistant }
        .map { MemoryRecord(content = summarize(it.content)) }
}

install(LongTermMemory) {
    ingestion {
        storage = myStorage
        extractionStrategy = summarizingExtractor
    }
}
```

## Implementing Custom Storage

Implement `SearchStorage` and/or `WriteStorage` to connect to your vector database:

```kotlin
class MyVectorDbStorage : SearchStorage<TextDocument, SearchRequest>, WriteStorage<TextDocument> {
    override suspend fun search(
        request: SearchRequest, namespace: String?
    ): List<SearchResult<TextDocument>> {
        // Query your vector DB
    }

    override suspend fun add(
        records: List<TextDocument>, namespace: String?
    ) {
        // Upsert into your vector DB
    }
}
```

For testing, use the built-in `InMemoryRecordStorage` which keeps records in memory. It accepts both `KeywordSearchRequest` and `SimilaritySearchRequest`, but implements both as simple case-insensitive substring matching (no vector embeddings).
