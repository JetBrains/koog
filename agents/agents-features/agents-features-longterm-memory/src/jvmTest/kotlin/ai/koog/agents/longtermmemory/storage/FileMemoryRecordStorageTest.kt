package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileMemoryRecordStorageTest {
    private val defaultNamespace = "default"

    /**
     * Deterministic word-bag embedder: each text is mapped to a vector over a small fixed
     * vocabulary, with each component equal to the count of the corresponding word in the text.
     * Similarity is computed via cosine distance, so [diff] returns `1 - cosineSimilarity`,
     * which is exactly what [ai.koog.rag.vector.storage.EmbeddingStorage] expects.
     */
    private class WordBagEmbedder : Embedder {
        private val vocabulary = listOf(
            "kotlin", "java", "python", "programming", "language",
            "modern", "popular", "data", "science", "bananas",
            "yellow", "fruits", "fun", "general", "is", "a", "also", "are", "in", "for"
        )

        override suspend fun embed(text: String): Vector {
            val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
            val values = vocabulary.map { v -> words.count { it == v }.toDouble() }
            return Vector(values)
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double {
            val a = embedding1.values
            val b = embedding2.values
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            val denom = sqrt(na) * sqrt(nb)
            val cosine = if (denom == 0.0) 0.0 else dot / denom
            return 1.0 - cosine
        }
    }

    private fun newStorage(root: Path): FileMemoryRecordStorage<Path> =
        FileMemoryRecordStorage(
            embedder = WordBagEmbedder(),
            fs = JVMFileSystemProvider.ReadWrite,
            root = root,
        )

    @Test
    fun testAddRecordsWithoutId(@TempDir root: Path) = runTest {
        val repository = newStorage(root)

        val ids = repository.add(
            listOf(
                MemoryRecord(content = "Test content 1"),
                MemoryRecord(content = "Test content 2")
            ),
            defaultNamespace,
        )

        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun testAddRecordsWithId(@TempDir root: Path) = runTest {
        val repository = newStorage(root)

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
    fun testSearchByKeyword(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
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
    fun testSearchWithLimit(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
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
    fun testSearchCaseInsensitive(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
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
    fun testGetByIds(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
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
        val ids = results.map { it.id }
        assertContains(ids, "id-1")
        assertContains(ids, "id-3")
    }

    @Test
    fun testGetByIdsReturnsOnlyExisting(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Existing")), defaultNamespace)

        val results = repository.get(listOf("id-1", "non-existent"), defaultNamespace)

        assertEquals(1, results.size)
        assertEquals("id-1", results[0].id)
    }

    @Test
    fun testDeleteByIds(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "First record"),
                MemoryRecord(id = "id-2", content = "Second record")
            ),
            defaultNamespace,
        )

        val deleted = repository.delete(listOf("id-1"), defaultNamespace)

        assertEquals(listOf("id-1"), deleted)
        assertTrue(repository.get(listOf("id-1"), defaultNamespace).isEmpty())
        assertEquals(1, repository.get(listOf("id-1", "id-2"), defaultNamespace).size)
    }

    @Test
    fun testDeleteNonExistentReturnsEmpty(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(listOf(MemoryRecord(id = "id-1", content = "Record")), defaultNamespace)

        val deleted = repository.delete(listOf("non-existent"), defaultNamespace)

        assertTrue(deleted.isEmpty())
        assertEquals(1, repository.get(listOf("id-1"), defaultNamespace).size)
    }

    @Test
    fun testUpdateExistingRecord(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
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
    fun testAddReturnsIds(@TempDir root: Path) = runTest {
        val repository = newStorage(root)

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
    fun testSearchBySimilarity(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a modern programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
                MemoryRecord(id = "id-3", content = "Bananas are yellow fruits")
            ),
            defaultNamespace,
        )

        val results = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language"),
            defaultNamespace
        )

        // id-3 has no overlap with the query in the vocabulary, so its cosine similarity is 0
        // and EmbeddingStorage drops it via the default minScore.
        val topTwo = results.map { it.document.id }
        assertContains(topTwo, "id-1")
        assertContains(topTwo, "id-2")
        assertEquals("id-1", results.first().document.id)
        assertTrue(results[0].score.value > results[1].score.value)
    }

    @Test
    fun testSearchBySimilarityWithMinScore(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a modern programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
            ),
            defaultNamespace,
        )

        val allResults = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language"),
            defaultNamespace
        )
        val topScore = allResults.first().score.value

        val filtered = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language", minScore = topScore),
            defaultNamespace
        )

        assertEquals(1, filtered.size)
        assertEquals("id-1", filtered[0].document.id)
    }

    @Test
    fun testSearchBySimilarityWithLimitAndOffset(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin is a modern programming language"),
                MemoryRecord(id = "id-2", content = "Java is also a programming language"),
                MemoryRecord(id = "id-3", content = "Programming in general is fun"),
            ),
            defaultNamespace,
        )

        val limited = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language", limit = 1),
            defaultNamespace
        )
        assertEquals(1, limited.size)
        assertEquals("id-1", limited[0].document.id)

        val offset = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language", limit = 1, offset = 1),
            defaultNamespace
        )
        assertEquals(1, offset.size)
        assertEquals("id-2", offset[0].document.id)
    }

    @Test
    fun testSearchBySimilarityExcludesNonOverlapping(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(
            listOf(
                MemoryRecord(id = "id-1", content = "Kotlin programming language"),
                MemoryRecord(id = "id-2", content = "Bananas are yellow fruits"),
            ),
            defaultNamespace,
        )

        // Use a tiny positive minScore to exclude records whose cosine similarity is 0.
        val results = repository.search(
            SimilaritySearchRequest(queryText = "Kotlin programming language", minScore = 1e-9),
            defaultNamespace
        )

        assertEquals(1, results.size)
        assertEquals("id-1", results[0].document.id)
    }

    @Test
    fun testAddWithoutIdGeneratesId(@TempDir root: Path) = runTest {
        val repository = newStorage(root)

        val ids = repository.add(
            listOf(MemoryRecord(content = "No id record")),
            defaultNamespace,
        )

        assertEquals(1, ids.size)
        assertTrue(ids[0].isNotBlank())
    }

    @Test
    fun testNamespacesAreIsolated(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        repository.add(listOf(MemoryRecord(id = "id-1", content = "alpha")), "ns-a")
        repository.add(listOf(MemoryRecord(id = "id-2", content = "beta")), "ns-b")

        val a = repository.search(KeywordSearchRequest(queryText = "alpha"), "ns-a")
        val b = repository.search(KeywordSearchRequest(queryText = "alpha"), "ns-b")

        assertEquals(1, a.size)
        assertEquals("id-1", a[0].document.id)
        assertTrue(b.isEmpty())
    }

    @Test
    fun testRejectsUnsafeNamespace(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        val unsafe = listOf("../escape", "a/b", "..", ".", "", "with space", "name:colon", "CON")
        for (ns in unsafe) {
            assertFailsWith<IllegalArgumentException>("namespace '$ns' must be rejected") {
                repository.add(listOf(MemoryRecord(id = "id-1", content = "x")), ns)
            }
        }
    }

    @Test
    fun testRejectsUnsafeDocumentId(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        val unsafe = listOf("../escape", "a/b", "..", ".", "", "with space", "id:1", "PRN")
        for (id in unsafe) {
            assertFailsWith<IllegalArgumentException>("id '$id' must be rejected") {
                repository.add(listOf(MemoryRecord(id = id, content = "x")), defaultNamespace)
            }
        }
    }

    @Test
    fun testRejectsTooLongSegment(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        val tooLong = "a".repeat(129)
        assertFailsWith<IllegalArgumentException> {
            repository.add(listOf(MemoryRecord(id = tooLong, content = "x")), defaultNamespace)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.add(listOf(MemoryRecord(id = "id-1", content = "x")), tooLong)
        }
    }

    @Test
    fun testUnsafeNamespaceDoesNotCreateDirectory(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        runCatching {
            repository.add(listOf(MemoryRecord(id = "id-1", content = "x")), "../escape")
        }
        // The traversal target must not exist anywhere outside root.
        val escapeTarget = root.parent.resolve("escape")
        assertTrue(!java.nio.file.Files.exists(escapeTarget), "traversal target must not be created")
    }

    @Test
    fun testConcurrentAddAndSearchDoesNotObservePartialState(@TempDir root: Path) = runTest {
        val repository = newStorage(root)
        val writerCount = 8
        val recordsPerWriter = 10
        coroutineScope {
            val writers = (0 until writerCount).map { w ->
                async {
                    repeat(recordsPerWriter) { i ->
                        repository.add(
                            listOf(MemoryRecord(id = "w$w-i$i", content = "content $w $i")),
                            defaultNamespace,
                        )
                    }
                }
            }
            val searchers = (0 until 4).map {
                async {
                    repeat(20) {
                        // Should never throw on partial/empty JSON files.
                        repository.search(KeywordSearchRequest(queryText = "content"), defaultNamespace)
                    }
                }
            }
            (writers + searchers).awaitAll()
        }

        val all = repository.search(KeywordSearchRequest(queryText = "content", limit = 1000), defaultNamespace)
        assertEquals(writerCount * recordsPerWriter, all.size)
        // Every persisted record must have its id baked in (no transient id = null state on disk).
        assertTrue(all.all { it.document.id != null && it.document.id!!.isNotBlank() })
    }
}
