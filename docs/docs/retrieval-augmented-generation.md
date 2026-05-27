---
status: beta
---

# Retrieval-augmented generation (RAG)

--8<-- "versioning-snippets.md:beta"

Koog provides building blocks for retrieval-augmented generation (RAG): embedding text, storing embedded documents, and retrieving the most relevant results for a query.

This page focuses on what is available in the current `rag` module and how to use it.

## What Koog provides today

The current RAG support is split into two modules:

- `rag-base`: common abstractions for retrieval, storage, search requests, filtering, and file/document providers
- `rag-vector`: local implementations that combine document embedding with vector storage

## Embedding and retrieving documents with EmbeddingStorage

The most complete out-of-the-box RAG flow uses `EmbeddingStorage` from the `rag-vector` module. It combines a `DocumentEmbedder` (which converts documents to vectors) with a `VectorStorageBackend` (which persists the vectors).

The steps are:

1. Create an `Embedder` backed by an embedding model (Ollama or OpenAI).
2. Create a `JVMTextDocumentEmbedder` that reads file content and delegates to the embedder.
3. Create an `EmbeddingStorage` with an in-memory or file-based backend.
4. Add documents with `add()`.
5. Search with `search(SimilaritySearchRequest(...))`.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.embeddings.local.LLMEmbedder
    import ai.koog.prompt.executor.ollama.client.OllamaClient
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import ai.koog.rag.base.storage.search.SimilaritySearchRequest
    import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
    import ai.koog.rag.vector.backend.InMemoryVectorStorageBackend
    import ai.koog.rag.vector.storage.EmbeddingStorage
    import kotlinx.coroutines.runBlocking
    import java.nio.file.Path

    fun main() {
        runBlocking {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    // 1. Create an embedder backed by a local Ollama model
    val embedder = LLMEmbedder(
        client = OllamaClient(),
        model = OllamaModels.Embeddings.NOMIC_EMBED_TEXT
    )

    // 2. Create a JVM document embedder that reads files and embeds their text
    val documentEmbedder = JVMTextDocumentEmbedder(embedder)

    // 3. Create an EmbeddingStorage with an in-memory backend
    val storage = EmbeddingStorage(
        embedder = documentEmbedder,
        storage = InMemoryVectorStorageBackend()
    )

    // 4. Add documents to the storage
    storage.add(
        listOf(
            Path.of("./docs/faq.txt"),
            Path.of("./docs/pricing.txt"),
            Path.of("./docs/getting-started.txt")
        )
    )

    // 5. Search for the most relevant documents
    val results = storage.search(
        SimilaritySearchRequest(
            queryText = "How do I reset my password?",
            limit = 3,
            minScore = 0.5
        )
    )

    results.forEach { result ->
        println("${result.document} (score: ${result.score.value})")
    }
    ```
    <!--- KNIT example-retrieval-augmented-generation-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ```
    <!--- KNIT example-retrieval-augmented-generation-java-01.java -->

## Providing relevance search as an agent tool (in agentic RAG)

Instead of injecting all retrieved documents into the prompt upfront, you can expose the RAG storage as a tool that the agent calls on demand. This gives the agent control over when and what to search for.

The example below wraps a `SearchStorage` (the base search interface that `EmbeddingStorage` implements) in a function annotated with `@Tool` and `@LLMDescription`, then registers it in a `ToolRegistry` for the agent to use.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.agents.core.tools.annotations.Tool
    import ai.koog.agents.core.tools.reflect.asTool
    import ai.koog.embeddings.local.LLMEmbedder
    import ai.koog.prompt.executor.ollama.client.OllamaClient
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import ai.koog.rag.base.storage.SearchStorage
    import ai.koog.rag.base.storage.search.SimilaritySearchRequest
    import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
    import ai.koog.rag.vector.backend.InMemoryVectorStorageBackend
    import ai.koog.rag.vector.storage.EmbeddingStorage
    import kotlinx.coroutines.runBlocking
    import java.nio.file.Files
    import java.nio.file.Path

    // Create the RAG storage
    val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
    val documentEmbedder = JVMTextDocumentEmbedder(embedder)
    val ragStorage: SearchStorage<Path, SimilaritySearchRequest> = EmbeddingStorage(documentEmbedder, InMemoryVectorStorageBackend())

    const val apiKey = "apikey"
    -->
    <!--- SUFFIX
    -->
    ```kotlin
    // Define a tool that searches the RAG storage
    @Tool
    @LLMDescription("Search the knowledge base for documents relevant to a query. Returns the content of the most relevant documents.")
    suspend fun searchKnowledgeBase(
        @LLMDescription("The search query describing what information you need")
        query: String,
        @LLMDescription("Maximum number of documents to return")
        count: Int
    ): String {
        val results = ragStorage.search(
            SimilaritySearchRequest(
                queryText = query,
                limit = count,
                minScore = 0.5
            )
        )

        if (results.isEmpty()) {
            return "No relevant documents found for: $query"
        }

        val response = StringBuilder("Found ${results.size} relevant documents:\n\n")
        results.forEachIndexed { index, result ->
            val content = Files.readString(result.document)
            response.append("Document ${index + 1}: ${result.document.fileName}")
            response.append(" (score: ${"%.2f".format(result.score.value)})\n")
            response.append("Content: $content\n\n")
        }
        return response.toString()
    }

    fun main() {
        runBlocking {
            // Register the search tool and create an agent
            val tools = ToolRegistry {
                tool(::searchKnowledgeBase.asTool())
            }

            val agent = AIAgent(
                toolRegistry = tools,
                promptExecutor = simpleOpenAIExecutor(apiKey),
                llmModel = OpenAIModels.Chat.GPT4o
            )

            val response = agent.run("What is your refund policy?")
            println("Agent response: $response")
        }
    }
    ```
    <!--- KNIT example-retrieval-augmented-generation-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ```
    <!--- KNIT example-retrieval-augmented-generation-java-02.java -->

With this approach, the agent decides when to call the search tool based on the user's query. This is useful when the agent handles diverse requests and only some of them require knowledge base lookups.

## Available implementations

### Vector storage backends

- `InMemoryVectorStorageBackend`: stores vectors in memory; suitable for testing and prototypes
- `FileVectorStorageBackend`: persists vectors to disk for durability across restarts
- `JVMFileVectorStorageBackend`: JVM-specific file-based backend using `java.nio.file.Path`

### Document embedders

- `TextDocumentEmbedder`: generic document-to-text embedder parameterized by document and path types
- `JVMTextDocumentEmbedder`: JVM-specific embedder that reads files from `java.nio.file.Path`

### Combined storage implementations

- `EmbeddingStorage`: composes any `DocumentEmbedder` with any `VectorStorageBackend`
- `InMemoryDocumentEmbeddingStorage`: convenience shortcut for `EmbeddingStorage` + `InMemoryVectorStorageBackend`
- `FileDocumentEmbeddingStorage`: convenience shortcut for `EmbeddingStorage` + `FileVectorStorageBackend`
- `JVMFileDocumentEmbeddingStorage`: JVM file-based embedding storage
- `TextFileDocumentEmbeddingStorage`: file-based storage for text documents
- `JVMFileEmbeddingStorage`: JVM file-based storage for text documents

## Current limitations

The built-in flow is useful for local and reference implementations, but it is not yet a full production RAG platform.

Important limitations:

- the built-in implementations support similarity search only
- there is no built-in chunking pipeline in the `rag` module
- metadata-rich production record modeling is still limited
- production vector database integrations (Pinecone, Weaviate, pgvector, Milvus) are not provided in the current `rag` module

If you are building a custom backend, start from `rag-base` abstractions and implement your own storage adapter.

## Choosing where to start

Use `rag-vector` if:

- you want a local RAG prototype
- you want a simple reference implementation
- you want to experiment with embedding and retrieval flow inside Koog

Use `rag-base` if:

- you are building your own storage backend
- you want to integrate an external vector database
- you want to reuse the abstractions in another Koog module

## See also

- [Embeddings](embeddings.md)
