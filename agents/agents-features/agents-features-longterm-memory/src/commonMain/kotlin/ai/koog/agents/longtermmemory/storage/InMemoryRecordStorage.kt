package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.LookupStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory implementation of [SearchStorage]
 * and [WriteStorage] that stores records in a map.
 *
 * This implementation is useful for testing, development, and scenarios where persistence
 * is not required. All data is stored in memory and will be lost when the application stops.
 *
 * ## Limitations:
 * - Data is not persisted and will be lost on application restart
 * - Both [ai.koog.rag.base.storage.search.KeywordSearchRequest] and
 *   [ai.koog.rag.base.storage.search.SimilaritySearchRequest] are accepted, but both are
 *   implemented as simple case-insensitive substring matching; no vector embeddings are used
 * - Filter expressions are ignored
 *
 * @param defaultNamespace The default namespace to use when none is specified in method calls.
 *                         Defaults to "default".
 */
public open class InMemoryRecordStorage(
    private val defaultNamespace: String = "default"
) : SearchStorage<TextDocument, SearchRequest>,
    WriteStorage<TextDocument>,
    LookupStorage<TextDocument>,
    DeletionStorage {

    private val mutex = Mutex()
    private val namespaceRecords = mutableMapOf<String, MutableMap<String, TextDocument>>()

    private fun getRecordsForNamespace(namespace: String? = null): MutableMap<String, TextDocument> {
        val ns = namespace ?: defaultNamespace
        return namespaceRecords.getOrPut(ns) { mutableMapOf() }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(documents: List<TextDocument>, namespace: String?): List<String> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            documents.map { doc ->
                val recordId = doc.id ?: Uuid.random().toString()
                val recordWithId =
                    if (doc.id == null) {
                        MemoryRecord(doc.content, recordId, doc.metadata)
                    } else {
                        MemoryRecord(doc.content, doc.id, doc.metadata)
                    }
                nsRecords[recordId] = recordWithId
                recordId
            }
        }
    }

    override suspend fun update(documents: Map<String, TextDocument>, namespace: String?): List<String> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            val updated = mutableListOf<String>()
            for ((id, record) in documents) {
                if (nsRecords.containsKey(id)) {
                    nsRecords[id] = MemoryRecord(record.content, record.id, record.metadata)
                    updated.add(id)
                }
            }
            updated
        }
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<TextDocument>> {
        return when (request) {
            is KeywordSearchRequest -> searchByText(
                request.queryText,
                request.limit,
                request.minScore ?: 0.0,
                namespace
            )

            is SimilaritySearchRequest -> searchByText( // TODO: use filterExpression after switching to Filter DSL
                request.queryText,
                request.limit,
                request.minScore ?: 0.0,
                namespace
            )

            else -> throw UnsupportedOperationException("InMemoryRecordStorage supports only KeywordSearchRequest and SimilaritySearchRequest.")
        }
    }

    override suspend fun delete(
        ids: List<String>,
        namespace: String?
    ): List<String> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            ids.filter { nsRecords.remove(it) != null }
        }
    }

    override suspend fun get(
        ids: List<String>,
        namespace: String?
    ): List<TextDocument> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            ids.mapNotNull { nsRecords[it] }
        }
    }

    private suspend fun searchByText(
        query: String,
        limit: Int,
        similarityThreshold: Double,
        namespace: String?
    ): List<SearchResult<TextDocument>> {
        val allRecords = mutex.withLock { getRecordsForNamespace(namespace).values.toList() }
        val queryLower = query.lowercase()

        return allRecords
            .filter { it.content.lowercase().contains(queryLower) }
            .map { record -> SearchResult(record, Score(1.0, ScoreMetric.COSINE_SIMILARITY)) }
            .filter { it.score.value >= similarityThreshold }
            .take(limit)
    }

    /**
     * Returns the number of records in the repository for the specified namespace.
     *
     * @param namespace Optional namespace to count records for. If null, counts the default namespace.
     */
    public suspend fun size(namespace: String? = null): Int = mutex.withLock { getRecordsForNamespace(namespace).size }
}
