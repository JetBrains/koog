package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.DocumentWithPayload
import ai.koog.rag.base.files.DocumentProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A concrete implementation of [VectorStorage] that stores documents and their associated vector payloads in memory.
 *
 * Use this class to manage the storage and retrieval of documents and their vector-based data without relying on
 * any external persistent storage. This is suitable for in-memory operations and testing environments where
 * persistent storage is not required.
 *
 * @param Document The type of document managed by this storage.
 */
public class InMemoryVectorStorage<Document> : VectorStorage<Document> {
    private val documentById: MutableMap<String, DocumentWithPayload<Document, Vector>> = mutableMapOf()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun store(document: Document, data: Vector): String {
        val docID = Uuid.random().toString()
        documentById[docID] = DocumentWithPayload(document, data)

        return docID
    }

    override suspend fun delete(documentId: String): Boolean {
        return documentById.remove(documentId) != null
    }

    override suspend fun read(documentId: String): Document? {
        return documentById[documentId]?.document
    }

    override suspend fun getPayload(documentId: String): Vector? {
        return documentById[documentId]?.payload
    }

    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Vector>? {
        return documentById[documentId]
    }

    override fun allDocuments(): Flow<Document> = flow {
        documentById.values.forEach { emit(it.document) }
    }

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Vector>> = flow {
        documentById.values.forEach { emit(it) }
    }
}

/**
 * An in-memory implementation of document embedding storage.
 *
 * This class facilitates the storage and retrieval of documents and their corresponding vector embeddings
 * entirely in memory. It utilizes an [InMemoryVectorStorage] for managing the document embeddings and extends
 * [EmbeddingBasedDocumentStorage], inheriting capabilities such as ranking, storing, and deleting documents
 * based on their embeddings.
 *
 * @param Document The type of the documents being stored.
 * @param embedder A mechanism responsible for embedding the documents into vector representations.
 */
public open class InMemoryDocumentEmbeddingStorage<Document>(embedder: DocumentEmbedder<Document>) :
    EmbeddingBasedDocumentStorage<Document>(
        embedder = embedder,
        storage = InMemoryVectorStorage()
    )

/**
 * Implementation of an in-memory storage solution for text document embeddings.
 *
 * This class leverages the functionality of an embedder and a document provider
 * to compute embeddings for text documents and store them in memory for efficient retrieval.
 * It combines a `TextDocumentEmbedder` for embedding computation and an
 * `InMemoryVectorStorage` for vector storage, enabling lightweight and fast operations.
 *
 * @param Document The type representing the document being managed.
 * @param Path The type representing the path or identifier for locating documents.
 * @param embedder The embedder used for generating vectorized representations of text.
 * @param documentReader The document provider facilitating access to document contents.
 */
public class InMemoryTextDocumentEmbeddingStorage<Document, Path>(
    embedder: Embedder,
    documentReader: DocumentProvider<Path, Document>
) : InMemoryDocumentEmbeddingStorage<Document>(
    embedder = TextDocumentEmbedder(documentReader, embedder),
)
