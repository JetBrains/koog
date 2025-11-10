package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentToolTest {

    private class MockAgent(
        private val run: () -> String
    ) : GraphAIAgent<String, String>(
        id = "mock_agent_id",
        strategy = strategy("mock") { edge(nodeStart forwardTo nodeFinish transformed { run() }) },
        promptExecutor = getMockExecutor { },
        agentConfig = AIAgentConfig(
            prompt = prompt("test-prompt-id") {
                system("You are a helpful assistant.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 5
        ),
        inputType = typeOf<String>(),
        outputType = typeOf<String>()
    ) {
        constructor(result: String) : this({ result })
    }

    companion object {
        const val RESPONSE = "This is the agent's response"
        private fun createMockAgent(): MockAgent {
            return MockAgent(RESPONSE)
        }

        private val agent = createMockAgent()

        @OptIn(InternalAgentToolsApi::class)
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        val argsJson = tool.encodeArgs("Test input")
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolCreation() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        assertEquals("testAgent", tool.descriptor.name)
        assertEquals("Test agent description", tool.descriptor.description)
        assertEquals(1, tool.descriptor.requiredParameters.size)
        assertEquals("Test request description", tool.descriptor.requiredParameters[0].description)
        assertEquals(ToolParameterType.String, tool.descriptor.requiredParameters[0].type)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolWithDefaultName() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )
        assertEquals("testAgent", tool.descriptor.name)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolExecution() = runTest {
        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args)

        assertTrue(result.successful)
        assertEquals(RESPONSE, result.result)
        assertNotNull(result.result)
        assertEquals(null, result.errorMessage)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolErrorHandling() = runTest {
        val testError = IllegalStateException("Test error")
        val agent = MockAgent { throw testError }

        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args)

        assertEquals(false, result.successful)
        assertEquals(null, result.result)

        val expectedErrorMessage =
            "Error happened: ${testError::class.simpleName}(${testError.message})\n${
                testError.stackTraceToString().take(100)
            }"

        assertEquals(expectedErrorMessage, result.errorMessage)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolResultSerialization() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args)

        assertEquals(
            AIAgentTool.AgentToolResult(
                successful = true,
                result = "This is the agent's response",
            ),
            result
        )
    }
}
