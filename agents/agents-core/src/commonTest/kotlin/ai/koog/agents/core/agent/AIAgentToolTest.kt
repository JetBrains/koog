package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
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
        private val expectedResponse: String
    ) : AIAgent<String, String> {
        override val id: String = "mock_agent_id"
        override suspend fun run(agentInput: String): String {
            return expectedResponse
        }

        override val agentConfig: AIAgentConfigBase = AIAgentConfig(
            prompt = prompt("test-prompt-id") {
                system("You are a helpful assistant.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 5
        )

        override suspend fun close() {
        }
    }

    companion object {
        const val RESPONSE = "This is the agent's response"
        private fun createMockAgent(): AIAgent<String, String> {
            return MockAgent(RESPONSE)
        }

        private val agent = createMockAgent()

        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        val argsJson = buildJsonObject {
            put("value", "Test input")
        }
    }

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
        assertEquals("value", tool.descriptor.requiredParameters[0].name)
        assertEquals("Test request description", tool.descriptor.requiredParameters[0].description)
        assertEquals(ToolParameterType.String, tool.descriptor.requiredParameters[0].type)
    }

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
        val result = tool.execute(args, Enabler)

        assertTrue(result.successful)
        assertEquals(RESPONSE, result.result?.jsonPrimitive?.content)
        assertNotNull(result.result)
        assertEquals(null, result.errorMessage)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolErrorHandling() = runTest {
        val testError = IllegalStateException("Test error")
        val agent = object : AIAgent<String, String> {

            override val id: String = "mock_agent_id"

            override val agentConfig = AIAgentConfig(
                prompt = prompt("test-prompt-id") {
                    system("You are a helpful assistant.")
                },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 5
            )

            override suspend fun run(agentInput: String): String {
                throw testError
            }

            override suspend fun close() {
            }
        }

        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
            inputDescription = "Test request description"
        )

        val args = tool.decodeArgs(argsJson)
        val result = tool.execute(args, Enabler)

        assertEquals(false, result.successful)
        assertEquals(null, result.result)

        val expectedErrorMessage =
            "Error happened: ${testError::class.simpleName}(${testError.message})\n${testError.stackTraceToString().take(100)}"

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
        val result = tool.execute(args, Enabler)

        assertEquals(
            AIAgentTool.AgentToolResult(
                successful = true,
                result = JsonPrimitive("This is the agent's response")
            ),
            result
        )
    }
}
