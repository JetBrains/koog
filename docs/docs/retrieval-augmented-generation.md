# Retrieval-augmented generation (RAG)

Koog provides building blocks for retrieval-augmented generation (RAG): embedding text, storing embedded documents, and retrieving the most relevant results for a query.

This page focuses on what is available in the current `rag` module and how to use it safely.

## What Koog provides today

The current RAG support is split into two modules:

- `rag-base`: common abstractions for retrieval, storage, search requests, filtering, and file/document providers
- `rag-vector`: local implementations that combine document embedding with vector storage

Today, the most complete out-of-the-box flow is:

1. Create an `Embedder`
2. Create a `DocumentEmbedder`
3. Create a `VectorStorage`
4. Add documents
5. Search with `SimilaritySearchRequest`

## Base storage operations

The `rag-base` module provides the core storage interfaces. Here is an example of using them directly:

```kotlin
// Example of using IngestionStorage and ReadStorage
suspend fun storeAndRetrieveDocuments(
    ingestion: IngestionStorage<TextDocument>,
    reader: ReadStorage<TextDocument>
) {
    // Create documents
    val documents = listOf(
        TextDocument("This is a sample document about artificial intelligence."),
        TextDocument("Another document about machine learning.")
    )

    // Store the documents and get their IDs
    val documentIds = ingestion.add(documents)
    println("Documents stored with IDs: $documentIds")

    // Retrieve the documents using their IDs
    val retrievedDocuments = reader.get(documentIds)
    retrievedDocuments.forEach { doc ->
        println("Retrieved document: ${doc.content}")
    }
}

// Example of using DeletionStorage
suspend fun deleteDocuments(deletion: DeletionStorage) {
    val idsToDelete = listOf("doc1", "doc2")
    val deletedIds = deletion.delete(idsToDelete)
    println("Deleted documents: $deletedIds")
}

// Example of using RetrievalStorage with search
suspend fun findRelevantDocuments(storage: RetrievalStorage<TextDocument>) {
    // Find documents relevant to a query using search
    val query = "What is artificial intelligence?"
    val results = storage.search(SimilaritySearchRequest(queryText = query, limit = 2, minScore = 0.5))

    println("Most relevant documents for query '$query':")
    results.forEach { result ->
        println("- ${result.document.content} (score: ${result.score.value})")
    }
}
```

## Usage with local embeddings

Use this approach when you want to build a local or prototype RAG pipeline inside Koog using the current built-in implementations.

### Main types

- `LLMEmbedder`: embeds text using an embedding-capable model
- `JVMTextDocumentEmbedder`: reads JVM file-based documents and embeds their text content
- `EmbeddingStorage`: combines a document embedder with a vector storage backend
- `InMemoryVectorStorageBackend`: stores vectors in memory
- `SimilaritySearchRequest`: performs similarity search by query text

### Minimal example

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
import ai.koog.rag.vector.backend.InMemoryVectorStorageBackend
import ai.koog.rag.vector.storage.EmbeddingStorage
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

val vectorStore = EmbeddingStorage(
    embedder = documentEmbedder,
    storage = InMemoryVectorStorageBackend()
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

### Vector storage example

The `rag-vector` module provides higher-level abstractions for embedding-based storage and retrieval:

```kotlin
// Example of using EmbeddingStorage with a TextDocumentEmbedder
suspend fun createVectorBasedStorage(): EmbeddingStorage<TextDocument> {
    // This would be replaced with your specific Embedder implementation
    val client: LLMEmbeddingProvider = YourEmbeddingProviderImplementation()
    val model: LLModel = YourEmbeddingModel
    val embedder = LLMEmbedder(client, model)
    val documentEmbedder = TextDocumentEmbedder(documentProvider, embedder)

    // Create a vector storage backend
    val vectorStorageBackend: VectorStorageBackend<TextDocument> = InMemoryVectorStorageBackend()

    // Create the embedding-based storage
    val store = EmbeddingStorage(documentEmbedder, vectorStorageBackend)

    return store
}

// Example of storing and retrieving documents based on semantic similarity
suspend fun findSimilarDocuments(store: EmbeddingStorage<TextDocument>) {
    // Store multiple documents
    store.add(listOf(
        TextDocument("Neural networks are a type of machine learning model."),
        TextDocument("Deep learning uses multiple layers of neural networks."),
        TextDocument("Transformers are a type of neural network architecture."),
        TextDocument("The capital of France is Paris.")
    ))

    // Find documents semantically similar to a query
    val query = "How do artificial neural networks work?"
    val results = store.search(SimilaritySearchRequest(queryText = query, limit = 3, minScore = 0.7))

    println("Documents most similar to query '$query':")
    results.forEach { result ->
        println("- ${result.document.content} (score: ${result.score.value})")
    }
}
```

### Available local implementations

- `InMemoryVectorStorageBackend`: in-memory vector storage backend
- `FileVectorStorageBackend`: file-based vector storage backend
- `JVMFileVectorStorageBackend`: JVM file-based vector storage backend
- `TextDocumentEmbedder`: generic document-to-text embedder
- `JVMTextDocumentEmbedder`: JVM text document embedder
- `EmbeddingStorage`: generic embedding + storage composition
- `InMemoryDocumentEmbeddingStorage`: in-memory embedding storage
- `FileDocumentEmbeddingStorage`: file-based embedding storage
- `JVMFileDocumentEmbeddingStorage`: JVM file-based embedding storage
- `TextFileDocumentEmbeddingStorage`: file-based storage for text documents
- `JVMFileEmbeddingStorage`: JVM file-based storage for text documents

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

Use `rag-vector` if:

- you want a local RAG prototype
- you want a simple reference implementation
- you want to experiment with embedding and retrieval flow inside Koog

Use `rag-base` if:

- you are building your own storage backend
- you want to integrate an external vector database
- you want to reuse the abstractions in another Koog module

## See also

- [Embeddings](embeddings.md)
