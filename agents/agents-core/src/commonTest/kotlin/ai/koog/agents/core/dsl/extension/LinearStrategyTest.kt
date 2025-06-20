package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearStrategyTest {

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
    fun testApplyLinearStrategy() = runTest {
        // Create a test LLM executor
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val agentStrategy = strategy("test") {
            // Define three nodes for our linear strategy
            val node1 by node<String, String>("node1") { input ->
                "Node 1 processed: $input"
            }

            val node2 by node<String, String>("node2") { input ->
                "Node 2 processed: $input"
            }

            val node3 by node<String, String>("node3") { input ->
                "Node 3 processed: $input"
            }

            // Apply the linear strategy
            linearSteps(node1 then node2 then node3)
        }

        val results = mutableListOf<String?>()

        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(5),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            handleEvents {
                onAgentFinished = { _, result -> results += result }
            }
        }

        agent.run("Initial input")

        // Verify we have one result
        assertEquals(1, results.size)

        // The result should be the output of the last node
        assertEquals("Node 3 processed: Node 2 processed: Node 1 processed: Initial input", results.first())

        // Verify that no TLDR messages were created (since we didn't use history compression)
        assertEquals(0, testExecutor.tldrCount, "No compression strategy should not create any TLDR messages")
    }

    @Test
    fun testApplyLinearStrategyWithHistoryCompression() = runTest {
        // Create a test LLM executor
        val testExecutor = TestLLMExecutor()
        testExecutor.reset()

        val agentStrategy = strategy("test") {
            // Define three nodes for our linear strategy
            val node1 by node<String, String>("node1") { input ->
                "Node 1 processed: $input"
            }

            val node2 by node<String, String>("node2") { input ->
                "Node 2 processed: $input"
            }

            val node3 by node<String, String>("node3") { input ->
                "Node 3 processed: $input"
            }

            // Apply the linear strategy with history compression
            linearSteps(
                steps = node1 then node2 then node3,
                historyCompressionBetweenSteps = HistoryCompressionStrategy.CompressWholeHistory
            )
        }

        val results = mutableListOf<String?>()

        val agentConfig = AIAgentConfig(
            prompt = createPromptWithMessages(15),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            handleEvents {
                onAgentFinished = { _, result -> results += result }
            }
        }

        agent.run("Initial input")

        // Verify we have one result
        assertEquals(1, results.size)

        // The result should be the output of the last node
        assertEquals("Node 3 processed: Node 2 processed: Node 1 processed: Initial input", results.first())

        // We expect TLDR messages (one before node3, as per the implementation in AIAgentSubgraphBuilder.kt)
        // The compression is only applied if index > 0 and the strategy is not NoCompression
        assertEquals(1, testExecutor.tldrCount, "CompressWholeHistory strategy should create TLDR messages")

        // Verify that the final messages include the TLDRs
        val tldrMessages = testExecutor.messages.filterIsInstance<ai.koog.prompt.message.Message.Assistant>()
            .filter { it.content.startsWith("TLDR") }
        assertEquals(1, tldrMessages.size, "There should be TLDR messages in the final history")
    }
}
