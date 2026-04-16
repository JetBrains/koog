package ai.koog.agents.core.agent

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AIAgentRunWithResultTest {
    private val serializer = KotlinxSerializer()

    private fun createGraphAgent(expectedResponse: String = "Hello!"): AIAgent<String, String> {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer(expectedResponse).asDefaultResponse
        }

        return AIAgent(
            promptExecutor = mockExecutor,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            id = "test-agent",
        )
    }

    @Test
    fun testRunWithResultReturnsCorrectOutput() = runTest {
        val agent = createGraphAgent("Task solved!")
        val result = agent.runWithResult("Solve task", null)
        assertEquals("Task solved!", result.output)
    }

    @Test
    fun testRunWithResultReturnsContextualResult() = runTest {
        val agent = createGraphAgent("Hello!")
        val result = agent.runWithResult("Hello", null)
        assertIs<AIAgentContextualResult<*, *>>(result)
        assertNotNull((result as AIAgentContextualResult<*, *>).context)
    }

    @Test
    fun testRunReturnsOutputDirectly() = runTest {
        val agent = createGraphAgent("Direct output")
        val output: String = agent.run("Test", null)
        assertEquals("Direct output", output)
    }

    @Test
    fun testRunAndRunWithResultProduceSameOutput() = runTest {
        val expectedResponse = "Consistent output"

        val agent1 = createGraphAgent(expectedResponse)
        val agent2 = createGraphAgent(expectedResponse)

        val runOutput: String = agent1.run("Input", null)
        val runWithResultOutput = agent2.runWithResult("Input", null).output

        assertEquals(runOutput, runWithResultOutput)
    }

    @Test
    fun testRunWithResultWithFunctionalStrategy() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Functional result").asDefaultResponse
        }

        val agent = AIAgent<String, String>(
            systemPrompt = "You are helpful",
            promptExecutor = mockExecutor,
            strategy = functionalStrategy { inputParam ->
                val response = requestLLM(inputParam)
                response.asAssistantMessage().content
            },
            llmModel = OllamaModels.Meta.LLAMA_3_2,
        )

        val result = agent.runWithResult("Test input", null)
        assertEquals("Functional result", result.output)
        assertIs<AIAgentContextualResult<*, *>>(result)
    }

    @Test
    fun testSessionRunWithResultReturnsContextualResult() = runTest {
        val agent = createGraphAgent("Session result")
        val session = agent.createSession(null)

        val result = session.runWithResult("Test input")
        assertEquals("Session result", result.output)
        assertNotNull(result.context)
    }

    @Test
    fun testSessionRunReturnsOutput() = runTest {
        val agent = createGraphAgent("Session output")
        val session = agent.createSession(null)

        val output: String = session.run("Test input")
        assertEquals("Session output", output)
    }
}
