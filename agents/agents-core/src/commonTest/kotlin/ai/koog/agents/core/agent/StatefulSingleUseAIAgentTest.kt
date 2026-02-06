package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatefulSingleUseAIAgentTest {

    @Test
    fun test_StatefulSingleUseAIAgent_RunsTwiceWithoutException() = runTest {
        // Arrange
        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("First run result") onRequestContains "First run"
            mockLLMAnswer("Second run result") onRequestContains "Second run"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry
        )

        // Act - First run
        val firstResult = agent.run("First run", null)

        // Assert first run
        assertNotNull(firstResult)
        assertEquals("First run result", firstResult)

        // Act - Second run (should not throw exception)
        val secondResult = agent.run("Second run", null)

        // Assert second run
        assertNotNull(secondResult)
        assertEquals("Second run result", secondResult)
    }

    @Test
    fun test_StatefulSingleUseAIAgent_RunsMultipleTimesSequentially() = runTest {
        // Arrange
        val testToolRegistry = ToolRegistry.EMPTY

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Result 1") onRequestEquals "Run 1"
            mockLLMAnswer("Result 2") onRequestEquals "Run 2"
            mockLLMAnswer("Result 3") onRequestEquals "Run 3"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry
        )

        // Act - Run multiple times
        val result1 = agent.run("Run 1", null)
        val result2 = agent.run("Run 2", null)
        val result3 = agent.run("Run 3", null)

        // Assert all runs completed successfully
        assertEquals("Result 1", result1)
        assertEquals("Result 2", result2)
        assertEquals("Result 3", result3)
    }

    @Test
    fun test_StatefulSingleUseAIAgent_RunsInParallel() = runTest {
        // Arrange
        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Parallel run 1 result") onRequestContains "Parallel run 1"
            mockLLMAnswer("Parallel run 2 result") onRequestContains "Parallel run 2"
            mockLLMAnswer("Parallel run 3 result") onRequestContains "Parallel run 3"
            mockLLMAnswer("Parallel run 4 result") onRequestContains "Parallel run 4"
            mockLLMAnswer("Parallel run 5 result") onRequestContains "Parallel run 5"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry
        )

        // Act - Run multiple times in parallel using async
        val results = listOf(
            async { agent.run("Parallel run 1", null) },
            async { agent.run("Parallel run 2", null) },
            async { agent.run("Parallel run 3", null) },
            async { agent.run("Parallel run 4", null) },
            async { agent.run("Parallel run 5", null) }
        ).awaitAll()

        // Assert all parallel runs completed successfully
        assertEquals(5, results.size)
        assertTrue(results.contains("Parallel run 1 result"))
        assertTrue(results.contains("Parallel run 2 result"))
        assertTrue(results.contains("Parallel run 3 result"))
        assertTrue(results.contains("Parallel run 4 result"))
        assertTrue(results.contains("Parallel run 5 result"))
    }
}
