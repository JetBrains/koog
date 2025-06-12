# Module embeddings-llm

A module that provides functionality for generating and comparing embeddings using remote LLM services.

### Overview

This module implements the Embedder interface from embeddings-base to generate vector representations of text using remote LLM services. It supports any LLM client that implements the LLMClientWithEmbeddings interface, allowing for flexible integration with different LLM providers.

The module provides:
- Text embedding generation through remote LLM APIs
- Semantic comparison between embeddings using cosine similarity
- A consistent interface that works across different LLM providers

### Example of usage

```kotlin
// Initialize the LLM client with embeddings capability
val client: LLMClientWithEmbeddings = YourLLMClientImplementation()
val model: LLModel = YourLLMModel.SOME_MODEL

// Create the embedder
val embedder = LLMEmbedder(client, model)

// Generate embeddings
val textEmbedding = embedder.embed("Sample text to embed")
val codeEmbedding = embedder.embed("function calculateSum(a, b) { return a + b; }")

// Calculate similarity (lower value means more similar)
val similarity = embedder.diff(textEmbedding, codeEmbedding)

println("Similarity score: $similarity")
```
