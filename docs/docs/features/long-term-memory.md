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
                searchStrategy = KeywordSearchStrategy(topK = 5)
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
                    .withSearchStrategy(query ->
                        new KeywordSearchRequest(query, 15, 0.5, null)
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

### Search Strategies

| Strategy                                                  | Behavior                 |
|-----------------------------------------------------------|--------------------------|
| `KeywordSearchStrategy()`                                 | Full-text/lexical keyword matching |
| `SimilaritySearchStrategy()`                              | Vector similarity semantic search |
| `query -> new KeywordSearchRequest(query, 20, 0.0, null)` | Custom search via lambda |

## Ingestion Only

Use ingestion without retrieval to build up a memory storage over time:

=== "Kotlin"

    ```kotlin
    @OptIn(ExperimentalAgentsApi::class)
    install(LongTermMemory) {
        ingestion {
            storage = myVectorDbStorage
            namespace = "my-collection"  // optional: scope to a specific namespace/collection
            extractor = FilteringMemoryRecordExtractor(
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
        .withExtractor(
            MemoryRecordExtractor.builder()
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
| `ON_LLM_CALL` | Ingests messages on each LLM call/stream (enables intra-session RAG) |
| `ON_AGENT_COMPLETION` | Ingests all messages at once when the agent run completes |

## Accessing Long-Term Memory from Strategy Nodes

Use `withLongTermMemory { }` inside a strategy node to directly search or add records:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val myNode by node<String, Unit> {
    withLongTermMemory {
        // Manually add records
        val record = MemoryRecord(content = "important fact")
        this.getIngestionStorage()?.add(listOf(record), ingestionSettings?.namespace)

        // Manually search
        val request = SimilaritySearchRequest(query = input, limit = 5)
        val results = this.getRetrievalStorage()?.search(request, retrievalSettings?.namespace)
    }
}
```

Use `longTermMemory()` to get the feature instance directly:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val myNode by node<String, Unit> {
    val memory = longTermMemory()
    val storage = memory.getIngestionStorage()
}
```

## Custom Memory Record Extractor

Implement `MemoryRecordExtractor` to control how messages are transformed before storage:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val summarizingExtractor = MemoryRecordExtractor { messages ->
    messages
        .filter { it.role == Message.Role.Assistant }
        .map { MemoryRecord(content = summarize(it.content)) }
}

install(LongTermMemory) {
    ingestion {
        storage = myStorage
        extractor = summarizingExtractor
    }
}
```

## Implementing Custom Storage

Implement `RetrievalStorage` and/or `IngestionStorage` to connect to your vector database:

```kotlin
class MyVectorDbStorage : RetrievalStorage, IngestionStorage {
    override suspend fun search(
        request: SearchRequest, namespace: String?
    ): List<SearchResult> {
        // Query your vector DB
    }

    override suspend fun add(
        records: List<MemoryRecord>, namespace: String?
    ) {
        // Upsert into your vector DB
    }
}
```

For testing, use the built-in `InMemoryRecordStorage` which keeps records in memory with keyword-based search.
