# Long Term Memory Feature (Experimental)

The `LongTermMemory` feature adds persistent memory to Koog AI agents via two independent group of settings:
- **Retrieval** — augments LLM prompts with relevant context from a memory storage (Retrieval-Augmented Generation or RAG)
- **Ingestion** — persists conversation messages into a memory storage for future retrieval

## Quick Start

> **Note:** `LongTermMemory` is an experimental API. Annotate your code with `@OptIn(ExperimentalAgentsApi::class)` or add `@file:OptIn(ExperimentalAgentsApi::class)` at the top of your file.

```kotlin
@OptIn(ExperimentalAgentsApi::class)
val storage = InMemoryRecordStorage() // or your vector DB adapter

@OptIn(ExperimentalAgentsApi::class)
val agent = AIAgent(
    promptExecutor = executor,
    strategy = singleRunStrategy(),
    agentConfig = agentConfig,
    toolRegistry = ToolRegistry.EMPTY
) {
    install(LongTermMemory) {
        retrieval {
            storage = storage
            searchStrategy = KeywordSearchStrategy(topK = 5)
        }
        ingestion {
            storage = storage
        }
    }
}

agent.run("What did we discuss yesterday?")
```

## Retrieval Only (RAG)

Use retrieval without ingestion when you have a pre-populated knowledge base:

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

### Prompt Augmenters

| Augmenter | Behavior |
|---|---|
| `SystemPromptAugmenter()` | Inserts context as a system message at the start of the prompt (no-op if there is no system message) |
| `UserPromptAugmenter()` | Inserts context as a separate user message before the last user message |
| `PromptAugmenter { prompt, context -> ... }` | Custom augmentation via lambda |

## Ingestion Only

Use ingestion without retrieval to build up a memory storage over time:

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

## Custom Search Request

Use `searchStrategy` with a lambda to control how user queries are turned into search requests:

```kotlin
@OptIn(ExperimentalAgentsApi::class)
install(LongTermMemory) {
    retrieval {
        storage = myStorage
        searchStrategy = SearchStrategy { query ->
            SimilaritySearchRequest(query = rephrase(query), limit = 10)
        }
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
