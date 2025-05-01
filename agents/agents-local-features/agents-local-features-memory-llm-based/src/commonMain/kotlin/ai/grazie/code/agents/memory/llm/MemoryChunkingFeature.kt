package ai.grazie.code.agents.memory.llm

import ai.grazie.code.agents.memory.chunk.BasicMemoryChunk
import ai.grazie.code.agents.memory.chunk.MemoryChunk
import ai.grazie.code.prompt.markdown.markdown
import ai.grazie.code.prompt.structure.executeStructured
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.grazie.code.prompt.structure.json.LLMDescription
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MemoryChunkingFeature(
    private val executor: CodePromptExecutor
) {
    /**
     * Data class for the LLM response when generating memory chunks.
     */
    @Serializable
    private data class LLMMemoryCollection(val chunks: List<LLMMemoryChunk>) {
        companion object {
            val structure = JsonStructuredData.createJsonStructure<LLMMemoryCollection>(
                id = "MemoryCollection",
                serializer = serializer(),
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
        @SerialName("MemoryChunk")
        data class LLMMemoryChunk(
            @LLMDescription("The content of the memory chunk")
            val content: String,
            @LLMDescription("The tags associated with the memory chunk")
            val tags: List<String>
        )
    }


    /**
     * Analyzes the current state of the task graph and determines if replanning is needed.
     *
     * @return Analysis result with replanning decision and observations
     */
    suspend fun execute(content: String): List<MemoryChunk> {
        val prompt = prompt(
            OllamaModels.Meta.LLAMA_3_2,
            "code-engine-memory-chunking",
            LLMParams(schema = LLMMemoryCollection.structure.schema)
        ) {
            system {
                markdown {
                    +"You are agent responsible for converting a provided content into structured memory chunks."
                    newline()

                    h2("PROCESS")
                    +"You have to analyze provided content and suggest best chunking of it into structured memory chunks."
                    +"Think deeply about the tags, they should represent the main information and the context of the content, while being concise"
                }
            }
            user {
                markdown {
                    h2("CONTENT")
                    blockquote(content)
                }
            }
        }

        val response = executor.executeStructured(prompt, LLMMemoryCollection.structure, retries = 3).structure
        return response.chunks.map {
            BasicMemoryChunk(
                UUID.random().toString(),
                it.content,
                it.tags,
            )
        }
    }
}