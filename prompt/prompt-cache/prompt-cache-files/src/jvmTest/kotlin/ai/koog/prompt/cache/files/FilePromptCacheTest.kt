package ai.koog.prompt.cache.files

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.milliseconds
import ai.koog.prompt.cache.model.*

class FilePromptCacheTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var cache: FilePromptCache

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @BeforeEach
    fun setUp() {
        cache = FilePromptCache(tempDir)
    }

    @AfterEach
    fun tearDown() {
        // Clean up any files created during tests
        if (Files.exists(tempDir.resolve("requests"))) {
            Files.walk(tempDir.resolve("requests"))
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun createTestPrompt(content: String): Prompt = prompt("test-id-${content.hashCode()}", clock = testClock) {
        user(content)
    }

    private fun assistantMessage(content: String): Message.Assistant = Message.Assistant(content, ResponseMetaInfo.create(testClock))

    @Disabled("This test is flaky")
    @Test
    fun `test basic cache operations`() = runBlocking {
        // Create a simple prompt and response
        val prompt = createTestPrompt("test prompt")
        val response = listOf(assistantMessage("test response"))

        // Put the response in the cache
        cache.put(prompt, emptyList(), response)

        // Get the response from the cache
        val cachedResponse = cache.get(prompt, emptyList())

        // Verify the response is correct
        assertNotNull(cachedResponse)
        assertEquals(response, cachedResponse)
    }

    @Disabled("This test is flaky")
    @Test
    fun `test file limit enforcement`() = runBlocking {
        // Create a cache with a small file limit
        val smallCache = FilePromptCache(tempDir, maxFiles = 3)

        // Create several prompts and responses
        val prompts = (1..5).map { createTestPrompt("test prompt $it") }
        val responses = (1..5).map { listOf(assistantMessage("test response $it")) }

        // Put all responses in the cache
        prompts.zip(responses).forEach { (prompt, response) ->
            smallCache.put(prompt, emptyList(), response)
            delay(10.milliseconds) // to avoid having the same `now` timestamp for the cached values
        }

        // Check that the number of files is limited to maxFiles
        val requestsDir = tempDir.resolve("requests")
        assertTrue(requestsDir.exists())
        val files = requestsDir.listDirectoryEntries()
        assertEquals(3, files.size, "Cache should contain only 3 files")

        // Verify that the most recently added files are still in the cache
        assertNull(smallCache.get(prompts[0], emptyList()), "Oldest file should be removed")
        assertNull(smallCache.get(prompts[1], emptyList()), "Second oldest file should be removed")
        assertNotNull(smallCache.get(prompts[2], emptyList()), "Third file should still be in cache")
        assertNotNull(smallCache.get(prompts[3], emptyList()), "Fourth file should still be in cache")
        assertNotNull(smallCache.get(prompts[4], emptyList()), "Fifth file should still be in cache")
    }

    @Test
    fun `test least recently accessed files are removed`() = runBlocking {
        // Create a cache with a small file limit
        val smallCache = FilePromptCache(tempDir, maxFiles = 3)

        // Create several prompts and responses
        val prompts = (1..3).map { createTestPrompt("test prompt $it") }
        val responses = (1..3).map { listOf(assistantMessage("test response $it")) }

        // Put all responses in the cache
        prompts.zip(responses).forEach { (prompt, response) ->
            smallCache.put(prompt, emptyList(), response)
        }

        // Access files in reverse order to change access timestamps
        for (i in prompts.indices.reversed()) {
            smallCache.get(prompts[i], emptyList())
        }

        // Add a new file which should trigger removal of least recently accessed
        val newPrompt = createTestPrompt("test prompt new")
        val newResponse = listOf(assistantMessage("test response new"))
        smallCache.put(newPrompt, emptyList(), newResponse)

        // Check that the number of files is still limited to maxFiles
        val requestsDir = tempDir.resolve("requests")
        assertTrue(requestsDir.exists())
        val files = requestsDir.listDirectoryEntries()
        assertEquals(3, files.size, "Cache should contain only 3 files")

        // Verify that the least recently accessed file was removed
        // and the most recently accessed files are still in the cache
        assertNotNull(smallCache.get(prompts[0], emptyList()), "Most recently accessed file should still be in cache")
        assertNotNull(
            smallCache.get(prompts[1], emptyList()),
            "Second most recently accessed file should still be in cache"
        )
        assertNull(smallCache.get(prompts[2], emptyList()), "Least recently accessed file should be removed")
        assertNotNull(smallCache.get(newPrompt, emptyList()), "Newly added file should be in cache")
    }

    @Test
    fun `test default max files value`() {
        val defaultCache = FilePromptCache(tempDir)
        // This is a reflection-based test to verify the default value
        val field = FilePromptCache::class.java.getDeclaredField("maxFiles")
        field.isAccessible = true
        val maxFiles = field.get(defaultCache) as Int
        assertEquals(3000, maxFiles, "Default maxFiles should be 3000")
    }
}
