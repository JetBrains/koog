# Module embeddings-base

A foundational module that provides core interfaces and data structures for representing and comparing text and code embeddings.

### Overview

The embeddings-base module defines the fundamental abstractions and data structures for working with text and code embeddings. It includes:

- The `Embedder` interface that defines core operations for embedding text and comparing embeddings
- The `Vector` class that represents embeddings as lists of floating-point values
- Utility methods for calculating similarity between vectors using cosine similarity and Euclidean distance
- Numerical stability improvements through the Kahan summation algorithm

This module serves as the foundation for all embedding operations in the project, providing a consistent API for generating and comparing embeddings regardless of the underlying implementation. It is designed to be implementation-agnostic, allowing different embedding models to be used interchangeably.

### Example of usage

```kotlin
// Example of using the Embedder interface and Vector class
suspend fun compareTextSimilarity(embedder: Embedder) {
    // Sample texts to compare
    val text1 = "This is a sample text about artificial intelligence."
    val text2 = "AI is transforming how we interact with computers."
    val text3 = "The weather is nice today."

    // Generate embeddings for each text
    val embedding1 = embedder.embed(text1)
    val embedding2 = embedder.embed(text2)
    val embedding3 = embedder.embed(text3)

    // Calculate differences between embeddings
    val diff1_2 = embedder.diff(embedding1, embedding2)
    val diff1_3 = embedder.diff(embedding1, embedding3)

    println("Difference between text1 and text2: $diff1_2")
    println("Difference between text1 and text3: $diff1_3")

    // Compare which texts are more semantically similar
    if (diff1_2 < diff1_3) {
        println("Text1 is more similar to Text2 than to Text3")
    } else {
        println("Text1 is more similar to Text3 than to Text2")
    }

    // You can also directly use Vector methods for more control
    val similarity = embedding1.cosineSimilarity(embedding2)
    val distance = embedding1.euclideanDistance(embedding3)

    println("Cosine similarity between text1 and text2: $similarity")
    println("Euclidean distance between text1 and text3: $distance")
}
```
