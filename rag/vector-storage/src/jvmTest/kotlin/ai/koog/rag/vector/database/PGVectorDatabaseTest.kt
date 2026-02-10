package ai.koog.rag.vector.database

import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PGVectorDatabaseTest {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var database: PGVectorDatabase

    @BeforeAll
    fun setUp() {
        // Use pgvector image which has the vector extension pre-installed
        postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()

        // Use smaller vector dimension for tests
        database = PGVectorDatabase(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            tableName = "memory_records_test",
            vectorDimension = 3
        )

        runBlocking {
            database.migrate()
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @BeforeEach
    fun clearTable() {
        runBlocking {
            database.deleteByFilter(filterExpression {
                "id".isNotNull()
            })
        }
    }

    // ==================== CREATE/UPDATE TESTS ====================

    @Test
    fun `add multiple records`() = runBlocking {
        val records = listOf(
            Record.Plain(id = "batch-1", content = "First"),
            Record.Plain(id = "batch-2", content = "Second"),
            Record.Plain(id = "batch-3", content = "Third")
        )

        val result = database.add(records)

        assertTrue(result.isFullySuccessful)
        assertEquals(3, result.successIds.size)
        assertEquals(3, database.getAll(listOf("batch-1", "batch-2", "batch-3")).size)
    }

    @Test
    fun `add record with same id updates existing (upsert)`() = runBlocking {
        val original = Record.Plain(id = "upsert-test", content = "Original")
        database.add(listOf(original))

        val updated = Record.Plain(id = "upsert-test", content = "Updated")
        database.add(listOf(updated))

        val retrieved = database.getAll(listOf("upsert-test")).firstOrNull()
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.content)
    }

    @Test
    fun `update records`() = runBlocking {
        val record = Record.Plain(id = "update-test", content = "Before update")
        database.add(listOf(record))

        val updated = Record.Embedded(
            id = "update-test",
            content = "After update",
            embedding = listOf(0.5f, 0.5f, 0.5f)
        )
        val result = database.update(listOf(updated))

        assertTrue(result.isFullySuccessful)
        val retrieved = database.getAll(listOf("update-test")).firstOrNull()
        assertNotNull(retrieved)
        assertTrue(retrieved is Record.Embedded)
        assertEquals("After update", retrieved.content)
        assertEquals(listOf(0.5f, 0.5f, 0.5f), retrieved.embedding)
    }

    @Test
    fun `update record without id fails`() = runBlocking {
        val record = Record.Plain(content = "No ID")

        val result = database.update(listOf(record))

        assertFalse(result.isFullySuccessful)
        assertEquals(1, result.failedIds.size)
    }

    // ==================== READ TESTS ====================

    @Test
    fun `getAll existing record`() = runBlocking {
        val record = Record.Plain(id = "get-test", content = "Test content")
        database.add(listOf(record))

        val retrieved = database.getAll(listOf("get-test")).firstOrNull()

        assertNotNull(retrieved)
        assertEquals("get-test", retrieved.id)
        assertEquals("Test content", retrieved.content)
    }

    @Test
    fun `getAll non-existing record returns empty list`() = runBlocking {
        val retrieved = database.getAll(listOf("non-existing"))

        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun `getAll returns matching records`() = runBlocking {
        database.add(
            listOf(
                Record.Plain(id = "all-1", content = "First"),
                Record.Plain(id = "all-2", content = "Second"),
                Record.Plain(id = "all-3", content = "Third")
            )
        )

        val resultIds = database.getAll(listOf("all-1", "all-3", "non-existing")).map { it.id }

        assertEquals(2, resultIds.size)
        assertContains(resultIds, "all-1")
        assertContains(resultIds, "all-3")
    }

    @Test
    fun `getAll with empty list returns empty map`() = runBlocking {
        val result = database.getAll(emptyList())

        assertTrue(result.isEmpty())
    }

    // ==================== SEARCH TESTS ====================

    @Test
    fun `vector search returns similar records`() = runBlocking {
        // Add records with embeddings
        database.add(
            listOf(
                Record.Embedded(id = "vec-1", content = "Similar to query", embedding = listOf(0.9f, 0.1f, 0.0f)),
                Record.Embedded(id = "vec-2", content = "Different", embedding = listOf(0.0f, 0.1f, 0.9f)),
                Record.Embedded(id = "vec-3", content = "Also similar", embedding = listOf(0.8f, 0.2f, 0.0f))
            )
        )

        val results = database.search(
            VectorSearchRequest(
                queryVector = listOf(1.0f, 0.0f, 0.0f),
                limit = 2
            )
        )

        assertEquals(2, results.size)
        // Most similar should be first
        assertTrue(results[0].similarity > results[1].similarity)
    }

    @Test
    fun `vector search with similarity threshold filters results`() = runBlocking {
        database.add(
            listOf(
                Record.Embedded(
                    id = "thresh-1",
                    content = "Very similar",
                    embedding = listOf(0.99f, 0.01f, 0.0f)
                ),
                Record.Embedded(id = "thresh-2", content = "Not similar", embedding = listOf(0.0f, 0.0f, 1.0f))
            )
        )

        val results = database.search(
            VectorSearchRequest(
                queryVector = listOf(1.0f, 0.0f, 0.0f),
                limit = 10,
                similarityThreshold = 0.9
            )
        )

        assertEquals(1, results.size)
        assertEquals("thresh-1", results[0].record.id)
    }

    @Test
    fun `keyword search finds matching content`() = runBlocking {
        database.add(
            listOf(
                Record.Plain(id = "kw-1", content = "The quick brown fox"),
                Record.Plain(id = "kw-2", content = "The lazy dog"),
                Record.Plain(id = "kw-3", content = "Another fox story")
            )
        )

        val results = database.search(
            KeywordSearchRequest(
                query = "fox",
                limit = 10
            )
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.record.content.contains("fox", ignoreCase = true) })
    }

    @Test
    fun `similarity search throws exception`() {
        runBlocking {
            assertFailsWith<VectorDatabaseException> {
                database.search(SimilaritySearchRequest(query = "test"))
            }
        }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `deleteAll removes multiple records`() = runBlocking {
        database.add(
            listOf(
                Record.Plain(id = "delall-1", content = "First"),
                Record.Plain(id = "delall-2", content = "Second"),
                Record.Plain(id = "delall-3", content = "Third")
            )
        )

        val result = database.deleteAll(listOf("delall-1", "delall-3"))

        assertEquals(2, result.successIds.size)
        assertEquals(1, database.getAll(listOf("delall-2")).size)
        assertTrue(database.getAll(listOf("delall-1")).isEmpty())
        assertTrue(database.getAll(listOf("delall-3")).isEmpty())
    }

    @Test
    fun `deleteByFilter removes matching records`() = runBlocking {
        database.add(
            listOf(
                Record.Plain(id = "filter-1", content = "Keep this"),
                Record.Plain(id = "filter-2", content = "Delete this"),
                Record.Plain(id = "filter-3", content = "Delete that")
            )
        )

        database.deleteByFilter(filterExpression {
            "id" isIn listOf("filter-2", "filter-3")
        })

        assertEquals(1, database.getAll(listOf("filter-1")).size)
        assertTrue(database.getAll(listOf("filter-2")).isEmpty())
        assertTrue(database.getAll(listOf("filter-3")).isEmpty())
    }

    // ==================== NAMESPACE TESTS ====================

    @Test
    fun `add and get records from custom namespace`() = runBlocking {
        // Migrate the custom namespace table first
        database.migrate("custom_namespace_test")

        // Add to default namespace
        database.add(listOf(Record.Plain(id = "ns-default-1", content = "Default content")))

        // Add to custom namespace
        database.add(
            listOf(Record.Plain(id = "ns-custom-1", content = "Custom content")),
            namespace = "custom_namespace_test"
        )

        // Verify records are isolated by namespace
        val defaultRecords = database.getAll(listOf("ns-default-1", "ns-custom-1"))
        val customRecords =
            database.getAll(listOf("ns-default-1", "ns-custom-1"), namespace = "custom_namespace_test")

        assertEquals(1, defaultRecords.size)
        assertEquals("Default content", defaultRecords.first().content)
        assertEquals(1, customRecords.size)
        assertEquals("Custom content", customRecords.first().content)
    }

    @Test
    fun `search in custom namespace`() = runBlocking {
        database.migrate("search_namespace_test")

        database.add(listOf(Record.Plain(id = "search-default", content = "Default fox")))
        database.add(
            listOf(Record.Plain(id = "search-custom", content = "Custom fox")),
            namespace = "search_namespace_test"
        )

        val defaultResults = database.search(KeywordSearchRequest(query = "fox"))
        val customResults = database.search(KeywordSearchRequest(query = "fox"), namespace = "search_namespace_test")

        assertEquals(1, defaultResults.size)
        assertEquals("Default fox", defaultResults.first().record.content)
        assertEquals(1, customResults.size)
        assertEquals("Custom fox", customResults.first().record.content)
    }

    @Test
    fun `delete from custom namespace`() = runBlocking {
        database.migrate("delete_namespace_test")

        database.add(listOf(Record.Plain(id = "del-default", content = "Default")))
        database.add(
            listOf(Record.Plain(id = "del-custom", content = "Custom")),
            namespace = "delete_namespace_test"
        )

        // Delete from custom namespace
        val result = database.deleteAll(listOf("del-custom"), namespace = "delete_namespace_test")

        assertTrue(result.isFullySuccessful)
        assertEquals(1, database.getAll(listOf("del-default")).size)
        assertTrue(database.getAll(listOf("del-custom"), namespace = "delete_namespace_test").isEmpty())
    }

    @Test
    fun `update in custom namespace`() = runBlocking {
        database.migrate("update_namespace_test")

        database.add(
            listOf(Record.Plain(id = "upd-custom", content = "Original")),
            namespace = "update_namespace_test"
        )

        val result = database.update(
            listOf(Record.Plain(id = "upd-custom", content = "Updated")),
            namespace = "update_namespace_test"
        )

        assertTrue(result.isFullySuccessful)
        val records = database.getAll(listOf("upd-custom"), namespace = "update_namespace_test")
        assertEquals("Updated", records.first().content)
    }
}
