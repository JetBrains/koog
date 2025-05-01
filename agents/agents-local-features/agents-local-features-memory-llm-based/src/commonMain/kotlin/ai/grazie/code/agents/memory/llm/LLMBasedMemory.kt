package ai.grazie.code.agents.memory.llm

import ai.grazie.code.agents.memory.Memory
import ai.grazie.code.agents.memory.chunk.MemoryChunk
import ai.grazie.code.agents.memory.storage.MemoryStorage
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor

/**
 * Implementation of Memory that uses an LLM (GeminiFlash2_0) to generate memory chunks with tags.
 * This implementation stores memory chunks in a provided storage and uses the LLM to generate
 * tags and enhance the content when storing new chunks.
 */
class LLMBasedMemory(
    private val storage: MemoryStorage,
    private val executor: CodePromptExecutor
) : Memory {
    private val chunking = MemoryChunkingFeature(executor)

    override suspend fun search(query: String, limit: Int): List<MemoryChunk> {
        return emptyList()
    }

    override suspend fun tags(tags: List<String>, matchAll: Boolean, limit: Int): List<MemoryChunk> {
        return storage.retrieve(tags, matchAll).take(limit)
    }

    override suspend fun store(content: String): List<MemoryChunk> {
        val chunks = chunking.execute(content)
        return chunks.map { store(it) }
    }

    override suspend fun store(chunk: MemoryChunk): MemoryChunk {
        return storage.store(chunk)
    }

    override suspend fun clear() {
        storage.clear()
    }

}
