package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.Agent
import ai.grazie.code.agents.core.agent.config.AgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.testing.tools.DummyTool
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentNodesTest {
    @Test
    fun testNodeLLMCompressHistory() = runTest {
        val agentStrategy = simpleStrategy("test") {
            val compress by nodeLLMCompressHistory<Unit>()

            edge(nodeStart forwardTo compress transformed { })
            edge(compress forwardTo nodeFinish transformed { "Done" })

        }

        val results = mutableListOf<String?>()
        val eventHandler = EventHandler {
            handleResult { results += it }
        }

        val agentConfig = AgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )


        val testExecutor = getMockExecutor {
            mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val runner = Agent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            cs = this,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            eventHandler = eventHandler
        )

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())
    }
}
