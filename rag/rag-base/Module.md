# Module rag-base

A foundational module that provides core interfaces for document storage and retrieval in Retrieval-Augmented Generation (RAG) systems.

### Overview

The rag-base module defines the fundamental abstractions for working with document storage in RAG applications. It includes:

- The `LookupStorage` interface for looking up documents by their identifiers
- The `WriteStorage` interface for adding and updating documents
- The `DeletionStorage` interface for deleting documents by their identifiers
- The `SearchStorage` interface that provides ranking capabilities based on query relevance, returning `SearchResult` items with scores
- The `SearchRequest` interface and `SimilaritySearchRequest` implementation for defining search parameters
- The `DocumentWithPayload` data class for associating documents with metadata or payload
- Support for generic document types, allowing flexibility in the types of documents that can be stored and retrieved

This module serves as the base for all RAG submodules (e.g., rag-vector) by providing a consistent API for document operations. It is designed to be implementation-agnostic, allowing different storage backends to be used interchangeably while maintaining a consistent interface for document management and retrieval.

For usage examples, see the [Retrieval-Augmented Generation](../../docs/docs/retrieval-augmented-generation.md) guide.
