package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
            agent.run("")
        }

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

        AIAgent(
            promptExecutor = modelCapturingExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(EventHandler) {
                onAgentCompleted { eventContext ->
                    executionEvents += "Agent finished"
                    results += eventContext.result
                }
            }
        }.use { agent ->

            val executionResult = agent.run("Heeeey")

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

    @Test
    fun testNodeSetStructuredOutput() = runTest {
        @Serializable
        data class TestOutput(
            val message: String,
            val code: Int
        )

        // Test Manual mode
        val manualStructure = JsonStructure.create<TestOutput>()
        val manualConfig = StructuredRequestConfig(
            default = StructuredRequest.Manual(manualStructure)
        )

        var capturedPrompt: Prompt? = null

        val manualStrategy = strategy<String, String>("test-manual") {
            val setStructuredOutput by nodeSetStructuredOutput<String, TestOutput>(config = manualConfig)
            val checkPrompt by node<String, String> { input ->
                capturedPrompt = llm.prompt
                input
            }

            edge(nodeStart forwardTo setStructuredOutput)
            edge(setStructuredOutput forwardTo checkPrompt)
            edge(checkPrompt forwardTo nodeFinish)
        }

        val testExecutor = getMockExecutor {
            mockLLMAnswer("Test").asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {},
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 5
        )

        val manualAgent = AIAgent(
            promptExecutor = testExecutor,
            strategy = manualStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { }
        )

        manualAgent.run("Test input")

        // Manual mode: schema should not be set, user message should be added
        assertNotNull(capturedPrompt, "Prompt should be captured")
        assertEquals(null, capturedPrompt!!.params.schema, "Schema should not be set for Manual config")
        assertTrue(
            capturedPrompt!!.messages.any { it is ai.koog.prompt.message.Message.User },
            "Should have user message with instructions for Manual config"
        )

        // Test Native mode
        val nativeStructure = JsonStructure.create<TestOutput>()
        val nativeConfig = StructuredRequestConfig(
            default = StructuredRequest.Native(nativeStructure)
        )

        val nativeStrategy = strategy<String, String>("test-native") {
            val setStructuredOutput by nodeSetStructuredOutput<String, TestOutput>(config = nativeConfig)
            val checkPrompt by node<String, String> { input ->
                capturedPrompt = llm.prompt
                input
            }

            edge(nodeStart forwardTo setStructuredOutput)
            edge(setStructuredOutput forwardTo checkPrompt)
            edge(checkPrompt forwardTo nodeFinish)
        }

        val nativeAgent = AIAgent(
            promptExecutor = testExecutor,
            strategy = nativeStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test") {},
                model = OpenAIModels.CostOptimized.GPT4oMini,
                maxAgentIterations = 5
            ),
            toolRegistry = ToolRegistry { }
        )

        nativeAgent.run("Test input")

        // Native mode: schema should be set
        assertNotNull(capturedPrompt, "Prompt should be captured")
        assertNotNull(capturedPrompt!!.params.schema, "Schema should be set for Native config")
        assertEquals(nativeStructure.schema, capturedPrompt!!.params.schema, "Schema should match structure's schema")
    }
}
