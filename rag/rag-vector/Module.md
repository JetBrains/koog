# Module rag-vector

A module that provides vector-based document storage and retrieval capabilities for Retrieval-Augmented Generation (RAG) systems.

### Overview

The rag-vector module extends the rag-base module by implementing document storage with vector embeddings. It enables semantic search and similarity-based document retrieval by converting documents into vector representations. Key components include:

- The `VectorStorageBackend` interface for low-level storage of documents with their pre-computed vector embeddings
- The `VectorStorage` interface that combines `WriteStorage`, `SearchStorage`, `DeletionStorage`, and `LookupStorage` into a single user-facing abstraction
- The `DocumentEmbedder` interface for converting documents into vector representations
- The `TextDocumentEmbedder` implementation that works with text documents
- The `EmbeddingStorage` class that implements `VectorStorage` by composing a `DocumentEmbedder` with a `VectorStorageBackend`

This module bridges the gap between raw document storage and semantic search capabilities by leveraging vector embeddings to represent document content. It allows for efficient retrieval of documents based on semantic similarity to queries rather than just keyword matching.

For usage examples, see the [Retrieval-Augmented Generation](../../docs/docs/retrieval-augmented-generation.md) guide.
