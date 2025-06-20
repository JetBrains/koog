# Module vector-storage

A module that provides vector-based document storage and retrieval capabilities for Retrieval-Augmented Generation (RAG) systems.

### Overview

The vector-storage module extends the rag-base module by implementing document storage with vector embeddings. It enables semantic search and similarity-based document retrieval by converting documents into vector representations. Key components include:

- The `VectorStorage` interface that extends `DocumentStorageWithPayload` to store documents with their vector embeddings
- The `DocumentEmbedder` interface for converting documents into vector representations
- The `TextDocumentEmbedder` implementation that works with text documents
- The `EmbeddingBasedDocumentStorage` class that implements `RankedDocumentStorage` using vector embeddings for similarity ranking

This module bridges the gap between raw document storage and semantic search capabilities by leveraging vector embeddings to represent document content. It allows for efficient retrieval of documents based on semantic similarity to queries rather than just keyword matching.

### Example of usage

```kotlin
// Example of using VectorStorage with a TextDocumentEmbedder
suspend fun createVectorBasedStorage() {
    // Create a document embedder
    val textReader = object : TextDocumentReader<TextDocument> {
        override suspend fun read(document: TextDocument): String = document.content
    }
    // This would be replaced with your specific Embedder implementation
    val client: LLMEmbeddingProvider = YourEmbeddingProviderImplementation()
    val model: LLModel = YourEmbeddingModel
    val embedder = LLMEmbedder(client, model)
    val documentEmbedder = TextDocumentEmbedder(textReader, embedder)

    // Create a vector storage implementation
    // This would be replaced with your specific VectorStorage implementation
    val vectorStorage: VectorStorage<TextDocument> = YourVectorStorageImplementation()

    // Create the embedding-based document storage
    val storage = EmbeddingBasedDocumentStorage(documentEmbedder, vectorStorage)

    return storage
}

// Example of storing and retrieving documents based on semantic similarity
suspend fun findSimilarDocuments(storage: RankedDocumentStorage<TextDocument>) {
    // Store multiple documents
    storage.store(TextDocument("Neural networks are a type of machine learning model."))
    storage.store(TextDocument("Deep learning uses multiple layers of neural networks."))
    storage.store(TextDocument("Transformers are a type of neural network architecture."))
    storage.store(TextDocument("The capital of France is Paris."))

    // Find documents semantically similar to a query
    val query = "How do artificial neural networks work?"
    val similarDocs = storage.mostRelevantDocuments(query, count = 3, similarityThreshold = 0.7)

    println("Documents most similar to query '$query':")
    similarDocs.forEach { doc ->
        println("- ${doc.content}")
    }
}
```
