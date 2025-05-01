package ai.grazie.code.agents.local.memory.llm

import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.grazie.code.prompt.structure.json.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class for the LLM response when generating memory chunks.
 */
@Serializable
@SerialName("LLMMemoryCollection")
data class LLMMemoryCollection(val chunks: List<LLMMemoryChunk>) {
    companion object {
        val structure = JsonStructuredData.createJsonStructure<LLMMemoryCollection>(
            id = "MemoryCollection",
            examples = listOf(
                LLMMemoryCollection(
                    chunks = listOf(
                        LLMMemoryChunk(
                            content = "The user asked about how to implement a binary search algorithm in Python.",
                            tags = listOf("python", "algorithm", "binary search", "implementation")
                        ),
                        LLMMemoryChunk(
                            content = "The repository structure includes a src directory with main and test subdirectories.",
                            tags = listOf("repository", "structure", "src", "test")
                        )
                    )
                )
            ),
        )
    }

    @Serializable
    @SerialName("LLMMemoryChunk")
    data class LLMMemoryChunk(
        @LLMDescription("The content of the memory chunk")
        val content: String,
        @LLMDescription("The tags associated with the memory chunk")
        val tags: List<String>
    )
}
