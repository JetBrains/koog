package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor
import ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor
import ai.koog.agents.longtermmemory.model.MemoryRecord
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
import ai.koog.rag.base.storage.search.KeywordSearchRequest
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
class LongTermMemoryIngestionTest {
    private val defaultNamespace = "default"

    private val defaultAgentConfig = AIAgentConfig(
        prompt = prompt("test") { system("You are a helpful assistant") },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 10,
    )

    private val nonStreamingStrategy =
        strategy<String, String>("ingestion-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

    /**
     * Strategy that performs two LLM calls within a single agent run.
     *
     * The first input is used as the first user message; a fixed second user message is
     * appended before the second LLM call. The prompt for the second call therefore
     * contains the first user message and the first assistant response — exactly the
     * "prompt-history replay" scenario that must not lead to duplicate ingestion.
     */
    private val twoCallNonStreamingStrategy =
        strategy<String, String>("ingestion-two-call-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val firstLlmNode by nodeLLMRequest(name = "llm-node-1", allowToolCalls = false)
            val secondLlmNode by nodeLLMRequest(name = "llm-node-2", allowToolCalls = false)
            edge(nodeStart forwardTo firstLlmNode)
            edge(firstLlmNode forwardTo secondLlmNode transformed { "Follow-up question" })
            edge(secondLlmNode forwardTo nodeFinish transformed { it.content })
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

    private fun streamingExecutor(vararg frames: String): PromptExecutor = object : PromptExecutor() {
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
    // Default MessagePassingDocumentExtractor (User + Assistant)
    // ==========================================

    @Test
    fun `default extractor stores both user and assistant messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = MessagePassingDocumentExtractor()
                }
            }
        }

        agent.run("Tell me about coroutines")

        assertTrue(storage.size() >= 2, "Both user and assistant messages should be stored")
        val results = storage.search(KeywordSearchRequest(queryText = "coroutines"), defaultNamespace)
        assertTrue(
            results.any { it.document.content.contains("Kotlin coroutines") },
            "Assistant message should be stored"
        )
        val userResults = storage.search(KeywordSearchRequest(queryText = "Tell me about"), defaultNamespace)
        assertTrue(
            userResults.any { it.document.content.contains("Tell me about coroutines") },
            "User message should be stored"
        )
    }

    // ==========================================
    // Role filtering
    // ==========================================

    @Test
    fun `assistant-only extractor stores only assistant messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = MessagePassingDocumentExtractor(setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("Hello")

        assertTrue(storage.size() > 0, "At least one record should be stored")
        val results = storage.search(KeywordSearchRequest(queryText = "assistant response"), defaultNamespace)
        assertTrue(
            results.any { it.document.content.contains("assistant response to store") },
            "Assistant response should be stored"
        )
    }

    @Test
    fun `user-only extractor stores only user messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = MessagePassingDocumentExtractor(setOf(Message.Role.User))
                }
            }
        }

        agent.run("User question about Kotlin")

        assertTrue(storage.size() > 0, "At least one user message should be stored")
        val results = storage.search(KeywordSearchRequest(queryText = "Kotlin"), defaultNamespace)
        assertTrue(
            results.any { it.document.content.contains("User question about Kotlin") },
            "User message should be stored"
        )
        val assistantResults = storage.search(KeywordSearchRequest(queryText = "Assistant"), defaultNamespace)
        assertTrue(
            assistantResults.none { it.document.content.contains("Assistant reply") },
            "Assistant messages should NOT be stored"
        )
    }

    // ==========================================
    // No ingestion when not configured
    // ==========================================

    @Test
    fun `no messages stored when ingestion is not configured`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
    // On-completion ingestion
    // ==========================================

    @Test
    fun `stores messages after agent run completes`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = MessagePassingDocumentExtractor(setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("Hello")

        assertTrue(storage.size() > 0, "Records should be stored after agent completion")
        val results =
            storage.search(KeywordSearchRequest(queryText = "assistant response stored on completion"), defaultNamespace)
        assertTrue(results.any { it.document.content.contains("assistant response stored on completion") })
    }

    @Test
    @Timeout(5)
    fun `does not store messages during LLM call`() = runTest {
        val storage = InMemoryRecordStorage()
        var storageSizeDuringLLMCall = -1

        val executor = object : PromptExecutor() {
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
                    documentExtractor = MessagePassingDocumentExtractor(setOf(Message.Role.Assistant))
                }
            }
        }

        agent.run("Hello")

        assertEquals(
            0,
            storageSizeDuringLLMCall,
            "No records should be stored during LLM call; ingestion happens only on agent completion"
        )
        assertTrue(storage.size() > 0, "Records should be stored after agent completion")
    }

    // ==========================================
    // Custom DocumentExtractor
    // ==========================================

    @Test
    fun `custom extractor transforms content before storing`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = DocumentExtractor { messages ->
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
        val results = storage.search(KeywordSearchRequest(queryText = "sentence"), defaultNamespace)
        assertTrue(results.any { it.document.content.contains("First sentence") })
        assertTrue(results.any { it.document.content.contains("Second sentence") })
        assertTrue(results.any { it.document.content.contains("Third sentence") })
    }

    // ==========================================
    // Edge case: extractor returns empty list
    // ==========================================

    @Test
    fun `extractor returning empty list stores nothing`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
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
                    documentExtractor = DocumentExtractor { emptyList() }
                }
            }
        }

        agent.run("Hello")

        assertEquals(0, storage.size(), "No records should be stored when extractor returns empty list")
    }

    // ==========================================
    // Multi-call ingestion: prompt history is ingested once on completion
    // ==========================================

    @Test
    fun `two LLM calls with default extractor do not duplicate prompt-history messages`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
            mockLLMAnswer("Second answer about coroutines") onRequestContains "Follow-up question"
            mockLLMAnswer("First answer about coroutines").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = twoCallNonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                ingestion {
                    this.storage = storage
                    documentExtractor = MessagePassingDocumentExtractor()
                }
            }
        }

        agent.run("First question")

        // Expected ingested records: User1, Assistant1, User2, Assistant2 → exactly 4.
        // Ingestion runs once on agent completion over the final prompt history,
        // so each message appears exactly once regardless of how many LLM calls happened.
        assertEquals(
            4,
            storage.size(),
            "Each prompt-history message must be ingested exactly once on agent completion"
        )

        val firstUser = storage.search(KeywordSearchRequest(queryText = "First question"), defaultNamespace)
        assertEquals(1, firstUser.size, "First user message must be stored exactly once")

        val firstAssistant = storage.search(KeywordSearchRequest(queryText = "First answer"), defaultNamespace)
        assertEquals(1, firstAssistant.size, "First assistant message must be stored exactly once")

        val secondUser = storage.search(KeywordSearchRequest(queryText = "Follow-up"), defaultNamespace)
        assertEquals(1, secondUser.size, "Second user message must be stored exactly once")

        val secondAssistant = storage.search(KeywordSearchRequest(queryText = "Second answer"), defaultNamespace)
        assertEquals(1, secondAssistant.size, "Second assistant message must be stored exactly once")
    }

    @Test
    fun `consecutive agent runs each ingest their own messages on completion`() = runTest {
        val storage = InMemoryRecordStorage()

        val executor = getMockExecutor(defaultAgentConfig.serializer) {
            mockLLMAnswer("Stable assistant reply").asDefaultResponse
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
                    documentExtractor = MessagePassingDocumentExtractor()
                }
            }
        }

        agent.run("Question one")
        val sizeAfterFirstRun = storage.size()
        agent.run("Question two")
        val sizeAfterSecondRun = storage.size()

        // Each run is independent and ingests its own prompt history on completion.
        assertTrue(
            sizeAfterSecondRun > sizeAfterFirstRun,
            "Second run must ingest its own messages on completion; " +
                "first=$sizeAfterFirstRun second=$sizeAfterSecondRun"
        )
    }
}
