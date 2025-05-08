package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.KotlinAIAgent
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.testing.tools.DummyTool
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAgentNodesHistoryCompressionTest {

    /**
     * Helper function to create a prompt with the specified number of message pairs
     */
    private fun createPromptWithMessages(count: Int) = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
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

        val agentStrategy = simpleStrategy("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.WholeHistory
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<String?>()
        val eventHandler = EventHandler {
            handleResult { results += it }
        }

        // Create a prompt with 15 message pairs
        val agentConfig = LocalAgentConfig(
            prompt = createPromptWithMessages(15),
            maxAgentIterations = 10
        )

        val runner = KotlinAIAgent(
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = agentStrategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            promptExecutor = testExecutor,
            cs = this
        )

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Verify that only one TLDR message was created
        assertEquals(1, testExecutor.tldrCount, "WholeHistory strategy should create exactly one TLDR message")

        // Verify that the final messages include the TLDR
        val tldrMessages = testExecutor.messages.filterIsInstance<Message.Assistant>()
            .filter { it.content.startsWith("TLDR") }
        assertEquals(1, tldrMessages.size, "There should be exactly one TLDR message in the final history")
    }

    @Test
    fun testNodeLLMCompressHistoryWithFromLastNMessages() = runTest {
        // Create a test LLM executor to track TLDR messages
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val agentStrategy = simpleStrategy("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.FromLastNMessages(4)
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<String?>()
        val eventHandler = EventHandler {
            handleResult { results += it }
        }

        // Create a prompt with 15 message pairs
        val agentConfig = LocalAgentConfig(
            prompt = createPromptWithMessages(15),
            maxAgentIterations = 10
        )

        val runner = KotlinAIAgent(
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = agentStrategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            promptExecutor = testExecutor,
            cs = this
        )

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Verify that only one TLDR message was created
        assertEquals(1, testExecutor.tldrCount, "FromLastNMessages strategy should create exactly one TLDR message")

        // Verify that the final messages include the TLDR
        val tldrMessages = testExecutor.messages.filterIsInstance<Message.Assistant>()
            .filter { it.content.startsWith("TLDR") }
        assertEquals(1, tldrMessages.size, "There should be exactly one TLDR message in the final history")
    }

    @Test
    fun testNodeLLMCompressHistoryWithChunked() = runTest {
        // Create a test LLM executor to track TLDR messages
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        // Use a chunk size of 4 (each chunk will have 4 messages)
        val chunkSize = 4
        val agentStrategy = simpleStrategy("test") {
            val compress by nodeLLMCompressHistory<Unit>(
                strategy = HistoryCompressionStrategy.Chunked(chunkSize)
            )

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<String?>()
        val eventHandler = EventHandler {
            handleResult { results += it }
        }

        // Create a prompt with 15 message pairs (30 messages total)
        val messageCount = 15
        val agentConfig = LocalAgentConfig(
            prompt = createPromptWithMessages(messageCount),
            maxAgentIterations = 10
        )

        val runner = KotlinAIAgent(
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = agentStrategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            promptExecutor = testExecutor,
            cs = this
        )

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())

        // Print the actual TLDR count for debugging
        println("[DEBUG_LOG] Actual TLDR count: ${testExecutor.tldrCount}")

        // In the Chunked strategy, we expect multiple TLDR messages
        // The exact number depends on how the implementation chunks the messages
        // For now, we'll just verify that we have more than one TLDR message
        assertTrue(testExecutor.tldrCount > 1, 
            "Chunked strategy should create multiple TLDR messages")

        // Verify that the final messages include the TLDRs
        val tldrMessages = testExecutor.messages.filterIsInstance<Message.Assistant>()
            .filter { it.content.startsWith("TLDR") }

        assertEquals(8, testExecutor.tldrCount)
        assertEquals(
            testExecutor.tldrCount, tldrMessages.size,
            "The number of TLDR messages in the final history should match the TLDR count"
        )
    }
}
