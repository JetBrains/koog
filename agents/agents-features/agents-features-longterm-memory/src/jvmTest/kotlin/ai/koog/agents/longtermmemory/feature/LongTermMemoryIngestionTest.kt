package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.longtermmemory.ingestion.IngestionTiming
import ai.koog.agents.longtermmemory.ingestion.extraction.FilteringMemoryRecordExtractor
import ai.koog.agents.longtermmemory.ingestion.extraction.MemoryRecordExtractor
import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for LongTermMemory ingestion (IngestionSettings): persisting messages into memory storage.
 */
@OptIn(ExperimentalAgentsApi::class)
class LongTermMemoryIngestionTest {
    private val defaultNamespace = "default"

    private val defaultAgentConfig = AIAgentConfig(
        prompt = prompt("test") { system("You are a helpful assistant") },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 10
    )

    private val nonStreamingStrategy =
        strategy<String, String>("ingestion-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

    private val streamingStrategy =
        strategy<String, String>("ingestion-streaming-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequestStreaming(name = "llm-node")
            edge(nodeStart forwardTo llmNode)
            edge(
                llmNode forwardTo nodeFinish transformed { flow ->
                    flow.toList().filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text }
                }
            )
        }

    private fun streamingExecutor(vararg frames: String): PromptExecutor = object : PromptExecutor {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            return listOf(Message.Assistant("non-streaming", ResponseMetaInfo.Empty))
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
            flow {
                for (frame in frames) emit(StreamFrame.TextDelta(frame))
                emit(StreamFrame.TextComplete(frames.joinToString("")))
                emit(StreamFrame.End("stop"))
            }

        override suspend fun moderate(prompt: Prompt, model: LLModel) =
            throw UnsupportedOperationException("Not needed")

        override fun close() {}
    }

    // ==========================================
    // Default FilteringMemoryRecordExtractor (User + Assistant)
    // ==========================================

    @Test
    fun `default extractor stores both user and assistant messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Assistant knows about Kotlin coroutines").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor()
                }
            }
        }

        agent.run("Tell me about coroutines")

        assertTrue(storage.size() >= 2, "Both user and assistant messages should be stored")
        val results = storage.search(KeywordSearchRequest(query = "coroutines"), defaultNamespace)
        assertTrue(
            results.any { it.record.content.contains("Kotlin coroutines") },
            "Assistant message should be stored"
        )
        val userResults = storage.search(KeywordSearchRequest(query = "Tell me about"), defaultNamespace)
        assertTrue(
            userResults.any { it.record.content.contains("Tell me about coroutines") },
            "User message should be stored"
        )
    }

    // ==========================================
    // Role filtering
    // ==========================================

    @Test
    fun `assistant-only extractor stores only assistant messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("This is the assistant response to store").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("Hello")

        assertTrue(storage.size() > 0, "At least one record should be stored")
        val results = storage.search(KeywordSearchRequest(query = "assistant response"), defaultNamespace)
        assertTrue(
            results.any { it.record.content.contains("assistant response to store") },
            "Assistant response should be stored"
        )
    }

    @Test
    fun `user-only extractor stores only user messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Assistant reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.User))
                    timing = IngestionTiming.ON_LLM_CALL
                }
            }
        }

        agent.run("User question about Kotlin")

        assertTrue(storage.size() > 0, "At least one user message should be stored")
        val results = storage.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)
        assertTrue(
            results.any { it.record.content.contains("User question about Kotlin") },
            "User message should be stored"
        )
        val assistantResults = storage.search(KeywordSearchRequest(query = "Assistant"), defaultNamespace)
        assertTrue(
            assistantResults.none { it.record.content.contains("Assistant reply") },
            "Assistant messages should NOT be stored"
        )
    }

    @Test
    fun `assistant-only extractor excludes user messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Kotlin is a modern language").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(messageRolesToExtract = setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("What is Kotlin?")

        val allResults = storage.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)
        assertTrue(
            allResults.none { it.record.content.contains("What is Kotlin") },
            "User message should NOT be stored"
        )
        assertTrue(
            allResults.any { it.record.content.contains("Kotlin is a modern language") },
            "Assistant message should be stored"
        )
    }

    // ==========================================
    // No ingestion when not configured
    // ==========================================

    @Test
    fun `no messages stored when ingestion is not configured`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("This response should NOT be stored").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                // No ingestion configured
            }
        }

        agent.run("Hello")

        assertEquals(0, storage.size(), "No records should be stored when ingestion is not configured")
    }

    @Test
    fun `no messages stored in streaming mode when ingestion is not configured`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = streamingExecutor("This should NOT be stored")

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = streamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {}
        }

        agent.run("Hello")

        assertEquals(
            0,
            storage.size(),
            "No records should be stored in streaming mode when ingestion is not configured"
        )
    }

    // ==========================================
    // Streaming ingestion
    // ==========================================

    @Test
    fun `streaming frames are ingested when configured`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = streamingExecutor("Hello ", "world", "!")

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = streamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("Hello")

        assertEquals(1, storage.size(), "Streaming frames should be stored as a memory record")
        val results = storage.search(KeywordSearchRequest(query = "Hello world"), defaultNamespace)
        assertEquals(1, results.size)
        assertTrue(
            results.first().record.content.contains("Hello world!"),
            "Concatenated streaming content should be stored"
        )
    }

    @Test
    fun `user messages are ingested during streaming with ON_LLM_CALL timing`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = streamingExecutor("Streaming reply")

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = streamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.User))
                    timing = IngestionTiming.ON_LLM_CALL
                }
            }
        }

        agent.run("User streaming question about Kotlin")

        assertTrue(storage.size() > 0, "User message should be stored during streaming")
        val results = storage.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)
        assertTrue(results.any { it.record.content.contains("User streaming question about Kotlin") })
        val streamResults = storage.search(KeywordSearchRequest(query = "Streaming reply"), defaultNamespace)
        assertTrue(
            streamResults.none { it.record.content.contains("Streaming reply") },
            "Streaming assistant response should NOT be stored"
        )
    }

    // ==========================================
    // IngestionTiming.ON_AGENT_COMPLETION
    // ==========================================

    @Test
    fun `ON_AGENT_COMPLETION stores messages after agent run completes`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("This is the assistant response stored on completion").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.Assistant))
                    timing = IngestionTiming.ON_AGENT_COMPLETION
                }
            }
        }

        agent.run("Hello")

        assertTrue(storage.size() > 0, "Records should be stored after agent completion")
        val results =
            storage.search(KeywordSearchRequest(query = "assistant response stored on completion"), defaultNamespace)
        assertTrue(results.any { it.record.content.contains("assistant response stored on completion") })
    }

    @Test
    @Timeout(5)
    fun `ON_AGENT_COMPLETION does not store messages during LLM call`() = runTest {
        val storage = InMemoryRecordStorage()
        var storageSizeDuringLLMCall = -1

        val executor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                storageSizeDuringLLMCall = storage.size()
                return listOf(Message.Assistant("Response that should not be stored yet", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> =
                throw UnsupportedOperationException("Not needed")

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed")

            override fun close() {}
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.Assistant))
                    timing = IngestionTiming.ON_AGENT_COMPLETION
                }
            }
        }

        agent.run("Hello")

        assertEquals(
            0,
            storageSizeDuringLLMCall,
            "No records should be stored during LLM call with ON_COMPLETION timing"
        )
        assertTrue(storage.size() > 0, "Records should be stored after agent completion")
    }

    @Test
    fun `ON_AGENT_COMPLETION stores user messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Assistant reply").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.User))
                    timing = IngestionTiming.ON_AGENT_COMPLETION
                }
            }
        }

        agent.run("User question about Kotlin")

        assertTrue(storage.size() > 0, "User message should be stored on completion")
        val results = storage.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)
        assertTrue(results.any { it.record.content.contains("User question about Kotlin") })
        assertTrue(
            results.none { it.record.content.contains("Assistant reply") },
            "Assistant messages should NOT be stored"
        )
    }

    @Test
    fun `ON_AGENT_COMPLETION stores both user and assistant messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Assistant response about Kotlin").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = FilteringMemoryRecordExtractor(setOf(Message.Role.User, Message.Role.Assistant))
                    timing = IngestionTiming.ON_AGENT_COMPLETION
                }
            }
        }

        agent.run("User question about Kotlin")

        assertTrue(storage.size() >= 2, "Both user and assistant records should be stored")
        val results = storage.search(KeywordSearchRequest(query = "Kotlin"), defaultNamespace)
        assertTrue(
            results.any { it.record.content.contains("User question about Kotlin") },
            "User message should be stored"
        )
        assertTrue(
            results.any { it.record.content.contains("Assistant response about Kotlin") },
            "Assistant message should be stored"
        )
    }

    // ==========================================
    // Custom MemoryRecordExtractor
    // ==========================================

    @Test
    fun `custom extractor transforms content before storing`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("First sentence. Second sentence. Third sentence.").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = MemoryRecordExtractor { messages ->
                        messages.filter { it.role == Message.Role.Assistant }
                            .flatMap { it.content.split(". ") }
                            .map { it.trim().removeSuffix(".") }
                            .filter { it.isNotBlank() }
                            .map { MemoryRecord(content = it) }
                    }
                }
            }
        }

        agent.run("Hello")

        assertEquals(3, storage.size(), "Custom extractor should split into 3 separate records")
        val results = storage.search(KeywordSearchRequest(query = "sentence"), defaultNamespace)
        assertTrue(results.any { it.record.content.contains("First sentence") })
        assertTrue(results.any { it.record.content.contains("Second sentence") })
        assertTrue(results.any { it.record.content.contains("Third sentence") })
    }

    @Test
    fun `custom extractor that uppercases content`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("The answer is 42").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = MemoryRecordExtractor { messages ->
                        messages
                            .filter { it.role == Message.Role.Assistant }
                            .map { MemoryRecord(content = it.content.uppercase()) }
                    }
                }
            }
        }

        agent.run("What is the answer?")

        assertTrue(storage.size() > 0, "At least one record should be stored")
        val results = storage.search(KeywordSearchRequest(query = "ANSWER"), defaultNamespace)
        assertTrue(
            results.any { it.record.content == "THE ANSWER IS 42" },
            "Custom extractor should have uppercased the content"
        )
    }

    // ==========================================
    // Edge case: extractor returns empty list
    // ==========================================

    @Test
    fun `extractor returning empty list stores nothing`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor {
            mockLLMAnswer("Some response").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    extractor = MemoryRecordExtractor { emptyList() }
                }
            }
        }

        agent.run("Hello")

        assertEquals(0, storage.size(), "No records should be stored when extractor returns empty list")
    }
}
