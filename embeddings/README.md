# Code Embeddings

The `embeddings` module provides functionality for generating and comparing embeddings of text and code. Embeddings are vector representations that capture semantic meaning, allowing for efficient similarity comparisons.

## Overview

This module consists of two main components:

1. **embeddings-base**: Core interfaces and data structures for embeddings
2. **embeddings-local**: Implementation using Ollama for local embedding generation

## Getting Started

### Installation

Add the following dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog:embeddings-base:$code_engine_version")
    implementation("ai.koog:embeddings-local:$code_engine_version")
}
```

### Basic Usage

To use the embeddings functionality, you need to have Ollama installed and running on your system. 
For installation and running instructions, please refer to the [official Ollama GitHub repository](https://github.com/ollama/ollama).

```kotlin
fun main() {
    // Initialize the Ollama embedder client
    val baseUrl = "http://localhost:11434"
    val model = OllamaEmbeddingModel.NOMIC_EMBED_TEXT
    val client = OllamaEmbedderClient(baseUrl, model)

    // Create an embedder
    val embedder = OllamaEmbedder(client)

    try {
        // Use the embedder
        // ...
    } finally {
        // Clean up
        client.close()
    }
}
```

## Text Comparison Examples

### Code-to-Text Comparison

Compare code snippets with natural language descriptions to find semantic matches:

```kotlin
suspend fun compareCodeToText(embedder: Embedder) { // Embedder type
    // Code snippet
    val code = """
        fun factorial(n: Int): Int {
            return if (n <= 1) 1 else n * factorial(n - 1)
        }
    """.trimIndent()

    // Text descriptions
    val description1 = "A recursive function that calculates the factorial of a number"
    val description2 = "A function that sorts an array of integers"

    // Generate embeddings
    val codeEmbedding = embedder.embed(code)
    val desc1Embedding = embedder.embed(description1)
    val desc2Embedding = embedder.embed(description2)

    // Calculate differences (lower value means more similar)
    val diff1 = embedder.diff(codeEmbedding, desc1Embedding)
    val diff2 = embedder.diff(codeEmbedding, desc2Embedding)

    println("Difference between code and description 1: $diff1")
    println("Difference between code and description 2: $diff2")

    // The code should be more similar to description1 than description2
    if (diff1 < diff2) {
        println("The code is more similar to: '$description1'")
    } else {
        println("The code is more similar to: '$description2'")
    }
}
```

### Code-to-Code Comparison

Compare code snippets to find semantic similarities regardless of syntax differences:

```kotlin
suspend fun compareCodeToCode(embedder: Embedder) { // Embedder type
    // Two implementations of the same algorithm in different languages
    val kotlinCode = """
        fun fibonacci(n: Int): Int {
            return if (n <= 1) n else fibonacci(n - 1) + fibonacci(n - 2)
        }
    """.trimIndent()

    val pythonCode = """
        def fibonacci(n):
            if n <= 1:
                return n
            else:
                return fibonacci(n-1) + fibonacci(n-2)
    """.trimIndent()

    val javaCode = """
        public static int bubbleSort(int[] arr) {
            int n = arr.length;
            for (int i = 0; i < n-1; i++) {
                for (int j = 0; j < n-i-1; j++) {
                    if (arr[j] > arr[j+1]) {
                        int temp = arr[j];
                        arr[j] = arr[j+1];
                        arr[j+1] = temp;
                    }
                }
            }
            return arr;
        }
    """.trimIndent()

    // Generate embeddings
    val kotlinEmbedding = embedder.embed(kotlinCode)
    val pythonEmbedding = embedder.embed(pythonCode)
    val javaEmbedding = embedder.embed(javaCode)

    // Calculate differences
    val diffKotlinPython = embedder.diff(kotlinEmbedding, pythonEmbedding)
    val diffKotlinJava = embedder.diff(kotlinEmbedding, javaEmbedding)

    println("Difference between Kotlin and Python implementations: $diffKotlinPython")
    println("Difference between Kotlin and Java implementations: $diffKotlinJava")

    // The Kotlin and Python implementations should be more similar
    if (diffKotlinPython < diffKotlinJava) {
        println("The Kotlin code is more similar to the Python code")
    } else {
        println("The Kotlin code is more similar to the Java code")
    }
}
```

## API Documentation

### Embedder

The core interface for embedding operations:

```kotlin
interface Embedder {
    /**
     * Embeds the given text into a vector representation.
     */
    suspend fun embed(text: String): Vector

    /**
     * Calculates the difference between two embeddings.
     * Lower values indicate more similar embeddings.
     */
    fun diff(embedding1: Vector, embedding2: Vector): Double
}
```

### Vector

Represents a vector of floating-point values used for embeddings:

```kotlin
data class Vector(val values: List<Float>) {
    /**
     * Returns the dimension (size) of the vector.
     */
    val dimension: Int
        get() = values.size

    /**
     * Calculates the cosine similarity between this vector and another vector.
     */
    fun cosineSimilarity(other: Vector): Double

    /**
     * Calculates the Euclidean distance between this vector and another vector.
     */
    fun euclideanDistance(other: Vector): Double
}
```

### OllamaEmbedderClient

The `OllamaEmbedderClient` provides a client for interacting with the Ollama API:

```kotlin
class OllamaEmbedderClient(
    private val baseUrl: String,
    private val model: OllamaEmbeddingModel,
    private val httpClient: HttpClient = HttpClient { /* ... */ }
) {
    /**
     * Embeds the given text using the Ollama model.
     */
    suspend fun embed(text: String): Vector

    /**
     * Closes the HTTP client.
     */
    fun close()
}
```

### OllamaEmbedder

The `OllamaEmbedder` implements the `Embedder` interface using Ollama:

```kotlin
class OllamaEmbedder(
    private val client: OllamaEmbedderClient
) : Embedder {
    /**
     * Embeds the given text using the Ollama model.
     */
    override suspend fun embed(text: String): Vector

    /**
     * Calculates the difference between two embeddings using cosine similarity.
     */
    override fun diff(embedding1: Vector, embedding2: Vector): Double
}
```

## Ollama Models Overview

The following table provides an overview of the available Ollama embedding models:

| Model ID | Parameters | Dimensions | Context Length | Performance | Tradeoffs |
|----------|------------|------------|---------------|-------------|-----------|
| NOMIC_EMBED_TEXT | 137M | 768 | 8192 | High-quality embeddings for semantic search and text similarity tasks | Balanced between quality and efficiency |
| ALL_MINILM | 33M | 384 | 512 | Fast inference with good quality for general text embeddings | Smaller model size with reduced context length, but very efficient |
| MULTILINGUAL_E5 | 300M | 768 | 512 | Strong performance across 100+ languages | Larger model size but provides excellent multilingual capabilities |
| BGE_LARGE | 335M | 1024 | 512 | Excellent for English text retrieval and semantic search | Larger model size but provides high-quality embeddings |
| MXBAI_EMBED_LARGE | - | - | - | High-dimensional embeddings of textual data | Designed for creating high-dimensional embeddings |

For more information about these models, see the [Ollama Embedding Models blog post](https://ollama.com/blog/embedding-models).

## Choosing the Right Model

- For general text embeddings, use `NOMIC_EMBED_TEXT`
- For multilingual support, use `MULTILINGUAL_E5`
- For maximum quality (at the cost of performance), use `BGE_LARGE`
- For maximum efficiency (at the cost of some quality), use `ALL_MINILM`
- For high-dimensional embeddings, use `MXBAI_EMBED_LARGE`
