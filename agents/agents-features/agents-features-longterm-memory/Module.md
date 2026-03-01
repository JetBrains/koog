# Module agents-features-longterm-memory

Provides the `LongTermMemory` feature for AI agents, enabling persistent storage and retrieval of memory records (documents) across agent runs via vector databases or other storage backends. Supports Retrieval-Augmented Generation (RAG) and message ingestion as two independently configurable flows.

### Overview

The agents-features-longterm-memory module adds long-term memory capabilities to Koog AI agents:

- **Retrieval (RAG)**: Searches a memory store for context relevant to the user's query and augments the LLM prompt before each call
- **Ingestion**: Extracts and persists conversation messages into a memory store for future retrieval
- **Flexible storage**: Plug any backend via `RetrievalStorage` / `IngestionStorage` interfaces; an in-memory `InMemoryRecordStorage` is included for testing
- **Configurable timing**: Ingest per-LLM-call or on agent completion
- **Prompt augmentation modes**: System prompt or user prompt or custom implementation

### Key Components

| Component                                                                                                                    | Description                                                            |
|------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| [`LongTermMemory`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/feature/LongTermMemory.kt)                            | Agent feature with DSL config for retrieval & ingestion                |
| [`RetrievalStorage`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/RetrievalStorage.kt)                      | Interface for searching memory records                                 |
| [`IngestionStorage`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/ingestion/IngestionStorage.kt)                      | Interface for adding memory records                                    |
| [`SearchStrategy`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/SearchStrategy.kt)              | Converts user query into a `SearchRequest` (similarity or keyword)     |
| [`MemoryRecordExtractor`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/ingestion/extraction/MemoryRecordExtractor.kt) | Transforms messages into `MemoryRecord`s for storage                   |
| [`PromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/PromptAugmenter.kt)           | Interface for augmenting prompts with relevant context                 |
| [`SystemPromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/SystemPromptAugmenter.kt)               | Inserts retrieved context as a system message                          |
| [`UserPromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/UserPromptAugmenter.kt)                   | Inserts retrieved context as a user message                            |
| [`InMemoryRecordStorage`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/storage/InMemoryRecordStorage.kt)              | In-memory storage implementing both retrieval and ingestion interfaces |
