package ai.koog.agents.memory.feature

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
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.rag.vector.database.EphemeralMemoryRecordRepository
import ai.koog.rag.vector.database.KeywordSearchRequest
import ai.koog.rag.vector.database.MemoryRecord
import ai.koog.rag.vector.database.ScoredMemoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for Memory2 feature
 */
@OptIn(ExperimentalAgentsApi::class)
class Memory2Test {

    // ==========================================
    // Tests for createFeature prompt augmentation
    // ==========================================

    /**
     * Test that verifies the createFeature method properly augments the prompt with vector store context.
     * 
     * This test:
     * 1. Configures Memory2 with a searchFunction that returns relevant documents
     * 2. Runs the agent and verifies the LLM receives the augmented prompt containing vector store context
     */
    @Test
    fun testCreateFeatureAugmentsPromptWithVectorStoreContext() = runTest {
        // Track whether the prompt was augmented by checking all messages
        var promptWasAugmented = false
        var searchFunctionCalled = false

        // Create a custom executor that checks the FULL prompt content (not just the last message)
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                // Check all messages in the prompt for augmentation
                val allContent = prompt.messages.joinToString("\n") { it.content }

                val containsKotlinInfo = allContent.contains("Kotlin was developed by JetBrains")
                val containsRelevantInfo = allContent.contains("Relevant information")
                promptWasAugmented = containsKotlinInfo && containsRelevantInfo

                val response = if (promptWasAugmented) "AUGMENTED_WITH_VECTOR_STORE" else "NOT_AUGMENTED"
                return listOf(Message.Assistant(response, ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = throw UnsupportedOperationException("Not needed for this test")

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM
        val strategy =
            strategy<String, String>("test-augment-vector", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { it.content })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured to augment from vector store using retriever
        // The retriever directly returns the relevant documents (simulating a vector store search)
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                retriever = MemoryRecordRetriever { _, _ ->
                    searchFunctionCalled = true
                    // Return relevant documents directly (simulating vector store search results)
                    listOf(
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin was developed by JetBrains"), 1.0),
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin is 100% interoperable with Java"), 0.9)
                    )
                }
            }
        }

        val result = agent.run("Tell me about Kotlin")

        // Verify the search function was called
        assertTrue(searchFunctionCalled, "The search function should have been called")
        // Verify the prompt was augmented with vector store context
        assertTrue(promptWasAugmented, "The prompt should have been augmented with vector store context")
        assertEquals("AUGMENTED_WITH_VECTOR_STORE", result)
    }

    /**
     * Test that verifies the createFeature method does NOT augment the prompt when
     * searchFunction is null.
     */
    @Test
    fun testCreateFeatureDoesNotAugmentWhenSearchFunctionIsNull() = runTest {
        // Track whether the prompt was incorrectly augmented
        var wasAugmented = false

        // Create a mock executor that checks if the prompt contains augmented context
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("INCORRECTLY_AUGMENTED") onCondition { request ->
                wasAugmented = request.contains("Relevant information")
                wasAugmented
            }
            mockLLMAnswer("NOT_AUGMENTED").asDefaultResponse
        }

        // Create a vector store with data (but searchFunction will be null)
        val vectorStore = EphemeralMemoryRecordRepository()
        vectorStore.add(
            listOf(
                MemoryRecord(content = "Some context that should not appear")
            )
        )

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-no-augment", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured with searchFunction = null (default)
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = vectorStore
                // searchFunction is null by default - no augmentation should happen
            }
        }

        val result = agent.run("Hello")

        // Verify the prompt was NOT augmented
        assertTrue(!wasAugmented, "The prompt should NOT have been augmented when searchFunction is null")
        assertEquals("NOT_AUGMENTED", result)
    }

    /**
     * Test that verifies persistAssistantMessagesAsMemoryRecords stores assistant messages.
     */
    @Test
    fun testPersistAssistantMessagesAsMemoryRecords() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()

        // Create a mock executor that returns a specific response
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("This is the assistant response to store").asDefaultResponse
        }

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = true
            }
        }

        agent.run("Hello")

        // Verify that the assistant message was stored in the repository
        val searchResults = memoryRepository.search(KeywordSearchRequest(query = "assistant response"))

        assertTrue(memoryRepository.size() > 0, "At least one record should be stored")
        assertTrue(
            searchResults.any { it.record.content.contains("assistant response to store") },
            "The assistant response should be stored in the repository"
        )
    }

    /**
     * Test that verifies searchFunction is correctly used to retrieve context.
     * This test uses a realistic search function that calls vectorStore.search(KeywordSearchRequest(query))
     * similar to how users would configure it in production.
     */
    @Test
    fun testSearchFunctionIsCorrectlyUsed() = runTest {
        var searchFunctionCalled = false
        var searchQuery: String? = null

        // Create a vector store with pre-populated data
        val vectorStore = EphemeralMemoryRecordRepository()
        vectorStore.add(
            listOf(
                MemoryRecord(content = "The weather in Paris is sunny today"),
                MemoryRecord(content = "Tokyo weather forecast shows rain"),
                MemoryRecord(content = "Kotlin is a programming language")
            )
        )

        // Create a mock executor that checks if the prompt was augmented
        var promptWasAugmented = false
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                val allContent = prompt.messages.joinToString("\n") { it.content }
                promptWasAugmented = allContent.contains("weather") && allContent.contains("Relevant information")
                return listOf(Message.Assistant("Response with context", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = throw UnsupportedOperationException("Not needed for this test")

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM
        val strategy =
            strategy<String, String>("test-search-function", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { it.content })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = vectorStore
                retriever = MemoryRecordRetriever { repo, query ->
                    searchFunctionCalled = true
                    searchQuery = query
                    // This is the realistic pattern users would use:
                    repo.search(KeywordSearchRequest(query))
                }
            }
        }

        agent.run("weather")

        // Verify the search function was called with the user's query
        assertTrue(searchFunctionCalled, "The search function should have been called")
        assertEquals("weather", searchQuery, "The search function should receive the user's query")
        // Verify the prompt was augmented with the search results
        assertTrue(promptWasAugmented, "The prompt should have been augmented with vector store search results")
    }

    /**
     * Test that verifies persistAssistantMessagesAsMemoryRecords=false does not store messages.
     */
    @Test
    fun testPersistAssistantMessagesDisabledDoesNotStore() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()

        // Create a mock executor that returns a specific response
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("This response should NOT be stored").asDefaultResponse
        }

        // Create a simple strategy that calls the LLM
        val strategy = strategy<String, String>("test-no-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
            val llmNode by nodeLLMRequest(name = "llm-node", allowToolCalls = false)
            edge(nodeStart forwardTo llmNode)
            edge(llmNode forwardTo nodeFinish transformed { it.content })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = false // Explicitly disabled
            }
        }

        agent.run("Hello")

        // Verify that no records were stored
        assertEquals(
            0,
            memoryRepository.size(),
            "No records should be stored when persistAssistantMessagesAsMemoryRecords is false"
        )
    }

    // ==========================================
    // Tests for streaming prompt augmentation (interceptLLMStreamingStarting)
    // ==========================================

    /**
     * Test that verifies the interceptLLMStreamingStarting properly augments the prompt with vector store context
     * during streaming LLM calls.
     *
     * This test:
     * 1. Configures Memory2 with a retriever that returns relevant documents
     * 2. Uses executeStreaming and verifies the LLM receives the augmented prompt containing vector store context
     */
    @Test
    fun testStreamingAugmentsPromptWithVectorStoreContext() = runTest {
        // Track whether the prompt was augmented by checking all messages
        var promptWasAugmented = false
        var searchFunctionCalled = false
        var streamingExecuteCalled = false

        // Create a custom executor that checks the FULL prompt content in streaming mode
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                // This should not be called in streaming test
                return listOf(Message.Assistant("Non-streaming response", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                streamingExecuteCalled = true
                // Check all messages in the prompt for augmentation
                val allContent = prompt.messages.joinToString("\n") { it.content }

                val containsKotlinInfo = allContent.contains("Kotlin was developed by JetBrains")
                val containsRelevantInfo = allContent.contains("Relevant information")
                promptWasAugmented = containsKotlinInfo && containsRelevantInfo

                val response = if (promptWasAugmented) "STREAMING_AUGMENTED" else "STREAMING_NOT_AUGMENTED"
                emit(StreamFrame.Append(response))
                emit(StreamFrame.End("stop"))
            }

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM with streaming
        val strategy =
            strategy<String, String>("test-streaming-augment", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequestStreaming(name = "llm-node")
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { flow ->
                    // Collect the streaming frames and extract text content
                    flow.toList().filterIsInstance<StreamFrame.Append>().joinToString("") { it.text }
                })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured to augment from vector store using retriever
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                retriever = MemoryRecordRetriever { _, _ ->
                    searchFunctionCalled = true
                    // Return relevant documents directly (simulating vector store search results)
                    listOf(
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin was developed by JetBrains"), 1.0),
                        ScoredMemoryRecord(MemoryRecord(content = "Kotlin is 100% interoperable with Java"), 0.9)
                    )
                }
            }
        }

        val result = agent.run("Tell me about Kotlin")

        // Verify streaming was used
        assertTrue(streamingExecuteCalled, "The executeStreaming method should have been called")
        // Verify the search function was called
        assertTrue(searchFunctionCalled, "The search function should have been called during streaming")
        // Verify the prompt was augmented with vector store context
        assertTrue(
            promptWasAugmented,
            "The prompt should have been augmented with vector store context during streaming"
        )
        assertEquals("STREAMING_AUGMENTED", result)
    }

    /**
     * Test that verifies the interceptLLMStreamingStarting does NOT augment the prompt when
     * retriever is null during streaming calls.
     */
    @Test
    fun testStreamingDoesNotAugmentWhenRetrieverIsNull() = runTest {
        // Track whether the prompt was incorrectly augmented
        var wasAugmented = false
        var streamingExecuteCalled = false

        // Create a mock executor that checks if the prompt contains augmented context
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                return listOf(Message.Assistant("Non-streaming response", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                streamingExecuteCalled = true
                val allContent = prompt.messages.joinToString("\n") { it.content }
                wasAugmented = allContent.contains("Relevant information")

                val response = if (wasAugmented) "STREAMING_INCORRECTLY_AUGMENTED" else "STREAMING_NOT_AUGMENTED"
                emit(StreamFrame.Append(response))
                emit(StreamFrame.End("stop"))
            }

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a vector store with data (but retriever will be null)
        val vectorStore = EphemeralMemoryRecordRepository()
        vectorStore.add(
            listOf(
                MemoryRecord(content = "Some context that should not appear")
            )
        )

        // Create a simple strategy that calls the LLM with streaming
        val strategy =
            strategy<String, String>("test-streaming-no-augment", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequestStreaming(name = "llm-node")
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { flow ->
                    // Collect the streaming frames and extract text content
                    flow.toList().filterIsInstance<StreamFrame.Append>().joinToString("") { it.text }
                })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        // Create agent with Memory2 configured with retriever = null (default)
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = vectorStore
                // retriever is null by default - no augmentation should happen
            }
        }

        val result = agent.run("Hello")

        // Verify streaming was used
        assertTrue(streamingExecuteCalled, "The executeStreaming method should have been called")
        // Verify the prompt was NOT augmented
        assertTrue(!wasAugmented, "The prompt should NOT have been augmented when retriever is null during streaming")
        assertEquals("STREAMING_NOT_AUGMENTED", result)
    }

    /**
     * Test that verifies persistAssistantResponses stores streaming frames as memory records
     * when streaming completes.
     *
     * This test:
     * 1. Configures Memory2 with persistAssistantResponses = true
     * 2. Uses executeStreaming that emits multiple frames
     * 3. Verifies all frames are stored as a memory record after streaming completes
     */
    @Test
    fun testPersistStreamingFramesAsMemoryRecords() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()
        var streamingExecuteCalled = false

        // Create a mock executor that emits multiple streaming frames
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                return listOf(Message.Assistant("Non-streaming response", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                streamingExecuteCalled = true
                // Emit multiple frames to simulate streaming response
                emit(StreamFrame.Append("Hello"))
                emit(StreamFrame.Append(" world"))
                emit(StreamFrame.Append("!"))
                emit(StreamFrame.End("stop"))
            }

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM with streaming
        val strategy =
            strategy<String, String>("test-streaming-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequestStreaming(name = "llm-node")
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { flow ->
                    flow.toList().filterIsInstance<StreamFrame.Append>().joinToString("") { it.text }
                })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = true
            }
        }

        agent.run("Hello")

        // Verify streaming was used
        assertTrue(streamingExecuteCalled, "The executeStreaming method should have been called")

        // Verify that all streaming frames were stored in the repository
        assertEquals(1, memoryRepository.size(), "All 3 streaming frames should be stored as a memory record")

        // Verify the content of stored records
        val searchResults = memoryRepository.search(KeywordSearchRequest(query = "Hello world"))
        assertEquals(1, searchResults.size)
        assertContains(searchResults.first().record.content, "Hello world!")
    }

    /**
     * Test that verifies persistAssistantResponses=false does NOT store streaming frames.
     */
    @Test
    fun testPersistStreamingFramesDisabledDoesNotStore() = runTest {
        val memoryRepository = EphemeralMemoryRecordRepository()
        var streamingExecuteCalled = false

        // Create a mock executor that emits multiple streaming frames
        val mockExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): List<Message.Response> {
                return listOf(Message.Assistant("Non-streaming response", ResponseMetaInfo.Empty))
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                streamingExecuteCalled = true
                emit(StreamFrame.Append("This should NOT be stored"))
                emit(StreamFrame.End("stop"))
            }

            override suspend fun moderate(prompt: Prompt, model: LLModel) =
                throw UnsupportedOperationException("Not needed for this test")

            override fun close() {}
        }

        // Create a simple strategy that calls the LLM with streaming
        val strategy =
            strategy<String, String>("test-streaming-no-persist", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
                val llmNode by nodeLLMRequestStreaming(name = "llm-node")
                edge(nodeStart forwardTo llmNode)
                edge(llmNode forwardTo nodeFinish transformed { flow ->
                    flow.toList().filterIsInstance<StreamFrame.Append>().joinToString("") { it.text }
                })
            }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("You are a helpful assistant")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(Memory2.Feature) {
                memoryRecordRepository = memoryRepository
                persistAssistantResponses = false // Explicitly disabled
            }
        }

        agent.run("Hello")

        // Verify streaming was used
        assertTrue(streamingExecuteCalled, "The executeStreaming method should have been called")

        // Verify that no records were stored
        assertEquals(
            0,
            memoryRepository.size(),
            "No records should be stored when persistAssistantResponses is false during streaming"
        )
    }
}
