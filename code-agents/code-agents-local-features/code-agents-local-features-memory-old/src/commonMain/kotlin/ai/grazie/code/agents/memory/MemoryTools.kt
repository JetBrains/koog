package ai.grazie.code.agents.memory

import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.code.agents.memory.chunk.BasicMemoryChunk
import ai.grazie.code.agents.memory.chunk.MemoryChunk
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Collection of tools for memory operations.
 * Provides separate tools for storing and retrieving memory chunks.
 */
object MemoryTools {
    /**
     * Tool for retrieving memory chunks.
     * Provides methods for semantic search and tag-based retrieval.
     */
    class Retrieve(private val memory: Memory) : SimpleTool<Retrieve.Args>() {
        @Serializable
        data class Args(
            val query: String,
            val limit: Int = 5
        ) : Tool.Args {

        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "memory-retrieve",
            description = "Tool for retrieving memory chunks. Provides methods for semantic search and tag-based retrieval.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "The semantic search query",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "limit",
                    description = "Maximum number of chunks to retrieve",
                    type = ToolParameterType.Integer
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            val chunks = memory.search(args.query, args.limit)
            return formatChunks(chunks)
        }

        private fun formatChunks(chunks: List<MemoryChunk>): String {
            if (chunks.isEmpty()) {
                return "No memory chunks found"
            }

            return chunks.joinToString("\n\n") { chunk ->
                """
                ID: ${chunk.id}
                Tags: ${chunk.tags.joinToString(", ")}
                Content: ${chunk.content}
                """.trimIndent()
            }
        }
    }

    /**
     * Tool for storing memory chunks.
     * Provides methods for storing content and clearing memory.
     */
    class Store(private val memory: Memory) : SimpleTool<Store.Args>() {
        @Serializable
        data class Args(
            val content: String,
            val tags: List<String> = emptyList()
        ) : Tool.Args

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "memory-store",
            description = "Tool for storing memory chunks. Provides methods for storing content and clearing memory.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "content",
                    description = "The content to store",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "tags",
                    description = "The tags to associate with the content (for content operation)",
                    type = ToolParameterType.List(ToolParameterType.String)
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            val chunk = BasicMemoryChunk(
                id = generateId(),
                content = args.content,
                tags = args.tags
            )
            memory.store(chunk)
            return "Memory chunk stored with ID: ${chunk.id}"
        }

        private fun generateId(): String {
            return "mem-${Random.nextLong()}-${Random.nextInt(0, 10000)}"
        }
    }
}