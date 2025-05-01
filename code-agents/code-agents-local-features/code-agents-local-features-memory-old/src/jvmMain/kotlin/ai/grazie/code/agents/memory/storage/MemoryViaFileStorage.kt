package ai.grazie.code.agents.memory.storage

import ai.grazie.code.agents.memory.chunk.BasicMemoryChunk
import ai.grazie.code.agents.memory.chunk.MemoryChunk
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.create
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MemoryViaFileStorage(private val file: File) : MemoryStorage {
    companion object {
        private val logger = LoggerFactory.create(MemoryViaFileStorage::class)

        val host = MemoryViaFileStorage(
            File(System.getProperty("user.home"), ".dev/host_memory.json")
        )
    }

    private val chunks = mutableListOf<MemoryChunk>()
    private val lock = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        if (file.exists()) {
            val content = file.readText()
            if (content.isNotBlank()) {
                try {
                    val loadedChunks: List<BasicMemoryChunk> = json.decodeFromString(content)
                    chunks.addAll(loadedChunks)
                } catch (e: Exception) {
                    logger.error(e) { "Error loading memory chunks from file" }
                }
            }
        }
    }

    override suspend fun store(chunk: MemoryChunk): MemoryChunk = withContext(Dispatchers.IO) {
        lock.withLock {
            chunks.removeIf { it.id == chunk.id }
            chunks.add(chunk)
            persist()
            chunk
        }
    }

    override suspend fun retrieve(id: String): MemoryChunk? = withContext(Dispatchers.IO) {
        lock.withLock {
            chunks.find { it.id == id }
        }
    }

    override suspend fun all(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        lock.withLock {
            chunks.toList()
        }
    }

    override suspend fun retrieve(tags: List<String>, matchAll: Boolean): List<MemoryChunk> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                chunks.filter { chunk ->
                    if (matchAll) {
                        tags.all { tag -> chunk.tags.contains(tag) }
                    } else {
                        tags.any { tag -> chunk.tags.contains(tag) }
                    }
                }
            }
        }

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val removed = chunks.removeIf { it.id == id }
            if (removed) {
                persist()
            }
            removed
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            chunks.clear()
            persist()
        }
    }

    private fun persist() {
        try {
            file.writeText(json.encodeToString(chunks))
        } catch (e: Exception) {
            logger.error(e) { "Error writing memory chunks to file" }
        }
    }
}