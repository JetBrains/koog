package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryRecordStorageTest {
    private val defaultNamespace = "default"

    @Test
    fun testAddRecordsWithoutId() = runTest {
        val repository = InMemoryRecordStorage()

        repository.add(
            listOf(
                MemoryRecord(content = "Test content 1"),
                MemoryRecord(content = "Test content 2")
            ),
            defaultNamespace,
        )

        assertEquals(2, repository.size())
    }

    @Test
    fun testAddRecordsWithId() = runTest {
        val repository = InMemoryRecordStorage()

        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Test content 1"),
                MemoryRecord(id = "id-2", content = "Test content 2")
            ),
            defaultNamespace,
        )

        val searchResults =
            repository.search(KeywordSearchRequest(queryText = "content"), defaultNamespace).map { it.document.id }
        assertEquals(2, searchResults.size)
        assertContains(searchResults, "id-1")
        assertContains(searchResults, "id-2")
    }

    @Test
    fun testSearchByKeyword() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
                MemoryRecord(id = "id-3", content = "Python is popular for data science")
            ),
            defaultNamespace,
        )

        val results = repository.search(KeywordSearchRequest(queryText = "programming"), defaultNamespace)

        assertEquals(2, results.size)
        assertTrue(results.all { it.document.content.contains("programming") })
    }

    @Test
    fun testSearchWithLimit() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Test content 1"),
                MemoryRecord(id = "id-2", content = "Test content 2"),
                MemoryRecord(id = "id-3", content = "Test content 3")
            ),
            defaultNamespace,
        )

        val results = repository.search(KeywordSearchRequest(queryText = "Test", limit = 2), defaultNamespace)

        assertEquals(2, results.size)
    }

    @Test
    fun testSearchCaseInsensitive() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "KOTLIN is great"),
                MemoryRecord(id = "id-2", content = "kotlin is awesome")
            ),
            defaultNamespace,
        )

        val results = repository.search(KeywordSearchRequest(queryText = "Kotlin"), defaultNamespace)

        assertEquals(2, results.size)
    }

    @Test
    fun testGetByIds() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "First record"),
                MemoryRecord(id = "id-2", content = "Second record"),
                MemoryRecord(id = "id-3", content = "Third record")
            ),
            defaultNamespace,
        )

        val results = repository.get(listOf("id-1", "id-3"), defaultNamespace)

        assertEquals(2, results.size)
        assertEquals("id-1", results[0].id)
        assertEquals("id-3", results[1].id)
    }

    @Test
    fun testGetByIdsReturnsOnlyExisting() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Existing")), defaultNamespace)

        val results = repository.get(listOf("id-1", "non-existent"), defaultNamespace)

        assertEquals(1, results.size)
        assertEquals("id-1", results[0].id)
    }

    @Test
    fun testDeleteByIds() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "First record"),
                MemoryRecord(id = "id-2", content = "Second record")
            ),
            defaultNamespace,
        )

        val deleted = repository.delete(listOf("id-1"), defaultNamespace)

        assertEquals(listOf("id-1"), deleted)
        assertEquals(1, repository.size(defaultNamespace))
        assertTrue(repository.get(listOf("id-1"), defaultNamespace).isEmpty())
    }

    @Test
    fun testDeleteNonExistentReturnsEmpty() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Record")), defaultNamespace)

        val deleted = repository.delete(listOf("non-existent"), defaultNamespace)

        assertTrue(deleted.isEmpty())
        assertEquals(1, repository.size(defaultNamespace))
    }

    @Test
    fun testUpdateExistingRecord() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Original content")), defaultNamespace)

        val updated = repository.update(
            mapOf("id-1" to MemoryRecord(id = "id-1", content = "Updated content")),
            defaultNamespace
        )

        assertEquals(listOf("id-1"), updated)
        val record = repository.get(listOf("id-1"), defaultNamespace).first()
        assertEquals("Updated content", record.content)
    }

    @Test
    fun testUpdateNonExistentRecordIsIgnored() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Original")), defaultNamespace)

        val updated = repository.update(
            mapOf("non-existent" to MemoryRecord(content = "New content")),
            defaultNamespace
        )

        assertTrue(updated.isEmpty())
        assertEquals(1, repository.size(defaultNamespace))
    }

    @Test
    fun testAddReturnsIds() = runTest {
        val repository = InMemoryRecordStorage()

        val ids = repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "First"),
                MemoryRecord(id = "id-2", content = "Second")
            ),
            defaultNamespace,
        )

        assertEquals(listOf("id-1", "id-2"), ids)
    }

    @Test
    fun testAddWithoutIdGeneratesId() = runTest {
        val repository = InMemoryRecordStorage()

        val ids = repository.add(
            listOf(MemoryRecord(content = "No id record")),
            defaultNamespace,
        )

        assertEquals(1, ids.size)
        assertTrue(ids[0].isNotBlank())
    }

    @Test
    fun testSearchBySimilarityRequest() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
                MemoryRecord(id = "id-3", content = "Python is popular for data science")
            ),
            defaultNamespace,
        )

        val results = repository.search(SimilaritySearchRequest(queryText = "programming"), defaultNamespace)

        assertEquals(2, results.size)
        assertTrue(results.all { it.document.content.contains("programming") })
    }

    @Test
    fun testSearchBySimilarityRequestWithLimit() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Test content 1"),
                MemoryRecord(id = "id-2", content = "Test content 2"),
                MemoryRecord(id = "id-3", content = "Test content 3")
            ),
            defaultNamespace,
        )

        val results = repository.search(SimilaritySearchRequest(queryText = "Test", limit = 2), defaultNamespace)

        assertEquals(2, results.size)
    }

    @Test
    fun testSearchBySimilarityRequestCaseInsensitive() = runTest {
        val repository = InMemoryRecordStorage()
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "KOTLIN is great"),
                MemoryRecord(id = "id-2", content = "kotlin is awesome")
            ),
            defaultNamespace,
        )

        val results = repository.search(SimilaritySearchRequest(queryText = "Kotlin"), defaultNamespace)

        assertEquals(2, results.size)
    }
}
