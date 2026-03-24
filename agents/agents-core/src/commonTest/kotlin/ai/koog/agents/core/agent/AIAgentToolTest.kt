package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolInput
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.serialization.typeToken
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentToolTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    data class SimpleData(
        @property:LLMDescription("Test request description")
        val value: String
    )

    private class MockAgent(
        private val run: () -> String
    ) : GraphAIAgent<SimpleData, SimpleData>(
        id = "mock_agent_id",
        strategy = strategy("mock") {
            edge(nodeStart forwardTo nodeFinish transformed { SimpleData(run()) })
        },
        promptExecutor = getMockExecutor(serializer) { },
        agentConfig = AIAgentConfig(
            prompt = prompt("test-prompt-id") {
                system("You are a helpful assistant.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 5
        ),
        inputType = typeToken<String>(),
        outputType = typeToken<String>()
    ) {
        constructor(result: String) : this({ result })
    }

    companion object {
        val serializer = KotlinxSerializer()

        const val RESPONSE = "This is the agent's response"
        private fun createMockAgent(): MockAgent {
            return MockAgent(RESPONSE)
        }

        private val agent = createMockAgent()

        @OptIn(InternalAgentToolsApi::class)
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
        )

        val argsJson = tool.encodeArgs(AgentToolInput(SimpleData("Test input")), serializer)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolCreation() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
        )

        tool.descriptor should {
            it.name shouldBe "testAgent"
            it.description shouldBe "Test agent description"
            it.requiredParameters.size shouldBe 1

            it.requiredParameters[0].type.shouldBeTypeOf<ToolParameterType.Object> {
                it.properties.size shouldBe 1
                it.properties[0] should {
                    it.description shouldBe "Test request description"
                    it.type shouldBe ToolParameterType.String
                }
            }
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolWithDefaultName() = runTest {
        val tool = agent.asTool(
            agentName = "testAgent",
            agentDescription = "Test agent description",
        )
        assertEquals("testAgent", tool.descriptor.name)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testAsToolExecution() = runTest {
        val args = tool.decodeArgs(argsJson, serializer)
        val result = tool.execute(args)

        assertTrue(result.successful)
        assertEquals(SimpleData(RESPONSE), result.result)
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
        )

        val args = tool.decodeArgs(argsJson, serializer)
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
        val result = AIAgentTool.AgentToolResult(
            successful = true,
            result = SimpleData("This is the agent's response"),
        )

        val resultSerialized = buildJsonObject {
            put("successful", true)
            putJsonObject("result") {
                put("value", "This is the agent's response")
            }
        }.toKoogJSONObject()

        tool.encodeResult(result, serializer) shouldBe resultSerialized
        tool.decodeResult(resultSerialized, serializer) shouldBe result
    }
}
