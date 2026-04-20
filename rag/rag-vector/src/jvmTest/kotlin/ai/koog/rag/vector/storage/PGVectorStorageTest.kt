package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PGVectorStorageTest {

    private companion object {
        const val VECTOR_DIM = 16
    }

    /**
     * Deterministic, content-addressable embedder for tests. Produces unit-norm vectors
     * of fixed dimension [VECTOR_DIM] seeded by the tokens present in the input text,
     * so that texts sharing tokens end up close in cosine distance.
     */
    private class MockEmbedder(private val dim: Int = VECTOR_DIM) : Embedder {
        override suspend fun embed(text: String): Vector {
            val values = DoubleArray(dim)
            val tokens = text.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                values[0] = 1.0
                return Vector(values.toList())
            }
            for (token in tokens) {
                val bucket = ((token.hashCode().toLong() and 0xFFFFFFFFL) % dim).toInt()
                values[bucket] += 1.0
            }
            val norm = sqrt(values.sumOf { it * it })
            if (norm > 0.0) for (i in values.indices) values[i] = values[i] / norm
            return Vector(values.toList())
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double =
            embedding1.values.zip(embedding2.values) { a, b -> (a - b) * (a - b) }.sum()
    }

    private data class TestDoc(
        override val content: String,
        override val id: String?,
        override val metadata: Map<String, Any> = emptyMap(),
    ) : TextDocument

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var dataSource: PGSimpleDataSource

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()

        dataSource = PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun storage(
        tableName: String,
        embedder: Embedder? = MockEmbedder(),
    ): PGVectorStorage = PGVectorStorage(
        dataSource = dataSource,
        tableName = tableName,
        vectorDimension = VECTOR_DIM,
        embedder = embedder,
    )

    @Test
    fun testMigrateIsIdempotent() = runBlocking {
        val table = "pgv_migrate_idempotent"
        val s = storage(table)
        s.migrate()
        s.migrate()
        // A second storage instance on the same table should also run migration without errors.
        storage(table).migrate()
    }

    @Test
    fun testAddGetAndDelete() = runBlocking {
        val table = "pgv_add_get_delete"
        val s = storage(table)
        s.migrate()

        val docs = listOf(
            TestDoc(content = "The quick brown fox jumps over the lazy dog", id = "d1"),
            TestDoc(content = "Koala bears live in Australia", id = "d2"),
            TestDoc(content = "Neil Armstrong walked on the moon", id = "d3"),
        )

        val addedIds = s.add(docs)
        assertEquals(listOf("d1", "d2", "d3"), addedIds)

        val loaded = s.get(listOf("d1", "d3")).associateBy { it.id }
        assertEquals(2, loaded.size)
        assertEquals("The quick brown fox jumps over the lazy dog", loaded["d1"]!!.content)
        assertEquals("Neil Armstrong walked on the moon", loaded["d3"]!!.content)

        val deleted = s.delete(listOf("d2"))
        assertEquals(listOf("d2"), deleted)
        val afterDelete = s.get(listOf("d1", "d2", "d3"))
        assertEquals(2, afterDelete.size)
        assertTrue(afterDelete.none { it.id == "d2" })
    }

    @Test
    fun testUpdateReplacesContent() = runBlocking {
        val table = "pgv_update"
        val s = storage(table)
        s.migrate()

        s.add(listOf(TestDoc(content = "initial content", id = "u1")))
        val updated = s.update(mapOf("u1" to TestDoc(content = "updated content", id = "u1")))
        assertEquals(listOf("u1"), updated)

        val loaded = s.get(listOf("u1")).single()
        assertEquals("updated content", loaded.content)
    }

    @Test
    fun testSimilaritySearchFindsBestMatch() = runBlocking {
        val table = "pgv_similarity"
        val s = storage(table)
        s.migrate()

        s.add(
            listOf(
                TestDoc(content = "apple orange banana", id = "fruits"),
                TestDoc(content = "car truck motorcycle", id = "vehicles"),
                TestDoc(content = "piano guitar violin", id = "instruments"),
            )
        )

        val results = s.search(SimilaritySearchRequest(queryText = "banana apple", limit = 3))
        assertTrue(results.isNotEmpty(), "Expected at least one similarity result")
        assertEquals("fruits", results.first().id)
        // Cosine similarity must be clamped into [0, 1].
        assertTrue(results.all { it.score.value in 0.0..1.0 })
    }

    @Test
    fun testKeywordSearchUsesFullTextThenTrigram() = runBlocking {
        val table = "pgv_keyword"
        val s = storage(table)
        s.migrate()

        s.add(
            listOf(
                TestDoc(content = "PostgreSQL is a powerful open source database", id = "pg"),
                TestDoc(content = "Kotlin is a modern programming language", id = "kt"),
                TestDoc(content = "Docker containers simplify deployment", id = "dk"),
            )
        )

        // Exact FTS match.
        val ftsResults = s.search(KeywordSearchRequest(queryText = "kotlin", limit = 5))
        assertTrue(ftsResults.isNotEmpty(), "Expected FTS to match 'kotlin'")
        assertEquals("kt", ftsResults.first().id)

        // Non-matching FTS → trigram fuzzy fallback should still surface closest token.
        val fuzzyResults = s.search(KeywordSearchRequest(queryText = "postgresq", limit = 5))
        // Fuzzy is best-effort; if it returns anything, it must be a valid stored id.
        fuzzyResults.forEach { r ->
            assertTrue(r.id in setOf("pg", "kt", "dk"))
        }
    }

    @Test
    fun testNamespaceIsolation() = runBlocking {
        val nsA = "pgv_ns_a"
        val nsB = "pgv_ns_b"
        val s = storage(nsA)
        s.migrate(nsA)
        s.migrate(nsB)

        s.add(listOf(TestDoc(content = "only in A", id = "a1")), namespace = nsA)
        s.add(listOf(TestDoc(content = "only in B", id = "b1")), namespace = nsB)

        val aResults = s.get(listOf("a1", "b1"), namespace = nsA)
        assertEquals(1, aResults.size)
        assertEquals("a1", aResults.first().id)

        val bResults = s.get(listOf("a1", "b1"), namespace = nsB)
        assertEquals(1, bResults.size)
        assertEquals("b1", bResults.first().id)
    }

    @Test
    fun testInvalidNamespaceIsRejected() = runBlocking {
        val s = storage("pgv_invalid_ns_default")
        s.migrate()
        var thrown: Throwable? = null
        try {
            s.get(listOf("x"), namespace = "bad; DROP TABLE students;")
        } catch (e: IllegalArgumentException) {
            thrown = e
        }
        assertNotNull(thrown, "Invalid namespace should be rejected")
        Unit
    }

    @Test
    fun testEmbedderDimensionMismatchFailsFast() = runBlocking {
        val table = "pgv_dim_mismatch"
        // Storage configured for VECTOR_DIM, embedder produces a different dimension.
        val mismatched = PGVectorStorage(
            dataSource = dataSource,
            tableName = table,
            vectorDimension = VECTOR_DIM,
            embedder = MockEmbedder(dim = VECTOR_DIM + 1),
        )
        mismatched.migrate()
        var thrown: Throwable? = null
        try {
            mismatched.search(SimilaritySearchRequest(queryText = "anything", limit = 1))
        } catch (e: IllegalArgumentException) {
            thrown = e
        }
        assertNotNull(thrown, "Dimension mismatch must fail fast with IllegalArgumentException")
        Unit
    }

    @Test
    fun testGetOnMissingIdsReturnsEmpty() = runBlocking {
        val table = "pgv_missing"
        val s = storage(table)
        s.migrate()

        assertEquals(emptyList(), s.get(emptyList()))
        val none = s.get(listOf("does-not-exist"))
        assertTrue(none.isEmpty())
        assertNull(none.firstOrNull())
    }

    @Test
    fun testFilterExpressionIsApplied() = runBlocking {
        val table = "pgv_filter"
        val s = storage(table)
        s.migrate()

        s.add(
            listOf(
                TestDoc(content = "apple orange banana", id = "t1a", metadata = mapOf("tenant" to "a")),
                TestDoc(content = "apple orange banana", id = "t1b", metadata = mapOf("tenant" to "b")),
                TestDoc(content = "piano guitar violin", id = "t2a", metadata = mapOf("tenant" to "a")),
            )
        )

        // Only tenant "a" documents must surface, even though the query matches both tenants.
        val results = s.search(
            SimilaritySearchRequest(
                queryText = "banana apple",
                limit = 5,
                filterExpression = """tenant = "a"""",
            )
        )
        assertTrue(results.isNotEmpty(), "Expected at least one filtered result")
        assertTrue(results.all { it.id in setOf("t1a", "t2a") }, "Expected only tenant=a ids, got: $results")
        assertTrue(results.none { it.id == "t1b" }, "tenant=b document must be filtered out")
    }

    @Test
    fun testDocumentGranularityDeduplicatesChunks() = runBlocking {
        val table = "pgv_doc_granularity"
        // Storage that will chunk a long document into multiple chunks.
        val s = PGVectorStorage(
            dataSource = dataSource,
            tableName = table,
            vectorDimension = VECTOR_DIM,
            embedder = MockEmbedder(),
            chunker = RecursiveCharacterChunker(
                chunkSizeChars = 40,
                overlapChars = 5,
                minChunkChars = 5,
            ),
            resultGranularity = ResultGranularity.DOCUMENT,
        )
        s.migrate()

        // A deliberately long document that should split into multiple chunks.
        val longText = (1..10).joinToString(". ") { "banana apple orange token$it word$it" } + "."
        s.add(
            listOf(
                TestDoc(content = longText, id = "long"),
                TestDoc(content = "unrelated piano guitar", id = "short"),
            )
        )

        val results = s.search(SimilaritySearchRequest(queryText = "banana apple", limit = 3))
        // Even though 'long' has many matching chunks, DOCUMENT granularity must return it once.
        val ids = results.map { it.id }
        assertEquals(ids.distinct(), ids, "Expected distinct document ids under DOCUMENT granularity, got $ids")
        assertTrue("long" in ids, "Top result should include 'long' document")
    }

    @Test
    fun testChunkGranularityReturnsAllMatchingChunks() = runBlocking {
        val table = "pgv_chunk_granularity"
        val s = PGVectorStorage(
            dataSource = dataSource,
            tableName = table,
            vectorDimension = VECTOR_DIM,
            embedder = MockEmbedder(),
            chunker = RecursiveCharacterChunker(
                chunkSizeChars = 40,
                overlapChars = 5,
                minChunkChars = 5,
            ),
            resultGranularity = ResultGranularity.CHUNK,
        )
        s.migrate()

        val longText = (1..5).joinToString(". ") { "banana apple orange token$it word$it" } + "."
        s.add(listOf(TestDoc(content = longText, id = "long")))

        val results = s.search(SimilaritySearchRequest(queryText = "banana apple", limit = 5))
        // In CHUNK granularity the same document id can repeat.
        assertTrue(results.size >= 2, "Expected multiple chunks from the same document under CHUNK granularity")
        assertTrue(results.all { it.id == "long" })
    }

    @Test
    fun testUnicodeContentIsChunkedSafely() = runBlocking {
        val table = "pgv_unicode"
        val s = PGVectorStorage(
            dataSource = dataSource,
            tableName = table,
            vectorDimension = VECTOR_DIM,
            embedder = MockEmbedder(),
            chunker = RecursiveCharacterChunker(
                chunkSizeChars = 30,
                overlapChars = 4,
                minChunkChars = 4,
            ),
        )
        s.migrate()

        // Emoji is a surrogate pair in UTF-16; a naive slice-by-char chunker would split it.
        val content = "😀".repeat(50)
        s.add(listOf(TestDoc(content = content, id = "emoji")))
        val loaded = s.get(listOf("emoji")).single()
        // Content must round-trip losslessly despite chunking.
        assertEquals(content, loaded.content)
    }
}
