# Embeddings

The `embeddings` module provides functionality for generating and comparing embeddings of text and code. Embeddings are vector representations that capture semantic meaning, allowing for efficient similarity comparisons.

## Overview

This module consists of two main components:

1. **embeddings-base**: core interfaces and data structures for embeddings.
2. **embeddings-llm**: implementation using Ollama for local embedding generation.

## Getting started

The following sections include basic examples of how to use embeddings in the following ways:

- With a local embedding models through Ollama
- Using an OpenAI embedding model

### Local embeddings

To use the embedding functionality with a local model, you need to have Ollama installed and running on your system.
For installation and running instructions, refer to the [official Ollama GitHub repository](https://github.com/ollama/ollama).

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() {
    runBlocking {
        // Create an OllamaClient instance
        val client = OllamaClient()
        // Create an embedder
        val embedder = LLMEmbedder(client, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
        // Create embeddings
        val embedding = embedder.embed("This is the text to embed")
        // Print embeddings to the output
        println(embedding)
    }
}
```
<!--- KNIT example-embeddings-01.kt -->

To use an Ollama embedding model, make sure to have the following prerequisites:

- Have [Ollama](https://ollama.com/download) installed and running
- Download an embedding model to your local machine using the following command:
    ```bash
    ollama pull <ollama-model-id>
    ```
    Replace `<ollama-model-id>` with the Ollama identifier of the specific model. For more information about available
embedding models and their identifiers, see [Ollama models overview](#ollama-models-overview).

### Ollama models overview

The following table provides an overview of the available Ollama embedding models.

| Model ID          | Ollama ID         | Parameters | Dimensions | Context Length | Performance                                                           | Tradeoffs                                                          |
|-------------------|-------------------|------------|------------|----------------|-----------------------------------------------------------------------|--------------------------------------------------------------------|
| NOMIC_EMBED_TEXT  | nomic-embed-text  | 137M       | 768        | 8192           | High-quality embeddings for semantic search and text similarity tasks | Balanced between quality and efficiency                            |
| ALL_MINILM        | all-minilm        | 33M        | 384        | 512            | Fast inference with good quality for general text embeddings          | Smaller model size with reduced context length, but very efficient |
| MULTILINGUAL_E5   | zylonai/multilingual-e5-large   | 300M       | 768        | 512            | Strong performance across 100+ languages                              | Larger model size but provides excellent multilingual capabilities |
| BGE_LARGE         | bge-large         | 335M       | 1024       | 512            | Excellent for English text retrieval and semantic search              | Larger model size but provides high-quality embeddings             |
| MXBAI_EMBED_LARGE | mxbai-embed-large | -          | -          | -              | High-dimensional embeddings of textual data                           | Designed for creating high-dimensional embeddings                  |

For more information about these models, see Ollama's [Embedding Models](https://ollama.com/blog/embedding-models)
blog post.

### Choosing a model

Here are some general tips on which Ollama embedding model to select depending on your requirements:

- For general text embeddings, use `NOMIC_EMBED_TEXT`.
- For multilingual support, use `MULTILINGUAL_E5`.
- For maximum quality (at the cost of performance), use `BGE_LARGE`.
- For maximum efficiency (at the cost of some quality), use `ALL_MINILM`.
- For high-dimensional embeddings, use `MXBAI_EMBED_LARGE`.

## OpenAI embeddings

To create embeddings using an OpenAI embedding model, use the `embed` method of an `OpenAILLMClient` instance as shown
in the example below.

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
-->
```kotlin
suspend fun openAIEmbed(text: String) {
    // Get the OpenAI API token from the OPENAI_KEY environment variable
    val token = System.getenv("OPENAI_KEY") ?: error("Environment variable OPENAI_KEY is not set")
    // Create an OpenAILLMClient instance
    val client = OpenAILLMClient(token)
    // Create an embedder
    val embedder = LLMEmbedder(client, OpenAIModels.Embeddings.TextEmbeddingAda002)
    // Create embeddings
    val embedding = embedder.embed(text)
    // Print embeddings to the output
    println(embedding)
}
```
<!--- KNIT example-embeddings-02.kt -->

## AWS Bedrock embeddings

To create embeddings using an AWS Bedrock embedding model, use the `embed` method of an `BedrockLLMClient` instance and your chosen model. Example:

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
-->
```kotlin
suspend fun bedrockEmbed(text: String) {
    // Get AWS credentials from environment/configuration
    val awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID") ?: error("AWS_ACCESS_KEY_ID not set")
    val awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: error("AWS_SECRET_ACCESS_KEY not set")
    // (Optional) AWS_SESSION_TOKEN for temporary credentials
    val awsSessionToken = System.getenv("AWS_SESSION_TOKEN")
    // Create a BedrockLLMClient instance
    val client = BedrockLLMClient(
        identityProvider = StaticCredentialsProvider {
            this.accessKeyId = awsAccessKeyId
            this.secretAccessKey = awsSecretAccessKey
            awsSessionToken?.let { this.sessionToken = it }
        },
        settings = BedrockClientSettings()
    )
    // Create an embedder
    val embedder = LLMEmbedder(client, BedrockModels.Embeddings.AmazonTitanEmbedText)
    // Create embeddings
    val embedding = embedder.embed(text)
    // Print embeddings to the output
    println(embedding)
}
```
<!--- KNIT example-embeddings-03.kt -->

### Supported AWS Bedrock embedding models

| Provider | Model name                   | Model ID                       | Input | Output    | Dimensions | Context Length | Notes                                                                                                 |
|----------|------------------------------|--------------------------------|-------|-----------|------------|----------------|-------------------------------------------------------------------------------------------------------|
| Amazon   | Titan Embeddings G1 - Text   | `amazon.titan-embed-text-v1`   | Text  | Embedding | 1,536      | 8192           | 25+ languages, optimized for retrieval, semantic similarity, clustering; segment long docs for search.|
| Amazon   | Titan Text Embeddings V2     | `amazon.titan-embed-text-v2:0` | Text  | Embedding | 1,024      | 8192           | High-accuracy, flexible dimensions, multilingual (100+); smaller dims save storage, normalized output.|
| Cohere   | Cohere Embed English v3      | `cohere.embed-english-v3`      | Text  | Embedding | 1,024      | 8192           | SOTA English text embeddings for search, retrieval, and understanding text nuances.                   |
| Cohere   | Cohere Embed Multilingual v3 | `cohere.embed-multilingual-v3` | Text  | Embedding | 1,024      | 8192           | Multilingual embeddings, SOTA for search and semantic understanding across languages.                 |

> For the most up-to-date model support, refer to the [AWS Bedrock supported models documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

## Examples

The following examples show how you can use embeddings to compare code with text or other code snippets.

### Code-to-text comparison

Compare code snippets with natural language descriptions to find semantic matches:

<!--- INCLUDE
import ai.koog.embeddings.base.Embedder
-->
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
<!--- KNIT example-embeddings-04.kt -->

### Code-to-code comparison

Compare code snippets to find semantic similarities regardless of syntax differences:

<!--- INCLUDE
import ai.koog.embeddings.base.Embedder
-->
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
<!--- KNIT example-embeddings-05.kt -->

## API documentation

For a complete API reference related to embeddings, see the reference documentation for the following modules:

- [embeddings-base](https://api.koog.ai/embeddings/embeddings-base/ai.koog.embeddings.base/index.html): Provides core interfaces and data structures for representing and comparing text 
and code embeddings.
- [embeddings-llm](https://api.koog.ai/embeddings/embeddings-llm/index.html): Includes implementations for working with local embedding models.
