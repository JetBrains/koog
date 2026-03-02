package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest
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

        val searchResults = repository.search(KeywordSearchRequest(query = "content"), defaultNamespace).map { it.record.id }
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

        val results = repository.search(KeywordSearchRequest(query = "programming"), defaultNamespace)

        assertEquals(2, results.size)
        assertTrue(results.all { it.record.content.contains("programming") })
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

        val results = repository.search(KeywordSearchRequest(query = "Test", limit = 2), defaultNamespace)

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

        val results = repository.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)

        assertEquals(2, results.size)
    }
}
