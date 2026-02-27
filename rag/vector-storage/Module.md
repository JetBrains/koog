# Module vector-storage

A module that provides vector-based document storage and retrieval capabilities for Retrieval-Augmented Generation (RAG) systems.

### Overview

The vector-storage module extends the rag-base module by implementing document storage with vector embeddings. It enables semantic search and similarity-based document retrieval by converting documents into vector representations. Key components include:

- The `VectorStorage` interface for low-level storage of documents with their pre-computed vector embeddings
- The `VectorStore` interface that combines `IngestionStorage`, `RetrievalStorage`, `DeletionStorage`, and `ReadStorage` into a single user-facing abstraction
- The `DocumentEmbedder` interface for converting documents into vector representations
- The `TextDocumentEmbedder` implementation that works with text documents
- The `EmbeddingVectorStore` class that implements `VectorStore` by composing a `DocumentEmbedder` with a `VectorStorage`

This module bridges the gap between raw document storage and semantic search capabilities by leveraging vector embeddings to represent document content. It allows for efficient retrieval of documents based on semantic similarity to queries rather than just keyword matching.

### Example of usage

```kotlin
// Example of using VectorStore with a TextDocumentEmbedder
suspend fun createVectorBasedStorage() {
    // This would be replaced with your specific Embedder implementation
    val client: LLMEmbeddingProvider = YourEmbeddingProviderImplementation()
    val model: LLModel = YourEmbeddingModel
    val embedder = LLMEmbedder(client, model)
    val documentEmbedder = TextDocumentEmbedder(documentProvider, embedder)

    // Create a vector storage implementation
    val vectorStorage: VectorStorage<TextDocument> = InMemoryVectorStorage()

    // Create the embedding-based vector store
    val store = EmbeddingVectorStore(documentEmbedder, vectorStorage)

    return store
}

// Example of storing and retrieving documents based on semantic similarity
suspend fun findSimilarDocuments(store: VectorStore<TextDocument>) {
    // Store multiple documents
    store.add(listOf(
        TextDocument("Neural networks are a type of machine learning model."),
        TextDocument("Deep learning uses multiple layers of neural networks."),
        TextDocument("Transformers are a type of neural network architecture."),
        TextDocument("The capital of France is Paris.")
    ))

    // Find documents semantically similar to a query
    val query = "How do artificial neural networks work?"
    val results = store.search(SimilaritySearchRequest(query, limit = 3, similarityThreshold = 0.7))

    println("Documents most similar to query '$query':")
    results.forEach { result ->
        println("- ${result.document.content} (similarity: ${result.similarity})")
    }
}
```
