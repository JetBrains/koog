# Retrieval-augmented generation (RAG)

To let you provide up-to-date and searchable information sources for use with Large Language Models (LLMs), Koog
supports Retrieval-augmented generation (RAG) to store and retrieve information from documents.

## Key RAG features

The core components of a common RAG system include:

- **Document storage**: a repository of documents, files, or text chunks that contain information.
- **Vector embeddings**: numerical representations of a text that capture semantic meaning. For more information on embeddings in Koog, see [Embeddings](embeddings.md).
- **Retrieval mechanism**: a system that finds the most relevant documents based on a query.
- **Generation component**: an LLM that uses the retrieved information to generate responses.

RAG addresses several limitations of traditional LLMs:

- **Knowledge cutoff**: RAG can access the most recent information, not limited to training data.
- **Hallucinations**: by grounding responses in retrieved documents, RAG reduces fabricated information.
- **Domain specificity**: RAG can be tailored to specific domains by curating the knowledge base.
- **Transparency**: the sources of information can be cited, making the system more explainable.

## Finding information in a RAG system

Finding relevant information in a RAG system involves storing documents as vector embeddings and ranking them based on their similarity to a user's query. This approach works with various document types, including PDFs, images, text files, or even individual text chunks.

The process involves:

1. **Document embedding**: converting documents into vector representations that capture their semantic meaning.
2. **Vector storage**: storing these embeddings efficiently for quick retrieval.
3. **Similarity search**: finding documents whose embeddings are most similar to the query embedding.
4. **Ranking**: ordering documents by their relevance score.

## Implementing a RAG system in Koog

To implement a RAG system in Koog, follow the steps below:

1. Create an embedder using Ollama or OpenAI. The embedder is an instance of the `LLMEmbedder` class that takes an LLM client instance and model as parameters. For more information, see [Embeddings](embeddings.md).
2. Create a document embedder based on the created general embedder.
3. Create a document storage.
4. Add documents to the storage.
5. Find the most relevant documents using a defined query.

This sequence of steps represents a *relevance search* flow that returns the most relevant documents for a given user query. Here is a code sample showing how to implement the entire sequence of steps described above:

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.storage.SimilaritySearchRequest
import ai.koog.rag.vector.store.EmbeddingVectorStore
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
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
// Create an embedder using Ollama
val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a vector store using in-memory vector storage
val vectorStore = EmbeddingVectorStore(documentEmbedder, InMemoryVectorStorage())

// Store documents in the storage
vectorStore.add(listOf(
    Path.of("./my/documents/doc1.txt"),
    Path.of("./my/documents/doc2.txt"),
    Path.of("./my/documents/doc3.txt"),
    // ... add more documents as needed
    Path.of("./my/documents/doc100.txt")
))

// Find the most relevant documents for a user query
val query = "I want to open a bank account but I'm getting a 404 when I open your website. I used to be your client with a different account 5 years ago before you changed your firm name"
val results = vectorStore.search(SimilaritySearchRequest(query, limit = 3))

// Process the relevant files
results.forEach { result ->
    println("Relevant file: ${result.document.toAbsolutePath()} (similarity: ${result.similarity})")
    // Process the file content as needed
}
```
<!--- KNIT example-retrieval-augmented-generation-01.kt -->


### Providing relevance search for use by AI agents

Once you have a vector store system, you can use it to provide relevant context to an AI agent for answering user queries. This enhances the agent's ability to provide accurate and contextually appropriate responses.

Here is an example of how to implement the defined RAG system for an AI agent to be able to answer queries by getting information from the document storage: 

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.storage.SimilaritySearchRequest
import ai.koog.rag.vector.store.EmbeddingVectorStore
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
import kotlin.io.path.pathString

// Create an embedder using Ollama
val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a vector store using in-memory vector storage
val vectorStore = EmbeddingVectorStore(documentEmbedder, InMemoryVectorStorage())

const val apiKey = "apikey"

-->
```kotlin
suspend fun solveUserRequest(query: String) {
    // Retrieve top-5 documents from the document provider
    val results = vectorStore.search(SimilaritySearchRequest(query, limit = 5))

    // Create an AI Agent with the relevant context
    val agentConfig = AIAgentConfig(
        prompt = prompt("context") {
            system("You are a helpful assistant. Use the provided context to answer the user's question accurately.")
            user {
                +"Relevant context:"
                results.forEach {
                    file(it.document.pathString, "text/plain")
                }
            }
        },
        model = OpenAIModels.Chat.GPT4o, // Or a different model of your choice
        maxAgentIterations = 100,
    )

    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o
    )


    // Run the agent to get a response
    val response = agent.run(query)

    // Return or process the response
    println("Agent response: $response")
}
```
<!--- KNIT example-retrieval-augmented-generation-02.kt -->


### Providing relevance search as a tool

Instead of directly providing document content as context, you can also implement a tool that allows the agent to perform relevance searches on demand. This gives the agent more flexibility in deciding when and how to use the document storage.

Here is an example of how to implement a relevance search tool:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.storage.SimilaritySearchRequest
import ai.koog.rag.vector.store.EmbeddingVectorStore
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

// Create an embedder using Ollama
val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a vector store using in-memory vector storage
val vectorStore = EmbeddingVectorStore(documentEmbedder, InMemoryVectorStorage())

const val apiKey = "apikey"

-->
```kotlin
@Tool
@LLMDescription("Search for relevant documents about any topic (if exists). Returns the content of the most relevant documents.")
suspend fun searchDocuments(
    @LLMDescription("Query to search relevant documents about")
    query: String,
    @LLMDescription("Maximum number of documents")
    count: Int
): String {
    val results =
        vectorStore.search(SimilaritySearchRequest(query, limit = count, similarityThreshold = 0.9))

    if (results.isEmpty()) {
        return "No relevant documents found for the query: $query"
    }

    val result = StringBuilder("Found ${results.size} relevant documents:\n\n")

    results.forEachIndexed { index, searchResult ->
        val content = Files.readString(searchResult.document)
        result.append("Document ${index + 1}: ${searchResult.document.fileName}\n")
        result.append("Content: $content\n\n")
    }

    return result.toString()
}

fun main() {
    runBlocking {
        val tools = ToolRegistry {
            tool(::searchDocuments.asTool())
        }

        val agent = AIAgent(
            toolRegistry = tools,
            promptExecutor = simpleOpenAIExecutor(apiKey),
            llmModel = OpenAIModels.Chat.GPT4o
        )

        val response = agent.run("How to make a cake?")
        println("Agent response: $response")

    }
}
```
<!--- KNIT example-retrieval-augmented-generation-03.kt -->

With this approach, the agent can decide when to use the search tool based on your query. This is particularly useful for complex queries that may require information from multiple documents or when the agent needs to search for specific details.

## Existing implementations of vector storage and document embedding providers

For convenience and easier implementation of a RAG system, Koog provides several out-of-the-box implementations for vector storage, document embedding, and combined embedding and storage components.

### Vector storage

#### InMemoryVectorStorage

A simple in-memory implementation that stores documents and their vector embeddings in memory. Suitable for testing or small-scale applications.

<!--- INCLUDE
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import java.nio.file.Path
-->
```kotlin
val inMemoryStorage = InMemoryVectorStorage<Path>()
```
<!--- KNIT example-retrieval-augmented-generation-04.kt -->

For more information, see the [InMemoryVectorStorage](api:vector-storage::ai.koog.rag.vector.storage.InMemoryVectorStorage) reference.

#### FileVectorStorage

A file-based implementation that stores documents and their vector embeddings on disk. Suitable for persistent storage across application restarts.

<!--- INCLUDE
/*
-->
<!--- SUFFIX
*/
-->
```kotlin
val fileStorage = FileVectorStorage<Document, Path>(
   documentReader = documentProvider,
   fs = fileSystemProvider,
   root = rootPath
)
```
<!--- KNIT example-retrieval-augmented-generation-05.kt -->

For more information, see the [FileVectorStorage](api:vector-storage::ai.koog.rag.vector.storage.FileVectorStorage) reference.

#### JVMFileVectorStorage

A JVM-specific implementation of `FileVectorStorage` that works with `java.nio.file.Path`.

<!--- INCLUDE
import ai.koog.rag.vector.storage.JVMFileVectorStorage
import java.nio.file.Path
-->
```kotlin
val jvmFileStorage = JVMFileVectorStorage(root = Path.of("/path/to/storage"))
```
<!--- KNIT example-retrieval-augmented-generation-06.kt -->

For more information, see the [JVMFileVectorStorage](api:vector-storage::ai.koog.rag.vector.storage.JVMFileVectorStorage) reference.

### Document embedder

#### TextDocumentEmbedder

A generic implementation that works with any document type that can be converted to text.

<!--- INCLUDE
/*
-->
<!--- SUFFIX
*/
-->
```kotlin
val textEmbedder = TextDocumentEmbedder<Document, Path>(
   documentReader = documentProvider,
   embedder = embedder
)
```
<!--- KNIT example-retrieval-augmented-generation-07.kt -->

For more information, see the [TextDocumentEmbedder](api:vector-storage::ai.koog.rag.vector.embedder.TextDocumentEmbedder) reference.

#### JVMTextDocumentEmbedder

A JVM-specific implementation that works with `java.nio.file.Path`.

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder

-->
```kotlin
val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
val jvmTextEmbedder = JVMTextDocumentEmbedder(embedder = embedder)
```
<!--- KNIT example-retrieval-augmented-generation-08.kt -->

For more information, see the [JVMTextDocumentEmbedder](api:vector-storage::ai.koog.rag.vector.embedder.JVMTextDocumentEmbedder) reference.

### Combined storage implementations

#### EmbeddingVectorStore

Combines a document embedder and a vector storage to provide a complete solution for storing and searching documents.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage02.documentEmbedder
import ai.koog.rag.vector.store.EmbeddingVectorStore
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import java.nio.file.Path

val vectorStorage = InMemoryVectorStorage<Path>()

-->
```kotlin
val embeddingStore = EmbeddingVectorStore(
    embedder = documentEmbedder,
    storage = vectorStorage
)
```
<!--- KNIT example-retrieval-augmented-generation-09.kt -->

For more information, see the [EmbeddingVectorStore](api:vector-storage::ai.koog.rag.vector.store.EmbeddingVectorStore) reference.

#### InMemoryDocumentEmbeddingStore

An in-memory implementation of `EmbeddingVectorStore`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage03.documentEmbedder
import ai.koog.rag.vector.store.InMemoryDocumentEmbeddingStore
import java.nio.file.Path

typealias Document = Path
-->
```kotlin
val inMemoryEmbeddingStore = InMemoryDocumentEmbeddingStore<Document>(
    embedder = documentEmbedder
)

```
<!--- KNIT example-retrieval-augmented-generation-10.kt -->

For more information, see the [InMemoryDocumentEmbeddingStore](api:vector-storage::ai.koog.rag.vector.store.InMemoryDocumentEmbeddingStore) reference.

#### FileDocumentEmbeddingStore

A file-based implementation of `EmbeddingVectorStore`.

<!--- INCLUDE
/*
-->
<!--- SUFFIX
*/
-->
```kotlin
val fileEmbeddingStore = FileDocumentEmbeddingStore<Document, Path>(
   embedder = documentEmbedder,
   documentProvider = documentProvider,
   fs = fileSystemProvider,
   root = rootPath
)
```
<!--- KNIT example-retrieval-augmented-generation-11.kt -->

For more information, see the [FileDocumentEmbeddingStore](api:vector-storage::ai.koog.rag.vector.store.FileDocumentEmbeddingStore) reference.

#### JVMFileDocumentEmbeddingStore

A JVM-specific implementation of `FileDocumentEmbeddingStore`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage03.documentEmbedder
import ai.koog.rag.vector.store.JVMFileDocumentEmbeddingStore
import java.nio.file.Path
-->
```kotlin
val jvmFileEmbeddingStore = JVMFileDocumentEmbeddingStore(
   embedder = documentEmbedder,
   root = Path.of("/path/to/storage")
)
```
<!--- KNIT example-retrieval-augmented-generation-12.kt -->

For more information, see the [JVMFileDocumentEmbeddingStore](api:vector-storage::ai.koog.rag.vector.store.JVMFileDocumentEmbeddingStore) reference.

#### TextFileDocumentEmbeddingStore

A file-based implementation that combines `TextDocumentEmbedder` and `FileVectorStorage`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage08.embedder
import ai.koog.rag.vector.store.TextFileDocumentEmbeddingStore
import java.nio.file.Path
-->
```kotlin
val textFileEmbeddingStore = TextFileDocumentEmbeddingStore<Document, Path>(
   embedder = embedder,
   documentProvider = documentProvider,
   fs = fileSystemProvider,
   root = rootPath
)
```
<!--- KNIT example-retrieval-augmented-generation-13.kt -->

For more information, see the [TextFileDocumentEmbeddingStore](api:vector-storage::ai.koog.rag.vector.store.TextFileDocumentEmbeddingStore) reference.

These implementations provide a flexible and extensible framework for working with document embeddings and vector storage in various environments.

## Implementing your own vector storage and document embedder

You can extend Koog's vector storage framework by implementing your own custom document embedders and vector storage solutions. This is particularly useful when working with specialized document types or storage requirements.

Here's an example of implementing a custom document embedder for PDF documents:

<!--- INCLUDE
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.storage.SearchResult
import ai.koog.rag.base.storage.RetrievalStorage
import ai.koog.rag.base.storage.SearchRequest
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.storage.SimilaritySearchRequest
import ai.koog.rag.vector.embedder.DocumentEmbedder
import ai.koog.rag.vector.storage.InMemoryVectorStorage
import ai.koog.rag.vector.storage.VectorStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
-->
```kotlin
// Define a PDFDocument class
class PDFDocument(private val path: Path) {
    fun readText(): String {
        // Use a PDF library to extract text from the PDF
        return "Text extracted from PDF at $path"
    }
}

// Implement a DocumentProvider for PDFDocument
class PDFDocumentProvider : DocumentProvider<Path, PDFDocument> {
    override suspend fun document(path: Path): PDFDocument? {
        return if (path.toString().endsWith(".pdf")) {
            PDFDocument(path)
        } else {
            null
        }
    }

    override suspend fun text(document: PDFDocument): CharSequence {
        return document.readText()
    }
}

// Implement a DocumentEmbedder for PDFDocument
class PDFDocumentEmbedder(private val embedder: Embedder) : DocumentEmbedder<PDFDocument> {
    override suspend fun embed(document: PDFDocument): Vector {
        val text = document.readText()
        return embed(text)
    }

    override suspend fun embed(text: String): Vector {
        return embedder.embed(text)
    }

    override fun diff(embedding1: Vector, embedding2: Vector): Double {
        return embedder.diff(embedding1, embedding2)
    }
}

// Create a custom vector storage for PDF documents
class PDFVectorStorage(
    private val pdfProvider: PDFDocumentProvider,
    private val embedder: PDFDocumentEmbedder,
    private val storage: VectorStorage<PDFDocument>
) : RetrievalStorage<PDFDocument> {
    override fun rankDocuments(query: String): Flow<SearchResult<PDFDocument>> = flow {
        val queryVector = embedder.embed(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(
                SearchResult(
                    document = document,
                    similarity = 1.0 - embedder.diff(queryVector, documentVector)
                )
            )
        }
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<PDFDocument>> {
        val results = mutableListOf<SearchResult<PDFDocument>>()
        val queryVector = embedder.embed(request.query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            val similarity = 1.0 - embedder.diff(queryVector, documentVector)
            if (similarity >= request.similarityThreshold) {
                results.add(SearchResult(document = document, similarity = similarity))
            }
        }
        return results.sortedByDescending { it.similarity }.take(request.limit)
    }

    suspend fun store(document: PDFDocument): String {
        val vector = embedder.embed(document)
        return storage.store(document, vector)
    }

    suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    suspend fun read(documentId: String): PDFDocument? {
        return storage.read(documentId)
    }

    fun allDocuments(): Flow<PDFDocument> = flow {
        storage.allDocumentsWithPayload().collect {
            emit(it.document)
        }
    }
}

// Usage example
suspend fun main() {
    val pdfProvider = PDFDocumentProvider()
    val embedder = LLMEmbedder(OllamaClient(), OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
    val pdfEmbedder = PDFDocumentEmbedder(embedder)
    val storage = InMemoryVectorStorage<PDFDocument>()
    val pdfStorage = PDFVectorStorage(pdfProvider, pdfEmbedder, storage)

    // Store PDF documents
    val pdfDocument = PDFDocument(Path.of("./documents/sample.pdf"))
    pdfStorage.store(pdfDocument)

    // Query for relevant PDF documents
    val relevantPDFs = pdfStorage.search(SimilaritySearchRequest("information about climate change", limit = 3))

}
```
<!--- KNIT example-retrieval-augmented-generation-14.kt -->

## Implementing custom non-embedding-based RetrievalStorage

While embedding-based document ranking is powerful, there are scenarios where you might want to implement a custom ranking mechanism that does not rely on embeddings. For example, you might want to rank documents based on:

- PageRank-like algorithms
- Keyword frequency
- Recency of documents
- User interaction history
- Domain-specific heuristics

Here's an example of implementing a custom `RetrievalStorage` that uses a simple keyword-based ranking approach:

<!--- INCLUDE
import ai.koog.rag.base.storage.RetrievalStorage
import ai.koog.rag.base.storage.SearchResult
import ai.koog.rag.base.storage.SearchRequest
import ai.koog.rag.base.files.DocumentProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
-->
```kotlin
class KeywordBasedDocumentStorage<Document>(
    private val documentProvider: DocumentProvider<Path, Document>,
    private val documents: MutableList<Document> = mutableListOf()
) : RetrievalStorage<Document> {

    override fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow {
        // Split the query into keywords
        val keywords = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }

        // Process each document
        documents.forEach { document ->
            // Get the document text
            val documentText = documentProvider.text(document).toString().lowercase()

            // Calculate a simple similarity score based on keyword frequency
            var similarity = 0.0
            for (keyword in keywords) {
                val count = countOccurrences(documentText, keyword)
                if (count > 0) {
                    similarity += count.toDouble() / documentText.length * 1000
                }
            }

            // Emit the document with its similarity score
            emit(SearchResult(document, similarity))
        }
    }

    private fun countOccurrences(text: String, keyword: String): Int {
        var count = 0
        var index = 0
        while (index != -1) {
            index = text.indexOf(keyword, index)
            if (index != -1) {
                count++
                index += keyword.length
            }
        }
        return count
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<Document>> {
        val results = mutableListOf<SearchResult<Document>>()
        rankDocuments(request.query).collect { result ->
            if (result.similarity >= request.similarityThreshold) {
                results.add(result)
            }
        }
        return results.sortedByDescending { it.similarity }.take(request.limit)
    }
}
```
<!--- KNIT example-retrieval-augmented-generation-15.kt -->

This implementation ranks documents based on the frequency of keywords from the query appearing in the document text. You could extend this approach with more sophisticated algorithms like TF-IDF (Term Frequency-Inverse Document Frequency) or BM25.

Another example is a time-based ranking system that prioritizes recent documents:

<!--- INCLUDE
import ai.koog.rag.base.storage.RetrievalStorage
import ai.koog.rag.base.storage.SearchResult
import ai.koog.rag.base.storage.SearchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.lang.System.currentTimeMillis
-->
```kotlin
class TimeBasedDocumentStorage<Document>(
    private val documents: MutableList<Document> = mutableListOf(),
    private val getDocumentTimestamp: (Document) -> Long
) : RetrievalStorage<Document> {

    override fun rankDocuments(query: String): Flow<SearchResult<Document>> = flow {
        val currentTime = System.currentTimeMillis()

        documents.forEach { document ->
            val timestamp = getDocumentTimestamp(document)
            val ageInHours = (currentTime - timestamp) / (1000.0 * 60 * 60)

            // Calculate a decay factor based on age (newer documents get higher scores)
            val decayFactor = Math.exp(-0.01 * ageInHours)

            emit(SearchResult(document, decayFactor))
        }
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<Document>> {
        val results = mutableListOf<SearchResult<Document>>()
        rankDocuments(request.query).collect { result ->
            if (result.similarity >= request.similarityThreshold) {
                results.add(result)
            }
        }
        return results.sortedByDescending { it.similarity }.take(request.limit)
    }
}
```
<!--- KNIT example-retrieval-augmented-generation-16.kt -->

By implementing the `RetrievalStorage` interface, you can create custom ranking mechanisms tailored to your specific use case while still leveraging the rest of the RAG infrastructure.

The flexibility of Koog's design allows you to mix and match different storage and ranking strategies to build a system that meets your specific requirements.
