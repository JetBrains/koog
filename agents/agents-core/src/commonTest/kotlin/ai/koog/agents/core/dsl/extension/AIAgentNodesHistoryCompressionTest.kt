package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIAgentNodesHistoryCompressionTest {

    /**
     * Helper function to create a prompt with the specified number of message pairs
     */
    private fun createPromptWithMessages(count: Int) = prompt("test", clock = TestLLMExecutor.testClock) {
        system("Test system message")

        // Add the specified number of user/assistant message pairs
        for (i in 1..count) {
            user("Test user message $i")
            assistant("Test assistant response $i")
        }
    }

    @Test
    fun testNodeLLMCompressHistoryWithWholeHistory() = runTest {
        // Create a test LLM executor to track TLDR messages
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.WholeHistory
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<Any?>()

        // Create a prompt with 15 message pairs
        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(15),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentCompleted { eventContext -> results += eventContext.result }
            }
        }.use { agent ->
            agent.run("", null)
        }

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Verify that only one TLDR message was created
        assertEquals(1, testExecutor.tldrCount, "WholeHistory strategy should create exactly one TLDR message")

        // Verify that the final messages include the TLDR
        val tldrMessages = testExecutor.messages.last().parts.filter {
            it is MessagePart.Text && it.text.startsWith("TLDR")
        }
        assertEquals(1, tldrMessages.size, "There should be exactly one TLDR message in the final history")
    }

    @Test
    fun testNodeLLMCompressHistoryWithFromLastNMessages() = runTest {
        // Create a test LLM executor to track TLDR messages
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.FromLastNMessages(4)
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<Any?>()

        // Create a prompt with 15 message pairs
        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(15),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentCompleted { eventContext -> results += eventContext.result }
            }
        }.use { agent ->
            agent.run("", null)
        }

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Verify that only one TLDR message was created
        assertEquals(1, testExecutor.tldrCount, "FromLastNMessages strategy should create exactly one TLDR message")

        // Verify that the final messages include the TLDR
        val tldrMessages = testExecutor.messages.last().parts.filter {
            it is MessagePart.Text && it.text.startsWith("TLDR")
        }
        assertEquals(1, tldrMessages.size, "There should be exactly one TLDR message in the final history")
    }

    @Test
    fun testNodeLLMCompressHistoryWithChunked() = runTest {
        // Create a test LLM executor to track TLDR messages
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        // Use a chunk size of 4 (each chunk will have 4 messages)
        val chunkSize = 4
        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.Chunked(chunkSize)
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<Any?>()

        // Create a prompt with 15 message pairs (30 messages total)
        val messageCount = 15
        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(messageCount),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            handleEvents {
                onAgentCompleted { eventContext -> results += eventContext.result }
            }
        }.use { agent ->
            agent.run("", null)
        }

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Print the actual TLDR count for debugging
        println("[DEBUG_LOG] Actual TLDR count: ${testExecutor.tldrCount}")

        // In the Chunked strategy, we expect multiple TLDR messages
        // The exact number depends on how the implementation chunks the messages
        // For now, we'll just verify that we have more than one TLDR message
        assertTrue(
            testExecutor.tldrCount > 1,
            "Chunked strategy should create multiple TLDR messages"
        )

        // Verify that the final messages include the TLDRs
        val tldrMessages = testExecutor.messages.flatMap { it.parts }.filter { part ->
            part is MessagePart.Text && part.text.startsWith("TLDR")
        }

        assertEquals(8, testExecutor.tldrCount)
        assertEquals(
            testExecutor.tldrCount,
            tldrMessages.size,
            "The number of TLDR messages in the final history should match the TLDR count"
        )
    }

    @Test
    fun testNodeLLMCompressHistoryWithFactRetrieval() = runTest {
        // Create a test LLM executor to track fact-extraction responses
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val concepts = listOf(
            Concept(
                keyword = "user-preferences",
                description = "User stated preferences and behaviour",
                factType = FactType.MULTIPLE
            ),
            Concept(
                keyword = "current-task",
                description = "The most important task currently being worked on",
                factType = FactType.SINGLE
            ),
        )

        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.FactRetrieval(concepts)
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<Any?>()

        // Create a prompt with 15 message pairs
        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(15),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentCompleted { eventContext -> results += eventContext.result }
            }
        }.use { agent ->
            agent.run("", null)
        }

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // FactRetrieval issues one extraction call per concept
        assertEquals(
            concepts.size,
            testExecutor.factCount,
            "FactRetrieval strategy should issue one extraction request per configured concept"
        )

        // No TLDR was needed because facts were extracted successfully
        assertEquals(
            0,
            testExecutor.tldrCount,
            "FactRetrieval strategy should not fall back to TLDR when facts are extracted successfully"
        )

        // The fact-extraction system prompts should reference all configured concept keywords.
        val systemMessages = testExecutor.messages.filterIsInstance<Message.System>()
        assertTrue(
            concepts.all { concept ->
                systemMessages.any { message ->
                    message.parts.any { it.text.contains(concept.keyword) }
                }
            },
            "Each configured concept keyword must appear in the fact-extraction system prompts"
        )
        // Each extraction request must wrap the conversation history in the dedicated XML tag.
        val userMessages = testExecutor.messages.filterIsInstance<Message.User>()
        assertEquals(
            concepts.size,
            userMessages.count { message ->
                message.parts.filterIsInstance<MessagePart.Text>().any { it.text.contains("<conversation_to_extract_facts>") }
            },
            "Each concept must be extracted from a conversation wrapped in <conversation_to_extract_facts>"
        )
    }
}
