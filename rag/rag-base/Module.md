# Module rag-base

A foundational module that provides core interfaces for document storage and retrieval in Retrieval-Augmented Generation (RAG) systems.

### Overview

The rag-base module defines the fundamental abstractions for working with document storage in RAG applications. It includes:

- The `ReadStorage` interface for reading documents by their identifiers
- The `IngestionStorage` interface for adding and updating documents
- The `DeletionStorage` interface for deleting documents by their identifiers
- The `RetrievalStorage` interface that provides ranking capabilities based on query relevance, returning `SearchResult` items with scores
- The `SearchRequest` interface and `SimilaritySearchRequest` implementation for defining search parameters
- The `DocumentWithPayload` data class for associating documents with metadata or payload
- Support for generic document types, allowing flexibility in the types of documents that can be stored and retrieved

This module serves as the base for all RAG submodules (e.g., rag-vector) by providing a consistent API for document operations. It is designed to be implementation-agnostic, allowing different storage backends to be used interchangeably while maintaining a consistent interface for document management and retrieval.

### Example of usage

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
