package ai.koog.rag.vector.backend

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
 * A file-system-based implementation of [VectorStorageBackend] that manages the storage and retrieval of documents
 * and their corresponding vector embeddings within a file system.
 *
 * Documents and vectors are stored in separate subdirectories under the root path.
 *
 * @param Document Type representing the document to be stored.
 * @param Path Type representing the file path in the storage system.
 * @param documentReader A provider responsible for handling document serialization and deserialization.
 * @param fs A file system provider enabling read and write operations for file storage.
 * @param root Root file path where the storage system will organize data.
 */
public open class FileVectorStorageBackend<Document, Path>(
    private val documentReader: DocumentProvider<Path, Document>,
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
) : VectorStorageBackend<Document> {
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

    private suspend fun writeDocumentAndVector(id: String, document: Document, vector: Vector) {
        val docsDir = documentsDir()
        val vecsDir = vectorsDir()

        val docPath = fs.joinPath(docsDir, id)
        val docText = documentReader.text(document).toString()
        fs.writeText(docPath, docText)

        val vecPath = fs.joinPath(vecsDir, id)
        val vectorJson = json.encodeToString(vector)
        fs.writeText(vecPath, vectorJson)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun store(document: Document, vector: Vector): String {
        val documentId = Uuid.random().toString()
        writeDocumentAndVector(documentId, document, vector)
        return documentId
    }

    override suspend fun store(id: String, document: Document, vector: Vector): Boolean {
        val docPath = fs.joinPath(documentsDir(), id)
        if (!fs.exists(docPath)) return false
        writeDocumentAndVector(id, document, vector)
        return true
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

    /**
     * Retrieves the vector payload associated with the document identified by the given document ID.
     *
     * @param documentId The unique identifier of the document whose vector is being retrieved.
     * @return The vector associated with the document, or null if no such document exists.
     */
    public suspend fun getPayload(documentId: String): Vector? {
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
