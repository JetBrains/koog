# Module agents-features-longterm-memory

Provides the `LongTermMemory` feature for AI agents, enabling persistent storage and retrieval of memory records (documents) across agent runs via vector databases or other storage backends. Supports Retrieval-Augmented Generation (RAG) and message ingestion as two independently configurable flows.

### Overview

The agents-features-longterm-memory module adds long-term memory capabilities to Koog AI agents:

- **Retrieval (RAG)**: Searches a memory store for context relevant to the user's query and augments the LLM prompt before each call
- **Ingestion**: Extracts and persists conversation messages into a memory store for future retrieval
- **Flexible storage**: Plug any backend via `SearchStorage` / `WriteStorage` interfaces from the `rag-base` module; an in-memory `InMemoryRecordStorage` is included for testing
- **Configurable timing**: Ingest per-LLM-call or on agent completion
- **Prompt augmentation modes**: System prompt or user prompt or custom implementation

### Key Components

| Component                                                                                                                      | Description                                                            |
|--------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| [`LongTermMemory`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/feature/LongTermMemory.kt)                              | Agent feature with DSL config for retrieval & ingestion                |
| [`SearchStorage`](../../../../../../rag/rag-base/src/commonMain/kotlin/ai/koog/rag/base/storage/SearchStorage.kt)              | Interface for searching memory records (defined in `rag-base`)         |
| [`WriteStorage`](../../../../../../rag/rag-base/src/commonMain/kotlin/ai/koog/rag/base/storage/WriteStorage.kt)                | Interface for adding memory records (defined in `rag-base`)            |
| [`SearchStrategy`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/SearchStrategy.kt)                            | Converts user query into a `SimilaritySearchRequest`; `SimilaritySearchStrategy` is the default and recommended implementation |
| [`QueryExtractor`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/QueryExtractor.kt)                              | Extracts the search query string from a `Prompt` for retrieval         |
| [`LastUserMessageQueryExtractor`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/QueryExtractor.kt)               | Default `QueryExtractor` that uses the last user message content       |
| [`ExtractionStrategy`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/ingestion/extraction/ExtractionStrategy.kt)         | Transforms messages into `TextDocument`s for storage                   |
| [`PromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/PromptAugmenter.kt)             | Interface for augmenting prompts with relevant context                 |
| [`SystemPromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/SystemPromptAugmenter.kt) | Inserts retrieved context as a system message                          |
| [`UserPromptAugmenter`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/retrieval/augmentation/UserPromptAugmenter.kt)     | Inserts retrieved context as a user message                            |
| [`InMemoryRecordStorage`](src/commonMain/kotlin/ai/koog/agents/longtermmemory/storage/InMemoryRecordStorage.kt)                | In-memory storage implementing both retrieval and ingestion interfaces |
