package ai.grazie.code.agents.memory.storage

import ai.grazie.code.agents.memory.chunk.MemoryChunk

/**
 * Memory storage implementation that stores memory chunks in the application session.
 * The memory is lost when the application is closed.
 */
class MemoryViaMapStorage(private val storage: MutableMap<String, MemoryChunk>) : MemoryStorage {
    companion object {
        val static = MemoryViaMapStorage(mutableMapOf())

        fun new() = MemoryViaMapStorage(mutableMapOf())
    }

    override suspend fun store(chunk: MemoryChunk): MemoryChunk {
        storage[chunk.id] = chunk
        return chunk
    }

    override suspend fun retrieve(id: String): MemoryChunk? = storage[id]

    override suspend fun all(): List<MemoryChunk> = storage.values.toList()

    override suspend fun retrieve(tags: List<String>, matchAll: Boolean): List<MemoryChunk> {
        return if (tags.isEmpty()) {
            all()
        } else {
            storage.values.filter { chunk ->
                if (matchAll) {
                    tags.all { tag -> chunk.tags.contains(tag) }
                } else {
                    tags.any { tag -> chunk.tags.contains(tag) }
                }
            }
        }
    }

    override suspend fun delete(id: String): Boolean = storage.remove(id) != null

    override suspend fun clear() {
        storage.clear()
    }
}