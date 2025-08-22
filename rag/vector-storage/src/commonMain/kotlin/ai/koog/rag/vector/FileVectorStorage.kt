package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.DocumentWithPayload
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.createDirectory
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * FileVectorStorage is a class that manages the storage and retrieval of documents and their corresponding vector embeddings
 * within a file system. It uses a DocumentProvider to handle document content and a FileSystemProvider to interact with the
 * file system for storing and reading data.
 *
 * @param Document Type representing the document to be stored.
 * @param Path Type representing the file path in the storage system.
 * @param documentReader A provider responsible for handling document serialization and deserialization.
 * @param fs A file system provider enabling read and write operations for file storage.
 * @param root Root file path where the storage system will organize data.
 */
public open class FileVectorStorage<Document, Path>(
    private val documentReader: DocumentProvider<Path, Document>,
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
) : VectorStorage<Document> {
    private val json = Json { prettyPrint = true }

    /**
     * Directory where document metadata is stored
     */
    private suspend fun documentsDir(): Path {
        val dir = fs.joinPath(root, "documents")
        if (!fs.exists(dir)) {
            fs.createDirectory(dir)
        }
        return dir
    }

    /**
     * Directory where vector payloads are stored
     */
    private suspend fun vectorsDir(): Path {
        val dir = fs.joinPath(root, "vectors")
        if (!fs.exists(dir)) {
            fs.createDirectory(dir)
        }
        return dir
    }

    /**
     * Get the path to the document file for a given document ID
     */
    private suspend fun documentPath(documentId: String): Path {
        return fs.joinPath(documentsDir(), documentId)
    }

    /**
     * Get the path to the vector file for a given document ID
     */
    private suspend fun vectorPath(documentId: String): Path {
        return fs.joinPath(vectorsDir(), documentId)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun store(document: Document, data: Vector): String {
        // Ensure root directories exist
        val docsDir = documentsDir()
        val vecsDir = vectorsDir()

        // Generate a unique ID for the document
        val documentId = Uuid.random().toString()

        // Create a temporary file path for the document
        val docPath = fs.joinPath(docsDir, documentId)

        // Write the document to the file system
        val docText = documentReader.text(document).toString()
        fs.writeText(docPath, docText)

        // Create a temporary file path for the vector
        val vecPath = fs.joinPath(vecsDir, documentId)

        // Serialize the vector to JSON and write it to the file system
        val vectorJson = json.encodeToString(data)
        fs.writeText(vecPath, vectorJson)

        return documentId
    }

    override suspend fun delete(documentId: String): Boolean {
        val docPath = documentPath(documentId)
        val vecPath = vectorPath(documentId)

        var success = true

        // Check if document exists and delete it
        if (fs.exists(docPath)) {
            val docParent = fs.parent(docPath)
            val docName = fs.name(docPath)
            if (docParent != null) {
                fs.delete(fs.joinPath(docParent, docName))
            } else {
                success = false
            }
        } else {
            success = false
        }

        // Check if vector exists and delete it
        if (fs.exists(vecPath)) {
            val vecParent = fs.parent(vecPath)
            val vecName = fs.name(vecPath)
            if (vecParent != null) {
                fs.delete(fs.joinPath(vecParent, vecName))
            } else {
                success = false
            }
        }

        return success
    }

    override suspend fun read(documentId: String): Document? {
        val docPath = documentPath(documentId)

        if (!fs.exists(docPath)) {
            return null
        }

        return documentReader.document(docPath)
    }

    override suspend fun getPayload(documentId: String): Vector? {
        val vecPath = vectorPath(documentId)

        if (!fs.exists(vecPath)) {
            return null
        }

        val vectorJson = fs.readText(vecPath)
        return json.decodeFromString<Vector>(vectorJson)
    }

    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Vector>? {
        val document = read(documentId) ?: return null
        val payload = getPayload(documentId) ?: return null

        return DocumentWithPayload(document, payload)
    }

    override fun allDocuments(): Flow<Document> = flow {
        val docsDir = documentsDir()

        if (!fs.exists(docsDir)) {
            return@flow
        }

        fs.list(docsDir).forEach { path ->
            documentReader.document(path)?.let { document ->
                emit(document)
            }
        }
    }

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Vector>> = flow {
        val docsDir = documentsDir()

        if (!fs.exists(docsDir)) {
            return@flow
        }

        fs.list(docsDir).forEach { path ->
            val documentId = fs.name(path)
            readWithPayload(documentId)?.let { docWithPayload ->
                emit(docWithPayload)
            }
        }
    }
}

/**
 * A file-based implementation of document embedding storage.
 *
 * This class facilitates the storage and retrieval of documents and their corresponding vector embeddings
 * in a file system. It utilizes a [FileVectorStorage] for managing the document embeddings and extends
 * [EmbeddingBasedDocumentStorage], inheriting capabilities such as ranking, storing, and deleting documents
 * based on their embeddings.
 *
 * @param Document The type of the documents being stored.
 * @param embedder A mechanism responsible for embedding the documents into vector representations.
 * @param fs Platform-specific file system provider for path manipulations
 * @param root Root directory where all vector storage will be located
 */
public open class FileDocumentEmbeddingStorage<Document, Path>(
    embedder: DocumentEmbedder<Document>,
    documentProvider: DocumentProvider<Path, Document>,
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path
) : EmbeddingBasedDocumentStorage<Document>(
    embedder = embedder,
    storage = FileVectorStorage(documentProvider, fs, root)
)

/**
 * A file-based implementation of document storage utilizing embeddings for ranking and retrieval.
 *
 * This class specializes in storing and ranking text documents in a file system using embeddings derived from their
 * textual content. It integrates several components:
 * - A `TextDocumentReader` to extract textual content from the provided documents.
 * - An `Embedder` to generate vector embeddings from this textual content.
 * - A file-based vector storage for storing documents alongside their embeddings.
 *
 * The storage system allows document ranking based on similarity to a given query, ensuring efficient,
 * persistent document search and retrieval.
 *
 * @param Document The type of document to be stored and processed.
 * @param embedder Converts text into vector embeddings and calculates similarity between embeddings.
 * @param reader Extracts text from documents of type [Document] for embedding purposes.
 * @param fs Platform-specific file system provider for path manipulations
 * @param root Root directory where all vector storage will be located
 */
public open class TextFileDocumentEmbeddingStorage<Document, Path>(
    embedder: Embedder,
    documentProvider: DocumentProvider<Path, Document>,
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path
) : FileDocumentEmbeddingStorage<Document, Path>(
    embedder = TextDocumentEmbedder(documentProvider, embedder),
    documentProvider = documentProvider,
    fs = fs,
    root = root
)
