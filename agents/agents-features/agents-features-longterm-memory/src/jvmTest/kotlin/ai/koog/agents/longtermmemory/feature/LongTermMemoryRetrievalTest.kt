package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor
import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.search.SearchStrategy
import ai.koog.agents.longtermmemory.retrieval.search.SimilaritySearchStrategy
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for LongTermMemory retrieval (RetrievalSettings): prompt augmentation via storage search.
 */
class LongTermMemoryRetrievalTest {
    private val defaultNamespace = "default"

    private val defaultAgentConfig = AIAgentConfig(
        prompt = prompt("test") { system("You are a helpful assistant") },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 10
    )

    private val nonStreamingStrategy =
        strategy<String, String>("retrieval-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

    private val streamingStrategy =
        strategy<String, String>("retrieval-streaming-test", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequestStreaming(name = "llm-node")
            edge(nodeStart forwardTo llmNode)
            edge(
                llmNode forwardTo nodeFinish transformed { flow ->
                    flow.toList().filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text }
                }
            )
        }

    /**
     * Creates a PromptExecutor that captures the full prompt content for inspection.
     * [onPrompt] receives the joined content of all prompt messages and returns the response text.
     */
    private fun promptCapturingExecutor(onPrompt: (String) -> String): PromptExecutor = object : PromptExecutor() {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> {
            val allContent = prompt.messages.joinToString("\n") { it.content }
            return listOf(Message.Assistant(onPrompt(allContent), ResponseMetaInfo.Empty))
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
            flow {
                val allContent = prompt.messages.joinToString("\n") { it.content }
                emit(StreamFrame.TextDelta(onPrompt(allContent)))
                emit(StreamFrame.End("stop"))
            }

        override suspend fun moderate(prompt: Prompt, model: LLModel) =
            throw UnsupportedOperationException("Not needed")

        override fun close() {}
    }

    // ==========================================
    // Prompt augmentation with search request builder
    // ==========================================

    @Test
    @Timeout(5)
    fun `prompt is augmented with storage results via search request builder`() = runTest {
        var searchCalled = false
        val storage = InMemoryRecordStorage()
        storage.add(
            listOf(
                MemoryRecord(content = "Kotlin was developed by JetBrains"),
                MemoryRecord(content = "Kotlin is 100% interoperable with Java")
            ),
            defaultNamespace
        )

        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Kotlin was developed by JetBrains") && content.contains("Relevant information")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SearchStrategy { _ ->
                        searchCalled = true
                        KeywordSearchRequest(queryText = "Kotlin")
                    }
                }
            }
        }

        val result = agent.run("Tell me about Kotlin")

        assertTrue(searchCalled, "Search function should have been called")
        assertTrue(augmented, "Prompt should be augmented with storage context")
        assertEquals("AUGMENTED", result)
    }

    @Test
    @Timeout(5)
    fun `streaming prompt is augmented with storage results`() = runTest {
        val storage = InMemoryRecordStorage()
        storage.add(
            listOf(
                MemoryRecord(content = "Kotlin was developed by JetBrains"),
                MemoryRecord(content = "Kotlin is 100% interoperable with Java")
            ),
            defaultNamespace
        )

        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Kotlin was developed by JetBrains") && content.contains("Relevant information")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = streamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SearchStrategy { _ -> KeywordSearchRequest(queryText = "Kotlin") }
                }
            }
        }

        val result = agent.run("Tell me about Kotlin")

        assertTrue(augmented, "Streaming prompt should be augmented with storage context")
        assertEquals("AUGMENTED", result)
    }

    // ==========================================
    // No augmentation when retrieval is not configured
    // ==========================================

    @Test
    @Timeout(5)
    fun `prompt is not augmented when retrieval is not configured`() = runTest {
        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Relevant information")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                // No retrieval configured
            }
        }

        val result = agent.run("Hello")

        assertFalse(augmented, "Prompt should NOT be augmented when retrieval is not configured")
        assertEquals("NOT_AUGMENTED", result)
    }

    @Test
    @Timeout(5)
    fun `streaming prompt is not augmented when retrieval is not configured`() = runTest {
        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Relevant information")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = streamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {}
        }

        val result = agent.run("Hello")

        assertFalse(augmented, "Streaming prompt should NOT be augmented when retrieval is not configured")
        assertEquals("NOT_AUGMENTED", result)
    }

    // ==========================================
    // Search request builder receives the user query
    // ==========================================

    @Test
    @Timeout(5)
    fun `search request strategy receives the user query`() = runTest {
        var capturedQuery: String? = null

        val storage = InMemoryRecordStorage()
        storage.add(
            listOf(
                MemoryRecord(content = "The weather in Paris is sunny today"),
                MemoryRecord(content = "Kotlin is a programming language")
            ),
            defaultNamespace
        )

        val executor = promptCapturingExecutor { "OK" }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SearchStrategy { query ->
                        capturedQuery = query
                        SimilaritySearchRequest(query)
                    }
                }
            }
        }

        agent.run("weather")

        assertEquals("weather", capturedQuery, "Search strategy should receive the user's query")
    }

    // ==========================================
    // Similarity search builder integration
    // ==========================================

    @Test
    @Timeout(5)
    fun `similaritySearch builder retrieves matching records`() = runTest {
        val storage = InMemoryRecordStorage()
        storage.add(
            listOf(
                MemoryRecord(content = "Kotlin was developed by JetBrains"),
                MemoryRecord(content = "Java is a popular programming language"),
                MemoryRecord(content = "Kotlin coroutines simplify async programming"),
            ),
            defaultNamespace
        )

        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Kotlin was developed by JetBrains")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SimilaritySearchStrategy(topK = 5)
                }
            }
        }

        val result = agent.run("Kotlin")

        assertTrue(augmented, "Prompt should be augmented with similarity search results")
        assertEquals("AUGMENTED", result)
    }

    @Test
    @Timeout(5)
    fun `similaritySearch builder returns no augmentation when query does not match`() = runTest {
        val storage = InMemoryRecordStorage()
        storage.add(
            listOf(
                MemoryRecord(content = "Kotlin was developed by JetBrains"),
                MemoryRecord(content = "Kotlin coroutines simplify async programming"),
            ),
            defaultNamespace
        )

        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Kotlin") && content.contains("JetBrains")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SimilaritySearchStrategy(topK = 5)
                }
            }
        }

        val result = agent.run("Tell me about Python and Django")

        assertFalse(augmented, "Prompt should NOT be augmented when no records match")
        assertEquals("NOT_AUGMENTED", result)
    }

    // ==========================================
    // Empty storage returns no augmentation
    // ==========================================

    @Test
    @Timeout(5)
    fun `empty storage produces no augmentation`() = runTest {
        val storage = InMemoryRecordStorage()

        var augmented = false
        val executor = promptCapturingExecutor { content ->
            augmented = content.contains("Relevant information")
            if (augmented) "AUGMENTED" else "NOT_AUGMENTED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SimilaritySearchStrategy(topK = 5)
                }
            }
        }

        val result = agent.run("Tell me something")

        assertFalse(augmented, "Empty storage should produce no augmentation")
        assertEquals("NOT_AUGMENTED", result)
    }

    // ==========================================
    // End-to-end: ingestion then retrieval
    // ==========================================

    @Test
    @Timeout(5)
    fun `ingested data is retrievable in subsequent agent run`() = runTest {
        val storage = InMemoryRecordStorage()

        // First agent run: ingest data
        val ingestExecutor = promptCapturingExecutor { "Kotlin supports coroutines for async programming" }

        val ingestAgent = AIAgent(
            promptExecutor = ingestExecutor,
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

        ingestAgent.run("Tell me about Kotlin")

        assertTrue(storage.size() > 0, "Data should have been ingested")

        // Second agent run: retrieve the ingested data
        var augmented = false
        val retrieveExecutor = promptCapturingExecutor { content ->
            augmented = content.contains("coroutines")
            if (augmented) "FOUND_CONTEXT" else "NO_CONTEXT"
        }

        val retrieveAgent = AIAgent(
            promptExecutor = retrieveExecutor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = storage
                    searchStrategy = SimilaritySearchStrategy(topK = 5)
                }
            }
        }

        val result = retrieveAgent.run("coroutines")

        assertTrue(augmented, "Previously ingested data should be retrievable")
        assertEquals("FOUND_CONTEXT", result)
    }

    // ==========================================
    // FailurePolicy.FAIL_FAST (default) on retrieval
    // ==========================================

    @Test
    @Timeout(5)
    fun `default FAIL_FAST policy throws LongTermMemoryRetrievalException when storage search fails`() = runTest {
        val storageError = RuntimeException("storage exploded")
        val failingStorage = object : SearchStorage<TextDocument, SearchRequest> {
            override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<TextDocument>> {
                throw storageError
            }
        }

        var llmCalled = false
        val executor = promptCapturingExecutor {
            llmCalled = true
            "SHOULD_NOT_BE_REACHED"
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = nonStreamingStrategy,
            agentConfig = defaultAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(LongTermMemory.Feature) {
                retrieval {
                    this.storage = failingStorage
                    searchStrategy = SearchStrategy { KeywordSearchRequest(queryText = "anything") }
                    // failurePolicy left at default (FAIL_FAST)
                }
            }
        }

        val thrown = assertFailsWith<LongTermMemoryRetrievalException> {
            agent.run("Tell me about Kotlin")
        }

        assertEquals(storageError, thrown.cause, "Original storage error should be preserved as cause")
        assertNotNull(thrown.message, "Exception should carry a message")
        assertFalse(llmCalled, "LLM must not be called when retrieval fails under FAIL_FAST policy")
    }
}
