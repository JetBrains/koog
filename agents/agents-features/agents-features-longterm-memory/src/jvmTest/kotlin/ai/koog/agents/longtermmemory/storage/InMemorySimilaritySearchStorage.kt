package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
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
 * Simple in-memory [SearchStorage] that supports only [SimilaritySearchRequest].
 *
 * Similarity is computed as the Jaccard coefficient of word sets (case-insensitive),
 * which is sufficient for unit tests without requiring real vector embeddings.
 *
 * @param defaultNamespace The default namespace to use when none is specified.
 */
internal class InMemorySimilaritySearchStorage(
    private val defaultNamespace: String = "default"
) : SearchStorage<TextDocument, SearchRequest>,
    WriteStorage<TextDocument> {

    private val mutex = Mutex()
    private val namespaceRecords = mutableMapOf<String, MutableMap<String, TextDocument>>()

    private fun getRecordsForNamespace(namespace: String?): MutableMap<String, TextDocument> {
        val ns = namespace ?: defaultNamespace
        return namespaceRecords.getOrPut(ns) { mutableMapOf() }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(documents: List<TextDocument>, namespace: String?): List<String> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            documents.map { doc ->
                val recordId = doc.id ?: Uuid.random().toString()
                val recordWithId = MemoryRecord(doc.content, recordId, doc.metadata)
                nsRecords[recordId] = recordWithId
                recordId
            }
        }
    }

    override suspend fun update(documents: Map<String, TextDocument>, namespace: String?): List<String> {
        return mutex.withLock {
            val nsRecords = getRecordsForNamespace(namespace)
            val updated = mutableListOf<String>()
            for ((id, doc) in documents) {
                if (nsRecords.containsKey(id)) {
                    nsRecords[id] = MemoryRecord(doc.content, doc.id, doc.metadata)
                    updated.add(id)
                }
            }
            updated
        }
    }

    override suspend fun search(
        request: SearchRequest,
        namespace: String?
    ): List<SearchResult<TextDocument>> {
        require(request is SimilaritySearchRequest) {
            "InMemorySimilaritySearchStorage supports only SimilaritySearchRequest, got: ${request::class.simpleName}"
        }
        val allRecords = mutex.withLock { getRecordsForNamespace(namespace).values.toList() }
        val queryWords = request.queryText.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.toSet()
        val minScore = request.minScore ?: 0.0

        return allRecords
            .map { record ->
                val docWords = record.content.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.toSet()
                val intersection = queryWords.intersect(docWords).size.toDouble()
                val union = (queryWords + docWords).size.toDouble()
                val score = if (union == 0.0) 0.0 else intersection / union
                SearchResult(record, Score(score, ScoreMetric.COSINE_SIMILARITY))
            }
            .filter { it.score.value > 0.0 && it.score.value >= minScore }
            .sortedByDescending { it.score.value }
            .drop(request.offset)
            .take(request.limit)
    }

    /**
     * Returns the number of records stored in the given namespace.
     */
    fun size(namespace: String? = null): Int = namespaceRecords[namespace ?: defaultNamespace]?.size ?: 0
}
