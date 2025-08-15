package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIAgentNodesTest {
    @Test
    fun testNodeLLMCompressHistory() = runTest {
        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>()

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })
        }

        val results = mutableListOf<Any?>()

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor {
            mockLLMAnswer(
                "Here's a summary of the conversation: Test user asked questions and received responses."
            ) onRequestContains
                "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val runner = AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())
    }

    @Test
    fun testNodeLLMCompressHistoryWithCustomModel() = runTest {
        val customModel = OpenAIModels.CostOptimized.O3Mini
        val originalModel = OllamaModels.Meta.LLAMA_3_2

        val results = mutableListOf<Any?>()
        val executionEvents = mutableListOf<String>()

        val modelCapturingExecutor = getMockExecutor {
            mockLLMAnswer("Custom model compression summary") onRequestContains "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val agentStrategy = strategy<String, String>("test") {
            val compress by nodeLLMCompressHistory<Unit>(retrievalModel = customModel)

            edge(
                nodeStart forwardTo compress transformed {
                    executionEvents += "nodeStart -> compress"
                }
            )
            edge(
                compress forwardTo nodeFinish transformed {
                    executionEvents += "compress -> nodeFinish"
                    "Done"
                }
            )
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {
                system("Test system message")
                user("User message for testing history compression")
                assistant("Assistant response for testing history compression")
                user("Another user message for more context")
                assistant("Another assistant response providing more context")
            },
            model = originalModel,
            maxAgentIterations = 10
        )

        val runner = AIAgent(
            promptExecutor = modelCapturingExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext ->
                    executionEvents += "Agent finished"
                    results += eventContext.result
                }
            }
        }

        val executionResult = runner.run("Heeeey")

        assertEquals("Done", executionResult, "Agent execution should return 'Done'")
        assertEquals(1, results.size, "Should have exactly one result")

        assertTrue(executionEvents.contains("nodeStart -> compress"), "Should transition from start to compress")
        assertTrue(executionEvents.contains("compress -> nodeFinish"), "Should transition from compress to finish")

        assertTrue(
            agentConfig.prompt.messages.any { it.content.contains("testing history compression") },
            "Prompt should contain test content for compression"
        )
        assertTrue(
            executionEvents.size >= 3,
            "Should have at least 3 execution events (agent finished, node transitions)"
        )
    }
}
