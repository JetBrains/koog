package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
            mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
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
}