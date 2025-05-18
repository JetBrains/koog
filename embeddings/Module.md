# Module embeddings

The embeddings module provides functionality for generating and comparing vector representations of text and code for semantic similarity analysis.

### Overview

This module consists of two main components:
- **embeddings-base**: Core interfaces and data structures for embeddings
- **embeddings-local**: Implementation using Ollama for local embedding generation

The module allows for efficient semantic comparison between code snippets and natural language descriptions, as well as between different code implementations.

### Using in your project

Add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog.embeddings:embeddings-base:$code_engine_version")
    implementation("ai.koog.embeddings:embeddings-local:$code_engine_version")
}
```

Note: You need to have Ollama installed and running on your system to use the local embeddings functionality.

### Using in unit tests

For unit tests, you can use the same dependencies as in your main project. Consider mocking the embedder interface for faster test execution if you don't need actual embedding generation.

### Example of usage

```kotlin
// Initialize the Ollama embedder client
val baseUrl = "http://localhost:11434"
val model = OllamaEmbeddingModel.NOMIC_EMBED_TEXT
val client = OllamaEmbedderClient(baseUrl, model)

// Create an embedder
val embedder = OllamaEmbedder(client)

try {
    // Generate embeddings
    val codeEmbedding = embedder.embed(codeSnippet)
    val textEmbedding = embedder.embed(textDescription)

    // Calculate similarity (lower value means more similar)
    val similarity = embedder.diff(codeEmbedding, textEmbedding)

    println("Similarity score: $similarity")
} finally {
    // Clean up
    client.close()
}
```
