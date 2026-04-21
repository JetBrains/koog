package ai.koog.rag.vector.backend

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.DocumentWithPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A concrete implementation of [VectorStorageBackend] that stores documents and their associated vector embeddings in memory.
 *
 * Use this class to manage the storage and retrieval of documents and their vector-based data without relying on
 * any external persistent storage. This is suitable for in-memory operations and testing environments where
 * persistent storage is not required.
 *
 * @param Document The type of document managed by this storage.
 */
public class InMemoryVectorStorageBackend<Document> : VectorStorageBackend<Document> {
    private val documentById: MutableMap<String, DocumentWithPayload<Document, Vector>> = mutableMapOf()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun store(document: Document, vector: Vector): String {
        val docID = Uuid.random().toString()
        documentById[docID] = DocumentWithPayload(document, vector)
        return docID
    }

    override suspend fun store(id: String, document: Document, vector: Vector): Boolean {
        if (!documentById.containsKey(id)) return false
        documentById[id] = DocumentWithPayload(document, vector)
        return true
    }

    override suspend fun delete(documentId: String): Boolean {
        return documentById.remove(documentId) != null
    }

    override suspend fun read(documentId: String): Document? {
        return documentById[documentId]?.document
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
