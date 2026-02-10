package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.ingestion.IngestionStorage
import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest
import ai.koog.agents.longtermmemory.retrieval.RetrievalStorage
import ai.koog.agents.longtermmemory.retrieval.SearchRequest
import ai.koog.agents.longtermmemory.retrieval.SearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory implementation of [ai.koog.agents.longtermmemory.retrieval.RetrievalStorage]
 * and [ai.koog.agents.longtermmemory.ingestion.IngestionStorage] that stores records in a map.
 *
 * This implementation is useful for testing, development, and scenarios where persistence
 * is not required. All data is stored in memory and will be lost when the application stops.
 *
 * ## Limitations:
 * - Data is not persisted and will be lost on application restart
 * - Supports only keyword search using simple substring matching
 * - Filter expressions are not supported
 *
 * @param defaultNamespace The default namespace to use when none is specified in method calls.
 *                         Defaults to "default".
 */
public open class InMemoryRecordStorage(
    private val defaultNamespace: String = "default"
) : RetrievalStorage, IngestionStorage {

    private val mutex = Mutex()
    private val namespaceRecords = mutableMapOf<String, MutableMap<String, MemoryRecord>>()

    private fun getRecordsForNamespace(namespace: String? = null): MutableMap<String, MemoryRecord> {
        val ns = namespace ?: defaultNamespace
        return namespaceRecords.getOrPut(ns) { mutableMapOf() }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(records: List<MemoryRecord>, namespace: String?) {
        mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            for (record in records) {
                val recordId = record.id ?: Uuid.random().toString()
                val recordWithId = if (record.id == null) {
                    record.copy(id = recordId)
                } else {
                    record
                }
                nsRecords[recordId] = recordWithId
            }
        }
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult> {
        return when (request) {
            is KeywordSearchRequest -> searchByText( // TODO: use filterExpression after switching to Filter DSL
                request.query,
                request.limit,
                request.similarityThreshold,
                namespace
            )

            else -> throw UnsupportedOperationException("InMemoryRecordStorage supports only KeywordSearchRequest.")
        }
    }

    private suspend fun searchByText(
        query: String,
        limit: Int,
        similarityThreshold: Double,
        namespace: String?
    ): List<SearchResult> {
        val allRecords = mutex.withLock { getRecordsForNamespace(namespace).values.toList() }
        val queryLower = query.lowercase()

        return allRecords
            .filter { it.content.lowercase().contains(queryLower) }
            .map { record -> SearchResult(record, 1.0) }
            .filter { it.similarity >= similarityThreshold }
            .take(limit)
    }

    /**
     * Returns the number of records in the repository for the specified namespace.
     *
     * @param namespace Optional namespace to count records for. If null, counts the default namespace.
     */
    public suspend fun size(namespace: String? = null): Int = mutex.withLock { getRecordsForNamespace(namespace).size }
}
