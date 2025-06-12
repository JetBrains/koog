# Module rag-base

A foundational module that provides core interfaces for document storage and retrieval in Retrieval-Augmented Generation (RAG) systems.

### Overview

The rag-base module defines the fundamental abstractions for working with document storage in RAG applications. It includes:

- The `DocumentStorage` interface that defines core operations for storing, reading, and deleting documents
- The `DocumentStorageWithPayload` interface that extends document storage with support for associated metadata or payload
- The `RankedDocumentStorage` interface that extends document storage with ranking capabilities based on query relevance
- The `TextDocumentReader` interface for transforming documents into text representation
- Support for generic document types, allowing flexibility in the types of documents that can be stored and retrieved

This module serves as the base for all RAG submodules (e.g., rag-vector-storage) by providing a consistent API for document operations. It is designed to be implementation-agnostic, allowing different storage backends to be used interchangeably while maintaining a consistent interface for document management and retrieval.

### Example of usage

```kotlin
// Example of using DocumentStorage
suspend fun storeAndRetrieveDocument(storage: DocumentStorage<TextDocument>) {
    // Create a document
    val document = TextDocument("This is a sample document about artificial intelligence.")

    // Store the document and get its ID
    val documentId = storage.store(document)
    println("Document stored with ID: $documentId")

    // Retrieve the document using its ID
    val retrievedDocument = storage.read(documentId)
    println("Retrieved document: ${retrievedDocument?.content}")

    // Delete the document
    val deleted = storage.delete(documentId)
    println("Document deleted: $deleted")
}

// Example of using DocumentStorageWithPayload
suspend fun storeAndRetrieveDocumentWithMetadata(storage: DocumentStorageWithPayload<TextDocument, DocumentMetadata>) {
    // Create a document and its metadata
    val document = TextDocument("This is a document about machine learning.")
    val metadata = DocumentMetadata(author = "John Doe", creationDate = "2023-05-15")

    // Store the document with its metadata
    val documentId = storage.store(document, metadata)

    // Retrieve the document with its metadata
    val docWithPayload = storage.readWithPayload(documentId)
    println("Document: ${docWithPayload?.document?.content}")
    println("Author: ${docWithPayload?.payload?.author}")

    // Retrieve just the metadata
    val justMetadata = storage.getPayload(documentId)
    println("Creation date: ${justMetadata?.creationDate}")
}

// Example of using TextDocumentReader
suspend fun extractTextFromDocument(reader: TextDocumentReader<PDFDocument>) {
    // Create a PDF document
    val pdfDocument = PDFDocument("path/to/document.pdf")

    // Extract text from the PDF
    val extractedText = reader.read(pdfDocument)
    println("Extracted text: $extractedText")
}

// Example of using RankedDocumentStorage
suspend fun findRelevantDocuments(storage: RankedDocumentStorage<TextDocument>) {
    // Store multiple documents
    val docId1 = storage.store(TextDocument("Artificial intelligence is transforming industries."))
    val docId2 = storage.store(TextDocument("Machine learning is a subset of AI."))
    val docId3 = storage.store(TextDocument("The weather is nice today."))

    // Find documents relevant to a query
    val query = "What is artificial intelligence?"
    val relevantDocs = storage.mostRelevantDocuments(query, count = 2, similarityThreshold = 0.5)

    println("Most relevant documents for query '$query':")
    relevantDocs.forEach { doc ->
        println("- ${doc.content}")
    }
}
```
