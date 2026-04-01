package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore

class SpringAiKoogVectorStoreTest {

    /**
     * A deterministic embedding model that produces a fixed-dimension vector from the hash of the input text.
     * This is sufficient for SimpleVectorStore which uses cosine similarity.
     */
    private val embeddingModel = object : EmbeddingModel {
        private val dimensions = 8

        override fun call(request: EmbeddingRequest): EmbeddingResponse {
            val embeddings = request.instructions.mapIndexed { index, text ->
                Embedding(embedText(text), index)
            }
            return EmbeddingResponse(embeddings)
        }

        override fun embed(document: Document): FloatArray = embedText(document.text ?: "")

        private fun embedText(text: String): FloatArray {
            val hash = text.hashCode().toLong() and 0xFFFFFFFFL
            return FloatArray(dimensions) { i ->
                val seed = (hash xor (i.toLong() * 31)) and 0xFFFFFFFFL
                (seed % 1000).toFloat() / 1000f
            }
        }

        override fun dimensions(): Int = dimensions
    }

    private lateinit var storage: SpringAiKoogVectorStore

    @BeforeEach
    fun setUp() {
        val vectorStore = SimpleVectorStore.builder(embeddingModel).build()
        storage = SpringAiKoogVectorStore(vectorStore = vectorStore, dispatcher = Dispatchers.Unconfined)
    }

    // ── Add ──

    @Test
    fun testAddReturnsGeneratedIds() = runTest {
        val ids = storage.add(
            listOf(
                DocumentWithMetadata(content = "first"),
                DocumentWithMetadata(content = "second"),
            ),
            namespace = null,
        )

        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotEmpty() })
    }

    @Test
    fun testAddPreservesExplicitId() = runTest {
        val ids = storage.add(
            listOf(
                DocumentWithMetadata(content = "a", id = "my-id-1"),
                DocumentWithMetadata(content = "b", id = "my-id-2"),
                DocumentWithMetadata(content = "c"), // no explicit id
            ),
            namespace = null,
        )

        assertEquals("my-id-1", ids[0])
        assertEquals("my-id-2", ids[1])
        assertTrue(ids[2].isNotEmpty())
    }

    @Test
    fun testAddPreservesMetadata() = runTest {
        storage.add(
            listOf(
                DocumentWithMetadata(
                    content = "document with metadata",
                    metadata = mapOf("topic" to "testing", "priority" to 1, "active" to true),
                ),
            ),
            namespace = null,
        )

        val results = storage.search(
            SimilaritySearchRequest(queryText = "metadata", limit = 1, offset = 0, minScore = 0.0),
            namespace = null,
        )

        val meta = results.first().document.metadata
        assertEquals("testing", meta!!["topic"])
        assertEquals(1, meta["priority"])
        assertEquals(true, meta["active"])
    }

    @Test
    fun testAddRejectsNonPrimitiveMetadata() = runTest {
        val ex = assertThrows<IllegalArgumentException> {
            storage.add(
                listOf(DocumentWithMetadata(content = "x", metadata = mapOf("bad" to listOf(1, 2)))),
                namespace = null,
            )
        }
        assertTrue(ex.message!!.contains("bad"))
        assertTrue(ex.message!!.contains("primitive"))
    }

    @Test
    fun testAddAcceptsPrimitiveMetadata() = runTest {
        val ids = storage.add(
            listOf(
                DocumentWithMetadata(
                    content = "x",
                    metadata = mapOf("s" to "v", "i" to 1, "b" to true, "d" to 1.5, "l" to 1L),
                ),
            ),
            namespace = null,
        )
        assertEquals(1, ids.size)
    }

    @Test
    fun testAddRejectsNonNullNamespace() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.add(listOf(DocumentWithMetadata(content = "x")), namespace = "ns")
        }
    }

    // ── Update ──

    @Test
    fun testUpdateReplacesDocument() = runTest {
        storage.add(
            listOf(DocumentWithMetadata(content = "original content", id = "update-me")),
            namespace = null,
        )

        val result = storage.update(
            mapOf("update-me" to DocumentWithMetadata(content = "updated content", metadata = mapOf("k" to "v"))),
            namespace = null,
        )

        assertEquals(listOf("update-me"), result)

        val results = storage.search(
            SimilaritySearchRequest(queryText = "updated content", limit = 1, offset = 0),
            namespace = null,
        )

        assertEquals("update-me", results.first().id)
        assertEquals("updated content", results.first().document.content)
        assertEquals("v", results.first().document.metadata!!["k"])
    }

    @Test
    fun testUpdateReturnsEmptyListForEmptyInput() = runTest {
        val result = storage.update(emptyMap(), namespace = null)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun testUpdateRejectsNonPrimitiveMetadata() = runTest {
        val ex = assertThrows<IllegalArgumentException> {
            storage.update(
                mapOf("id" to DocumentWithMetadata(content = "x", metadata = mapOf("nested" to mapOf("a" to 1)))),
                namespace = null,
            )
        }
        assertTrue(ex.message!!.contains("nested"))
        assertTrue(ex.message!!.contains("primitive"))
    }

    @Test
    fun testUpdateRejectsNonNullNamespace() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.update(mapOf("id" to DocumentWithMetadata(content = "x")), namespace = "ns")
        }
    }

    // ── Delete by IDs ──

    @Test
    fun testDeleteRemovesDocuments() = runTest {
        val ids = storage.add(
            listOf(
                DocumentWithMetadata(content = "to be deleted"),
                DocumentWithMetadata(content = "to be kept"),
            ),
            namespace = null,
        )

        val result = storage.delete(listOf(ids[0]), namespace = null)
        assertEquals(listOf(ids[0]), result)

        val results = storage.search(
            SimilaritySearchRequest(queryText = "deleted kept", limit = 10, offset = 0),
            namespace = null,
        )

        val remainingIds = results.map { it.id }
        assertTrue(ids[0] !in remainingIds)
    }

    @Test
    fun testDeleteByIdsReturnsEmptyListForEmptyInput() = runTest {
        val result = storage.delete(emptyList(), namespace = null)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun testDeleteByIdsRejectsNonNullNamespace() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.delete(listOf("id-1"), namespace = "ns")
        }
    }

    // ── Delete by filter expression ──

    @Test
    fun testDeleteByFilterRejectsNonNullNamespace() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.delete(filterExpression = "x == 'y'", namespace = "ns")
        }
    }

    // ── Search ──

    @Test
    fun testSearchReturnsResultsWithCorrectStructure() = runTest {
        storage.add(
            listOf(
                DocumentWithMetadata(content = "Kotlin is a modern programming language"),
                DocumentWithMetadata(content = "Spring Boot simplifies Java development"),
                DocumentWithMetadata(content = "AI agents can automate complex tasks"),
            ),
            namespace = null,
        )

        val results = storage.search(
            SimilaritySearchRequest(queryText = "programming language", limit = 2, offset = 0),
            namespace = null,
        )

        assertEquals(2, results.size)
        results.forEach { result ->
            assertTrue(result.document.content.isNotEmpty())
            assertEquals(ScoreMetric.COSINE_SIMILARITY, result.score.metric)
            assertTrue(result.id!!.isNotEmpty())
            assertNull(result.namespace)
            assertNull(result.metadata)
        }
    }

    @Test
    fun testSearchAppliesMinScoreThreshold() = runTest {
        storage.add(
            listOf(
                DocumentWithMetadata(content = "exact match query text"),
                DocumentWithMetadata(content = "completely unrelated content about cooking recipes"),
            ),
            namespace = null,
        )

        val allResults = storage.search(
            SimilaritySearchRequest(queryText = "exact match query text", limit = 10, offset = 0, minScore = 0.0),
            namespace = null,
        )

        val filteredResults = storage.search(
            SimilaritySearchRequest(queryText = "exact match query text", limit = 10, offset = 0, minScore = 0.99),
            namespace = null,
        )

        assertTrue(filteredResults.size <= allResults.size)
    }

    @Test
    fun testSearchDropsOffsetResultsAndTakesLimitResults() = runTest {
        storage.add(
            listOf(
                DocumentWithMetadata(content = "alpha document", id = "id-0"),
                DocumentWithMetadata(content = "beta document", id = "id-1"),
                DocumentWithMetadata(content = "gamma document", id = "id-2"),
                DocumentWithMetadata(content = "delta document", id = "id-3"),
                DocumentWithMetadata(content = "epsilon document", id = "id-4"),
            ),
            namespace = null,
        )

        val allResults = storage.search(
            SimilaritySearchRequest(queryText = "document", limit = 5, offset = 0),
            namespace = null,
        )

        val offsetResults = storage.search(
            SimilaritySearchRequest(queryText = "document", limit = 3, offset = 2),
            namespace = null,
        )

        assertEquals(3, offsetResults.size)
        // The offset results should match the tail of the full results
        assertEquals(allResults.drop(2).take(3).map { it.id }, offsetResults.map { it.id })
    }

    @Test
    fun testSearchRejectsNonNullNamespace() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.search(
                SimilaritySearchRequest(queryText = "q", limit = 1, offset = 0),
                namespace = "my-ns",
            )
        }
    }

    @Test
    fun testSearchRejectsNegativeLimit() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.search(
                SimilaritySearchRequest(queryText = "q", limit = -1, offset = 0),
                namespace = null,
            )
        }
    }

    @Test
    fun testSearchRejectsNegativeOffset() = runTest {
        assertThrows<IllegalArgumentException> {
            storage.search(
                SimilaritySearchRequest(queryText = "q", limit = 1, offset = -1),
                namespace = null,
            )
        }
    }

    // ── Exception wrapping ──

    @Test
    fun testAddWrapsBackendExceptionAsKoogVectorStoreException() = runTest {
        val failing = mockk<VectorStore>(relaxed = true)
        every { failing.add(any<List<Document>>()) } throws RuntimeException("backend error")
        val failingStorage = SpringAiKoogVectorStore(vectorStore = failing, dispatcher = Dispatchers.Unconfined)

        val ex = assertThrows<KoogVectorStoreException> {
            failingStorage.add(listOf(DocumentWithMetadata(content = "x")), namespace = null)
        }
        assertTrue(ex.message!!.contains("add"))
        assertTrue(ex.cause is RuntimeException)
    }

    @Test
    fun testSearchWrapsBackendExceptionAsKoogVectorStoreException() = runTest {
        val failing = mockk<VectorStore>(relaxed = true)
        every { failing.similaritySearch(any<org.springframework.ai.vectorstore.SearchRequest>()) } throws RuntimeException("search failed")
        val failingStorage = SpringAiKoogVectorStore(vectorStore = failing, dispatcher = Dispatchers.Unconfined)

        val ex = assertThrows<KoogVectorStoreException> {
            failingStorage.search(
                SimilaritySearchRequest(queryText = "q", limit = 1, offset = 0),
                namespace = null,
            )
        }
        assertTrue(ex.message!!.contains("search"))
        assertTrue(ex.cause is RuntimeException)
    }

    @Test
    fun testDeleteWrapsBackendExceptionAsKoogVectorStoreException() = runTest {
        val failing = mockk<VectorStore>(relaxed = true)
        every { failing.delete(any<List<String>>()) } throws RuntimeException("delete failed")
        val failingStorage = SpringAiKoogVectorStore(vectorStore = failing, dispatcher = Dispatchers.Unconfined)

        val ex = assertThrows<KoogVectorStoreException> {
            failingStorage.delete(listOf("id-1"), namespace = null)
        }
        assertTrue(ex.message!!.contains("delete"))
        assertTrue(ex.cause is RuntimeException)
    }

    // ── Filter parsing failure ──

    @Test
    fun testSearchWrapsFilterParsingFailureAsKoogVectorStoreException() = runTest {
        val request = SimilaritySearchRequest(
            queryText = "q",
            limit = 1,
            offset = 0,
            filterExpression = "this is not a valid filter @@!!",
        )

        val ex = assertThrows<KoogVectorStoreException> {
            storage.search(request, namespace = null)
        }
        assertTrue(ex.message!!.contains("search"))
    }

    // ── Update partial failure ──

    @Test
    fun testUpdateWrapsPartialFailureAsKoogVectorStoreException() = runTest {
        val failing = mockk<VectorStore>(relaxed = true)
        every { failing.delete(any<List<String>>()) } returns Unit
        every { failing.add(any<List<Document>>()) } throws RuntimeException("add after delete failed")
        val failingStorage = SpringAiKoogVectorStore(vectorStore = failing, dispatcher = Dispatchers.Unconfined)

        val ex = assertThrows<KoogVectorStoreException> {
            failingStorage.update(
                mapOf("id-1" to DocumentWithMetadata(content = "updated")),
                namespace = null,
            )
        }
        assertTrue(ex.message!!.contains("update"))
        assertTrue(ex.cause is RuntimeException)
    }
}
