package ai.grazie.code.agents.local.memory.llm

import ai.grazie.code.agents.local.memory.model.BasicMemoryChunk
import ai.grazie.code.agents.local.memory.model.MemoryChunk
import ai.grazie.code.agents.local.memory.prompts.MemoryPrompts
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MemoryChunkingFeature(
    private val executor: CodePromptExecutor
) {
    /**
     * Analyzes the content and splits it into memory chunks with tags.
     *
     * @param content The content to analyze and chunk
     * @return List of memory chunks with content and tags
     */
    suspend fun execute(content: String): List<MemoryChunk> {
        val prompt = prompt(
            JetBrainsAIModels.Google.Flash2_0,
            "code-engine-memory-chunking"
        ) {
            system(MemoryPrompts.ChunkContent)
            user {
                +"## CONTENT"
                +content
            }
        }

        val response = executor.execute(prompt)
        val json = Json.parseToJsonElement(response).jsonObject
        val chunks = json["chunks"]?.jsonArray ?: return emptyList()

        return chunks.mapNotNull { chunk ->
            val chunkObj = chunk.jsonObject
            val content = chunkObj["content"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val tags = chunkObj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: return@mapNotNull null

            BasicMemoryChunk(
                UUID.random().toString(),
                content,
                tags
            )
        }
    }
}
