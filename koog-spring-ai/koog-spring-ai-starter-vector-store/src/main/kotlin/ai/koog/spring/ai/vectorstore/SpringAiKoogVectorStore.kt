package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import org.springframework.ai.vectorstore.SearchRequest as SpringAiSearchRequest

/**
 * Adapts a Spring AI [VectorStore] to Koog storage abstractions.
 */
public class SpringAiKoogVectorStore(
    private val vectorStore: VectorStore,
    private val dispatcher: CoroutineDispatcher,
) : KoogVectorStore {

    /**
     * It adds documents with randomly generated ids for null ids, otherwise it adds documents with specified ids.
     */
    override suspend fun add(documents: List<TextDocument>, namespace: String?): List<String> {
        require(namespace == null) { "Namespace scoping is not yet supported by SpringAiKoogVectorStore" }
        documents.forEach { validateMetadata(it.metadata) }
        return withContext(dispatcher) {
            try {
                val springDocs = documents.map { document ->
                    if (document.id != null) {
                        Document(document.id, document.content, document.metadata)
                    } else {
                        Document(document.content, document.metadata)
                    }
                }
                vectorStore.add(springDocs)
                springDocs.map { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KoogVectorStoreException("add", e.message, e)
            }
        }
    }

    /**
     * It deletes old documents by their ids and adds new documents. Spring AI VectorStore does not support transactional update.
     */
    override suspend fun update(documents: Map<String, TextDocument>, namespace: String?): List<String> {
        require(namespace == null) { "Namespace scoping is not yet supported by SpringAiKoogVectorStore" }
        documents.values.forEach { validateMetadata(it.metadata) }
        documents.forEach { (id, document) ->
            require(document.id == null || document.id == id) {
                "Document ID '${document.id}' conflicts with map key '$id'. " +
                    "Either set document.id to null or ensure it matches the map key."
            }
        }
        return withContext(dispatcher) {
            if (documents.isEmpty()) {
                return@withContext emptyList()
            }

            try {
                vectorStore.delete(documents.keys.toList())
                vectorStore.add(
                    documents.map { (id, document) ->
                        Document(
                            id,
                            document.content,
                            document.metadata,
                        )
                    }
                )
                documents.keys.toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KoogVectorStoreException("update", e.message, e)
            }
        }
    }

    /**
     * It deletes documents by ids, but returns specified ids, because Spring AI VectorStore does not return deleted ids.
     */
    override suspend fun delete(ids: List<String>, namespace: String?): List<String> {
        require(namespace == null) { "Namespace scoping is not yet supported by SpringAiKoogVectorStore" }
        return withContext(dispatcher) {
            if (ids.isEmpty()) {
                return@withContext emptyList()
            }

            try {
                vectorStore.delete(ids)
                ids
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KoogVectorStoreException("delete", e.message, e)
            }
        }
    }

    /**
     * It deletes documents by filterExpression, but returns an empty list, because VectorStore does not return deleted ids.
     */
    override suspend fun delete(filterExpression: String, namespace: String?): List<String> {
        require(namespace == null) { "Namespace scoping is not yet supported by SpringAiKoogVectorStore" }
        return withContext(dispatcher) {
            try {
                vectorStore.delete(filterExpression)
                emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KoogVectorStoreException("delete", e.message, e)
            }
        }
    }

    /**
     * Does similarity search, because Spring AI VectorStore currently supports only similaritySearch.
     */
    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<TextDocument>> {
        require(namespace == null) { "Namespace scoping is not yet supported by SpringAiKoogVectorStore" }
        require(request.limit >= 0) { "limit must be non-negative, but was ${request.limit}" }
        require(request.offset >= 0) { "offset must be non-negative, but was ${request.offset}" }
        if (request.limit == 0) {
            return emptyList()
        }
        require(request is SimilaritySearchRequest) { "Spring AI VectorStore supports only similarity search requests" }
        return withContext(dispatcher) {
            val textQuery = request.queryText
            val similarityThreshold = request.minScore ?: SpringAiSearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL

            try {
                val filterExpression = request.filterExpression?.let { text ->
                    FilterExpressionTextParser().parse(text)
                }
                vectorStore.similaritySearch(
                    SpringAiSearchRequest.builder()
                        .query(textQuery)
                        .topK(request.limit + request.offset)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(filterExpression)
                        .build()
                )
                    .drop(request.offset)
                    .take(request.limit)
                    .map { document ->
                        SearchResult(
                            document = DocumentWithMetadata(
                                document.text ?: "",
                                document.metadata,
                                document.id
                            ),
                            score = Score(document.score ?: 0.0, ScoreMetric.COSINE_SIMILARITY),
                            id = document.id,
                            metadata = null, // Spring AI stores metadata inside the document
                            namespace = namespace,
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KoogVectorStoreException("search", e.message, e)
            }
        }
    }

    private companion object {
        private val ALLOWED_TYPES = setOf(
            String::class,
            Boolean::class,
            Byte::class,
            Short::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
        )

        fun validateMetadata(metadata: Map<String, Any>?) {
            requireNotNull(metadata) { "metadata can not be null" }
            metadata.forEach { (key, value) ->
                require(value::class in ALLOWED_TYPES) {
                    "Metadata value for key '$key' must be a primitive type " +
                        "(String, Number, or Boolean), but was ${value::class.qualifiedName}"
                }
            }
        }
    }
}
