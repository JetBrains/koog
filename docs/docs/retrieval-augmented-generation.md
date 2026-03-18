# Retrieval-augmented generation (RAG)

Koog provides building blocks for retrieval-augmented generation (RAG): embedding text, storing embedded documents, and retrieving the most relevant results for a query.

This page focuses on what is available in the current `rag` module and how to use it safely.

## What Koog provides today

The current RAG support is split into two modules:

- `rag-base`: common abstractions for retrieval, storage, search requests, filtering, and file/document providers
- `vector-storage`: local implementations that combine document embedding with vector storage

Today, the most complete out-of-the-box flow is:

1. Create an `Embedder`
2. Create a `DocumentEmbedder`
3. Create a `VectorStore`
4. Add documents
5. Search with `SimilaritySearchRequest`

## Usage with local embeddings

Use this approach when you want to build a local or prototype RAG pipeline inside Koog using the current built-in implementations.

### Main types

- `LLMEmbedder`: embeds text using an embedding-capable model
- `JVMTextDocumentEmbedder`: reads JVM file-based documents and embeds their text content
- `EmbeddingStore`: combines a document embedder with a vector storage backend
- `InMemoryVectorStorage`: stores vectors in memory
- `SimilaritySearchRequest`: performs similarity search by query text

### Minimal example

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.rag.base.storage.SimilaritySearchRequest
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import ai.koog.rag.vector.store.EmbeddingStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val embedder = LLMEmbedder(
    client = OllamaClient(),
    model = OllamaModels.Embeddings.NOMIC_EMBED_TEXT
)

val documentEmbedder = JVMTextDocumentEmbedder(embedder)

val vectorStore = EmbeddingStore(
    embedder = documentEmbedder,
    storage = InMemoryVectorStorage()
)

vectorStore.add(
    listOf(
        Path.of("./docs/doc1.txt"),
        Path.of("./docs/doc2.txt"),
        Path.of("./docs/doc3.txt")
    )
)

val results = vectorStore.search(
    SimilaritySearchRequest(
        queryText = "How do I reset my password?",
        limit = 3
    )
)

results.forEach { result ->
    println("${result.document}: ${result.score.value}")
}
```
<!--- KNIT example-retrieval-augmented-generation-01.kt -->

### Available local implementations

- `InMemoryVectorStorage`: in-memory vector storage
- `FileVectorStorage`: file-based vector storage
- `JVMFileVectorStorage`: JVM file-based vector storage
- `TextDocumentEmbedder`: generic document-to-text embedder
- `JVMTextDocumentEmbedder`: JVM text document embedder
- `EmbeddingStore`: generic embedding + storage composition
- `InMemoryDocumentEmbeddingStore`: in-memory embedding store
- `FileDocumentEmbeddingStore`: file-based embedding store
- `JVMFileDocumentEmbeddingStore`: JVM file-based embedding store
- `TextFileDocumentEmbeddingStore`: file-based store for text documents
- `JVMFileEmbeddingStore`: JVM file-based store for text documents

## Current limitations

The current built-in flow is useful for local and reference implementations, but it is not yet a full production RAG platform.

Important limitations:

- the built-in examples center on similarity search only
- there is no built-in chunking pipeline in the `rag` module
- metadata-rich production record modeling is still limited
- production vector database integrations are not provided in the current `rag` module

If you are building a custom backend, start from `rag-base` abstractions and implement your own storage adapter.

## Usage with production vector databases

This section will document recommended integrations for production vector databases such as Pinecone, Weaviate, pgvector, and Milvus.

To be added later.

## Choosing where to start

Use `vector-storage` if:

- you want a local RAG prototype
- you want a simple reference implementation
- you want to experiment with embedding and retrieval flow inside Koog

Use `rag-base` if:

- you are building your own storage backend
- you want to integrate an external vector database
- you want to reuse the abstractions in another Koog module

## See also

- [Embeddings](embeddings.md)
