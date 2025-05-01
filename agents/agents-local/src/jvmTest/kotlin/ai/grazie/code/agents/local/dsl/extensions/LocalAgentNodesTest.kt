package ai.grazie.code.agents.local.dsl.extensions

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.simpleStrategy
import ai.grazie.code.agents.testing.tools.DummyTool
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalAgentNodesTest {
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

        val agentConfig = LocalAgentConfig(
            prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test-agent") {},
            maxAgentIterations = 10
        )


        val testExecutor = getMockExecutor {
            mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val runner = KotlinAIAgent(
            promptExecutor = testExecutor,
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = agentStrategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            cs = this
        )

        runner.run("")

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())
    }
}
