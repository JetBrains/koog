# Document storage

To let you provide up-to-date and searchable information sources for use with Large Language Models (LLMs), Koog
supports Resource-Augmented Generation (RAG) to store and retrieve information from documents.

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
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.InMemoryVectorStorage
import ai.koog.rag.vector.JVMTextDocumentEmbedder
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
val embedder = LLMEmbedder(OllamaClient(), OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a ranked document storage using in-memory vector storage
val rankedDocumentStorage = EmbeddingBasedDocumentStorage(documentEmbedder, InMemoryVectorStorage())

// Store documents in the storage
rankedDocumentStorage.store(Path.of("./my/documents/doc1.txt"))
rankedDocumentStorage.store(Path.of("./my/documents/doc2.txt"))
rankedDocumentStorage.store(Path.of("./my/documents/doc3.txt"))
// ... store more documents as needed
rankedDocumentStorage.store(Path.of("./my/documents/doc100.txt"))

// Find the most relevant documents for a user query
val query = "I want to open a bank account but I'm getting a 404 when I open your website. I used to be your client with a different account 5 years ago before you changed your firm name"
val relevantFiles = rankedDocumentStorage.mostRelevantDocuments(query, count = 3)

// Process the relevant files
relevantFiles.forEach { file ->
    println("Relevant file: ${file.toAbsolutePath()}")
    // Process the file content as needed
}
```
<!--- KNIT example-ranked-document-storage-01.kt -->


### Providing relevance search for use by AI agents

Once you have a ranked document storage system, you can use it to provide relevant context to an AI agent for answering user queries. This enhances the agent's ability to provide accurate and contextually appropriate responses.

Here is an example of how to implement the defined RAG system for an AI agent to be able to answer queries by getting information from the document storage: 

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.InMemoryVectorStorage
import ai.koog.rag.vector.JVMTextDocumentEmbedder
import kotlin.io.path.pathString

// Create an embedder using Ollama
val embedder = LLMEmbedder(OllamaClient(), OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a ranked document storage using in-memory vector storage
val rankedDocumentStorage = EmbeddingBasedDocumentStorage(documentEmbedder, InMemoryVectorStorage())

const val apiKey = "apikey"

-->
```kotlin
suspend fun solveUserRequest(query: String) {
    // Retrieve top-5 documents from the document provider
    val relevantDocuments = rankedDocumentStorage.mostRelevantDocuments(query, count = 5)

    // Create an AI Agent with the relevant context
    val agentConfig = AIAgentConfig(
        prompt = prompt("context") {
            system("You are a helpful assistant. Use the provided context to answer the user's question accurately.")
            user {
                +"Relevant context:"
                relevantDocuments.forEach {
                    file(it.pathString, "text/plain")
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
<!--- KNIT example-ranked-document-storage-02.kt -->


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
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.InMemoryVectorStorage
import ai.koog.rag.vector.JVMTextDocumentEmbedder
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

// Create an embedder using Ollama
val embedder = LLMEmbedder(OllamaClient(), OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
// You may also use OpenAI embeddings with:
// val embedder = LLMEmbedder(OpenAILLMClient("API_KEY"), OpenAIModels.Embeddings.TextEmbeddingAda3Large)

// Create a JVM-specific document embedder
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// Create a ranked document storage using in-memory vector storage
val rankedDocumentStorage = EmbeddingBasedDocumentStorage(documentEmbedder, InMemoryVectorStorage())

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
    val relevantDocuments =
        rankedDocumentStorage.mostRelevantDocuments(query, count = count, similarityThreshold = 0.9).toList()

    if (!relevantDocuments.isEmpty()) {
        return "No relevant documents found for the query: $query"
    }

    val result = StringBuilder("Found ${relevantDocuments.size} relevant documents:\n\n")

    relevantDocuments.forEachIndexed { index, document ->
        val content = Files.readString(document)
        result.append("Document ${index + 1}: ${document.fileName}\n")
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
<!--- KNIT example-ranked-document-storage-03.kt -->

With this approach, the agent can decide when to use the search tool based on your query. This is particularly useful for complex queries that may require information from multiple documents or when the agent needs to search for specific details.

## Existing implementations of vector storage and document embedding providers

For convenience and easier implementation of a RAG system, Koog provides several out-of-the-box implementations for vector storage, document embedding, and combined embedding and storage components.

### Vector storage

#### InMemoryVectorStorage

A simple in-memory implementation that stores documents and their vector embeddings in memory. Suitable for testing or small-scale applications.

<!--- INCLUDE
import ai.koog.rag.vector.InMemoryVectorStorage
import java.nio.file.Path
-->
```kotlin
val inMemoryStorage = InMemoryVectorStorage<Path>()
```
<!--- KNIT example-ranked-document-storage-04.kt -->

For more information, see the [InMemoryVectorStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-in-memory-vector-storage/index.html) reference.

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
<!--- KNIT example-ranked-document-storage-05.kt -->

For more information, see the [FileVectorStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-file-vector-storage/index.html) reference.

#### JVMFileVectorStorage

A JVM-specific implementation of `FileVectorStorage` that works with `java.nio.file.Path`.

<!--- INCLUDE
import ai.koog.rag.vector.JVMFileVectorStorage
import java.nio.file.Path
-->
```kotlin
val jvmFileStorage = JVMFileVectorStorage(root = Path.of("/path/to/storage"))
```
<!--- KNIT example-ranked-document-storage-06.kt -->

For more information, see the [JVMFileVectorStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-j-v-m-file-vector-storage/index.html) reference.

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
<!--- KNIT example-ranked-document-storage-07.kt -->

For more information, see the [TextDocumentEmbedder](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-text-document-embedder/index.html) reference.

#### JVMTextDocumentEmbedder

A JVM-specific implementation that works with `java.nio.file.Path`.

<!--- INCLUDE
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.vector.JVMTextDocumentEmbedder

-->
```kotlin
val embedder = LLMEmbedder(OllamaClient(), OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
val jvmTextEmbedder = JVMTextDocumentEmbedder(embedder = embedder)
```
<!--- KNIT example-ranked-document-storage-08.kt -->

For more information, see the [JVMTextDocumentEmbedder](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-j-v-m-text-document-embedder/index.html) reference.

### Combined storage implementations

#### EmbeddingBasedDocumentStorage

Combines a document embedder and a vector storage to provide a complete solution for storing and ranking documents.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage02.documentEmbedder
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.InMemoryVectorStorage
import java.nio.file.Path

val vectorStorage = InMemoryVectorStorage<Path>()

-->
```kotlin
val embeddingStorage = EmbeddingBasedDocumentStorage(
    embedder = documentEmbedder,
    storage = vectorStorage
)
```
<!--- KNIT example-ranked-document-storage-09.kt -->

For more information, see the [EmbeddingBasedDocumentStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-embedding-based-document-storage/index.html) reference.

#### InMemoryDocumentEmbeddingStorage

An in-memory implementation of `EmbeddingBasedDocumentStorage`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage03.documentEmbedder
import ai.koog.rag.vector.InMemoryDocumentEmbeddingStorage
import java.nio.file.Path

typealias Document = Path
-->
```kotlin
val inMemoryEmbeddingStorage = InMemoryDocumentEmbeddingStorage<Document>(
    embedder = documentEmbedder
)

```
<!--- KNIT example-ranked-document-storage-10.kt -->

For more information, see the [InMemoryDocumentEmbeddingStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-in-memory-document-embedding-storage/index.html) reference.

#### FileDocumentEmbeddingStorage

A file-based implementation of `EmbeddingBasedDocumentStorage`.

<!--- INCLUDE
/*
-->
<!--- SUFFIX
*/
-->
```kotlin
val fileEmbeddingStorage = FileDocumentEmbeddingStorage<Document, Path>(
   embedder = documentEmbedder,
   documentProvider = documentProvider,
   fs = fileSystemProvider,
   root = rootPath
)
```
<!--- KNIT example-ranked-document-storage-11.kt -->

For more information, see the [FileDocumentEmbeddingStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-file-document-embedding-storage/index.html) reference.

#### JVMFileDocumentEmbeddingStorage

A JVM-specific implementation of `FileDocumentEmbeddingStorage`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage03.documentEmbedder
import ai.koog.rag.vector.JVMFileDocumentEmbeddingStorage
import java.nio.file.Path
-->
```kotlin
val jvmFileEmbeddingStorage = JVMFileDocumentEmbeddingStorage(
   embedder = documentEmbedder,
   root = Path.of("/path/to/storage")
)
```
<!--- KNIT example-ranked-document-storage-12.kt -->

For more information, see the [JVMFileDocumentEmbeddingStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-j-v-m-file-document-embedding-storage/index.html) reference.

#### JVMTextFileDocumentEmbeddingStorage

A JVM-specific implementation that combines `JVMTextDocumentEmbedder` and `JVMFileVectorStorage`.

<!--- INCLUDE
import ai.koog.agents.example.exampleRankedDocumentStorage08.embedder
import ai.koog.rag.vector.JVMTextFileDocumentEmbeddingStorage
import java.nio.file.Path
-->
```kotlin
val jvmTextFileEmbeddingStorage = JVMTextFileDocumentEmbeddingStorage(
   embedder = embedder,
   root = Path.of("/path/to/storage")
)
```
<!--- KNIT example-ranked-document-storage-13.kt -->

For more information, see the [JVMTextFileDocumentEmbeddingStorage](https://api.koog.ai/rag/vector-storage/ai.koog.rag.vector/-j-v-m-text-file-document-embedding-storage/index.html) reference.

These implementations provide a flexible and extensible framework for working with document embeddings and vector storage in various environments.

## Implementing your own vector storage and document embedder

You can extend Koog's vector storage framework by implementing your own custom document embedders and vector storage solutions. This is particularly useful when working with specialized document types or storage requirements.

Here's an example of implementing a custom document embedder for PDF documents:

<!--- INCLUDE
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.DocumentEmbedder
import ai.koog.rag.vector.InMemoryVectorStorage
import ai.koog.rag.vector.VectorStorage
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
) : RankedDocumentStorage<PDFDocument> {
    override fun rankDocuments(query: String): Flow<RankedDocument<PDFDocument>> = flow {
        val queryVector = embedder.embed(query)
        storage.allDocumentsWithPayload().collect { (document, documentVector) ->
            emit(
                RankedDocument(
                    document = document,
                    similarity = 1.0 - embedder.diff(queryVector, documentVector)
                )
            )
        }
    }

    override suspend fun store(document: PDFDocument, data: Unit): String {
        val vector = embedder.embed(document)
        return storage.store(document, vector)
    }

    override suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    override suspend fun read(documentId: String): PDFDocument? {
        return storage.read(documentId)
    }

    override fun allDocuments(): Flow<PDFDocument> = flow {
        storage.allDocumentsWithPayload().collect {
            emit(it.document)
        }
    }
}

// Usage example
suspend fun main() {
    val pdfProvider = PDFDocumentProvider()
    val embedder = LLMEmbedder(OllamaClient(), OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
    val pdfEmbedder = PDFDocumentEmbedder(embedder)
    val storage = InMemoryVectorStorage<PDFDocument>()
    val pdfStorage = PDFVectorStorage(pdfProvider, pdfEmbedder, storage)

    // Store PDF documents
    val pdfDocument = PDFDocument(Path.of("./documents/sample.pdf"))
    pdfStorage.store(pdfDocument)

    // Query for relevant PDF documents
    val relevantPDFs = pdfStorage.mostRelevantDocuments("information about climate change", count = 3)

}
```
<!--- KNIT example-ranked-document-storage-14.kt -->

## Implementing custom non-embedding-based RankedDocumentStorage

While embedding-based document ranking is powerful, there are scenarios where you might want to implement a custom ranking mechanism that does not rely on embeddings. For example, you might want to rank documents based on:

- PageRank-like algorithms
- Keyword frequency
- Recency of documents
- User interaction history
- Domain-specific heuristics

Here's an example of implementing a custom `RankedDocumentStorage` that uses a simple keyword-based ranking approach:

<!--- INCLUDE
import ai.koog.rag.base.DocumentStorage
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import ai.koog.rag.base.files.DocumentProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
-->
```kotlin
class KeywordBasedDocumentStorage<Document>(
    private val documentProvider: DocumentProvider<Path, Document>,
    private val storage: DocumentStorage<Document>
) : RankedDocumentStorage<Document> {

    override fun rankDocuments(query: String): Flow<RankedDocument<Document>> = flow {
        // Split the query into keywords
        val keywords = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }

        // Process each document
        storage.allDocuments().collect { document ->
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
            emit(RankedDocument(document, similarity))
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

    override suspend fun store(document: Document, data: Unit): String {
        return storage.store(document)
    }

    override suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    override suspend fun read(documentId: String): Document? {
        return storage.read(documentId)
    }

    override fun allDocuments(): Flow<Document> {
        return storage.allDocuments()
    }
}
```
<!--- KNIT example-ranked-document-storage-15.kt -->

This implementation ranks documents based on the frequency of keywords from the query appearing in the document text. You could extend this approach with more sophisticated algorithms like TF-IDF (Term Frequency-Inverse Document Frequency) or BM25.

Another example is a time-based ranking system that prioritizes recent documents:

<!--- INCLUDE
import ai.koog.rag.base.DocumentStorage
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.lang.System.currentTimeMillis
-->
```kotlin
class TimeBasedDocumentStorage<Document>(
    private val storage: DocumentStorage<Document>,
    private val getDocumentTimestamp: (Document) -> Long
) : RankedDocumentStorage<Document> {

    override fun rankDocuments(query: String): Flow<RankedDocument<Document>> = flow {
        val currentTime = System.currentTimeMillis()

        storage.allDocuments().collect { document ->
            val timestamp = getDocumentTimestamp(document)
            val ageInHours = (currentTime - timestamp) / (1000.0 * 60 * 60)

            // Calculate a decay factor based on age (newer documents get higher scores)
            val decayFactor = Math.exp(-0.01 * ageInHours)

            emit(RankedDocument(document, decayFactor))
        }
    }

    // Implement other required methods from RankedDocumentStorage
    override suspend fun store(document: Document, data: Unit): String {
        return storage.store(document)
    }

    override suspend fun delete(documentId: String): Boolean {
        return storage.delete(documentId)
    }

    override suspend fun read(documentId: String): Document? {
        return storage.read(documentId)
    }

    override fun allDocuments(): Flow<Document> {
        return storage.allDocuments()
    }
}
```
<!--- KNIT example-ranked-document-storage-16.kt -->

By implementing the `RankedDocumentStorage` interface, you can create custom ranking mechanisms tailored to your specific use case while still leveraging the rest of the RAG infrastructure.

The flexibility of Koog's design allows you to mix and match different storage and ranking strategies to build a system that meets your specific requirements.
