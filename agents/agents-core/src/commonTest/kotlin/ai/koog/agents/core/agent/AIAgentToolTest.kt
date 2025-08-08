package ai.koog.agents.core.agent

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
object Enabler : DirectToolCallsEnabler

class AIAgentToolTest {

    private class MockAgent(
        private val executor: PromptExecutor,
        private val expectedResponse: String
    ) : AIAgent<String, String> {
        override val id: String = "mock_agent_id"
        override suspend fun run(agentInput: String): String {
            return expectedResponse
        }

        override suspend fun close() {
        }
    }

    companion object {
        const val RESPONSE = "This is the agent's response"
        private fun createMockAgent(): AIAgent<String, String> {
            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer(RESPONSE).asDefaultResponse
            }
            return MockAgent(mockExecutor, RESPONSE)
        }

        val agent = createMockAgent()
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescriptor = ToolParameterDescriptor(
                name = "request",
                description = "Test request description",
                type = ToolParameterType.String
            )
        )

        val argsJson = buildJsonObject {
            put("request", "Test input")
        }
    }

    @Test
    fun testAsToolCreation() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescriptor = ToolParameterDescriptor(
                name = "request",
                description = "Test request description",
                type = ToolParameterType.String
            )
        )

        assertEquals("testAgent", tool.descriptor.name)
        assertEquals("Test agent description", tool.descriptor.description)
        assertEquals(1, tool.descriptor.requiredParameters.size)
        assertEquals("request", tool.descriptor.requiredParameters[0].name)
        assertEquals("Test request description", tool.descriptor.requiredParameters[0].description)
        assertEquals(ToolParameterType.String, tool.descriptor.requiredParameters[0].type)
    }

    @Test
    fun testAsToolWithDefaultName() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescriptor = ToolParameterDescriptor(
                name = "request",
                description = "Test request description",
                type = ToolParameterType.String
            )
        )
        assertEquals("testAgent", tool.descriptor.name)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolExecution() = runTest {
        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args, Enabler)

        assertTrue(result.successful)
        assertEquals(RESPONSE, result.result?.jsonPrimitive?.content)
        assertNotNull(result.result)
        assertEquals(null, result.errorMessage)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolErrorHandling() = runTest {
        val agent = object : AIAgent<String, String> {

            override val id: String = "mock_agent_id"

            override suspend fun run(agentInput: String): String {
                throw IllegalStateException("Test error")
            }

            override suspend fun close() {
            }
        }

        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescriptor = ToolParameterDescriptor(
                name = "request",
                description = "Test request description",
                type = ToolParameterType.String
            )
        )

        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args, Enabler)

        assertEquals(false, result.successful)
        assertEquals(null, result.result)
        assertTrue(result.errorMessage?.contains("Test error") == true)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolResultSerialization() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescriptor = ToolParameterDescriptor(
                name = "request",
                description = "Test request description",
                type = ToolParameterType.String
            )
        )

        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args, Enabler)

        val serialized = result.toStringDefault()
        assertTrue(serialized.contains("\"successful\": true"))
        assertTrue(serialized.contains("\"result\": \"This is the agent's response\""))
    }
}
