# Module embeddings:embeddings-local

A module that provides text and code embedding functionality using Ollama for local embedding generation.

### Overview

The embeddings-local module implements the Embedder interface from embeddings-base to provide local embedding generation using Ollama. It includes:

- The `OllamaEmbedder` class that implements the Embedder interface using Ollama models
- The `OllamaEmbeddingModels` object that defines various embedding models with different characteristics:
  - NOMIC_EMBED_TEXT: A general-purpose embedding model (137M parameters, 768 dimensions)
  - ALL_MINI_LM: A lightweight and efficient model (33M parameters, 384 dimensions)
  - MULTILINGUAL_E5: A model supporting 100+ languages (300M parameters, 768 dimensions)
  - BGE_LARGE: A high-quality English text model (335M parameters, 1024 dimensions)
  - MXBAI_EMBED_LARGE: Another high-dimensional embedding model

This module allows for efficient generation of embeddings locally without requiring external API calls, making it suitable for privacy-sensitive applications or environments with limited internet connectivity.

### Example of usage

```kotlin
// Initialize the Ollama client
val baseUrl = "http://localhost:11434"
val model = OllamaEmbeddingModels.NOMIC_EMBED_TEXT
val client = OllamaClient(baseUrl)

// Create an embedder
val embedder = OllamaEmbedder(client, model)

// Generate embeddings for two text snippets
val text1 = "This is a sample code snippet that calculates factorial."
val text2 = "This code implements a recursive algorithm."

// Embed the text snippets
val embedding1 = embedder.embed(text1)
val embedding2 = embedder.embed(text2)

// Calculate the difference between the embeddings
val difference = embedder.diff(embedding1, embedding2)
println("Semantic difference: $difference")

// You can also directly use Vector methods
val similarity = embedding1.cosineSimilarity(embedding2)
println("Cosine similarity: $similarity")
```
