package ai.koog.protocol

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.flow.KoogFlow
import ai.koog.protocol.mock.TestMcpServer
import ai.koog.protocol.parser.FlowJsonConfigParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FlowExecutionTest : FlowTestBase() {

    companion object {

        private val logger = KotlinLogging.logger { }

        /**
         * A mock tool that matches the MCP greeting tool's signature.
         * This is used to tell the mock LLM to call the greeting tool with specific arguments.
         * The actual tool execution will be done by the real MCP tool from the registry.
         */
        private val greetingToolMock = object : Tool<JsonObject, String>(
            argsSerializer = JsonObject.serializer(),
            resultSerializer = String.serializer(),
            descriptor = ToolDescriptor(
                name = "greeting",
                description = "A simple greeting tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "name",
                        type = ToolParameterType.String,
                        description = "A name to greet"
                    )
                )
            )
        ) {
            override suspend fun execute(args: JsonObject): String {
                throw UnsupportedOperationException("Mock tool should not be executed directly")
            }
        }
    }

    @Test
    fun testFlowRun_randomNumbersFlowJson() = runTest {
        val jsonContent = readFlow("random_koog_agent_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val generateNumbersAgentTask = "Generate two random numbers between 1 and 100. Output them with a space between them."
        val calculatorAgentTask = "Your task is to sum all individual numbers in the input string. Numbers are separated by spaces."

        // Finalized tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        // Mock executor: the first agent returns "42 58", the second returns "100"
        val testExecutor = getMockExecutor {
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("42 58")) onCondition { request ->
                request.contains(generateNumbersAgentTask)
            }
            mockLLMToolCall(finalizeTool, FlowAgentInput.InputString("100")) onCondition { request ->
                request.contains(calculatorAgentTask)
            }
        }

        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = emptyList(),
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = testExecutor
        )

        // Create initial input based on the first agent's task
        val initialInput = FlowAgentInput.InputString(generateNumbersAgentTask)
        val result = flow.run(initialInput)

        // Verify the result is the sum from the calculator agent
        assertIs<FlowAgentInput.InputString>(result)
        assertEquals("100", result.data)
    }

    @Test
    fun testFlowRun_withMcpToolExecution() = runTest(timeout = 30.seconds) {
        val jsonContent = readFlow("greeting_flow_with_mcp_tool.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val taskInput = "Use the greeting tool to greet the user named 'TestUser'"

        assertEquals(1, flowConfig.tools.size, "Check tools were parsed from JSON")

        // Finalized tool for mocking with FlowAgentInput type
        val finalizeTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        val testExecutor = getMockExecutor {
            // When asked to greet, call the greeting tool
            mockLLMToolCall(
                greetingToolMock,
                buildJsonObject { put("name", "TestUser") }
            ) onCondition { request ->
                request.contains(taskInput)
            }

            // After getting a tool result, finalize with the greeting
            mockLLMToolCall(
                finalizeTool,
                FlowAgentInput.InputString("Hello, TestUser!")
            ) onCondition { request ->
                request.contains("Hello, TestUser!")
            }
        }

        val flow = KoogFlow(
            id = flowConfig.id ?: "test-flow",
            agents = flowConfig.agents,
            tools = flowConfig.tools,
            transitions = flowConfig.transitions,
            defaultModel = flowConfig.defaultModel,
            promptExecutor = testExecutor
        )

        // Create initial input based on the task
        val initialInput = FlowAgentInput.InputString(taskInput)
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withMcpServer(port = 3002) { _ ->
                flow.run(initialInput)
            }
        }

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("Hello, TestUser!"), "Result should contain greeting: ${result.data}")
    }

    //region Verify and Transform Tests

    /**
     * Test that InputCritiqueResult can be serialized and deserialized correctly.
     * This validates the custom serializer handles the InputCritiqueResult type properly.
     */
    @Test
    fun testInputCritiqueResult_serializationRoundTrip() {
        val original = FlowAgentInput.InputCritiqueResult(
            success = false,
            feedback = "Missing greeting word. Please add 'Hello' or 'Hi'.",
            input = FlowAgentInput.InputString("World")
        )

        val json = kotlinx.serialization.json.Json.encodeToString(
            FlowAgentInput.serializer(),
            original
        )

        val deserialized = kotlinx.serialization.json.Json.decodeFromString(
            FlowAgentInput.serializer(),
            json
        )

        assertIs<FlowAgentInput.InputCritiqueResult>(deserialized)
        assertEquals(original.success, deserialized.success)
        assertEquals(original.feedback, deserialized.feedback)
        assertIs<FlowAgentInput.InputString>(deserialized.input)
        assertEquals("World", (deserialized.input as FlowAgentInput.InputString).data)
    }

    /**
     * Test that InputCritiqueResult with nested InputCritiqueResult can be serialized.
     */
    @Test
    fun testInputCritiqueResult_nestedSerialization() {
        val nested = FlowAgentInput.InputCritiqueResult(
            success = true,
            feedback = "Nested result",
            input = FlowAgentInput.InputInt(42)
        )

        val original = FlowAgentInput.InputCritiqueResult(
            success = false,
            feedback = "Outer feedback",
            input = nested
        )

        val json = kotlinx.serialization.json.Json.encodeToString(
            FlowAgentInput.serializer(),
            original
        )

        val deserialized = kotlinx.serialization.json.Json.decodeFromString(
            FlowAgentInput.serializer(),
            json
        )

        assertIs<FlowAgentInput.InputCritiqueResult>(deserialized)
        assertEquals(false, deserialized.success)
        assertEquals("Outer feedback", deserialized.feedback)

        val nestedResult = deserialized.input
        assertIs<FlowAgentInput.InputCritiqueResult>(nestedResult)
        assertEquals(true, nestedResult.success)
        assertEquals("Nested result", nestedResult.feedback)
        assertIs<FlowAgentInput.InputInt>(nestedResult.input)
        assertEquals(42, (nestedResult.input as FlowAgentInput.InputInt).data)
    }

    /**
     * Test that the flow configuration correctly parses verify->transform transitions.
     * This validates that conditional transitions based on InputCritiqueResult.success
     * are properly configured.
     */
    @Test
    fun testFlowConfig_verifyTransformTransitions() {
        val jsonContent = readFlow("verify_transform_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        // Verify we have the correct agent types
        assertEquals(4, flowConfig.agents.size)

        val taskAgent = flowConfig.agents.find { it.name == "task_agent" }
        val verifyAgent = flowConfig.agents.find { it.name == "verify_agent" }
        val transformAgent = flowConfig.agents.find { it.name == "transform_feedback" }
        val fixAgent = flowConfig.agents.find { it.name == "fix_agent" }

        assertNotNull(taskAgent, "task_agent should exist")
        assertNotNull(verifyAgent, "verify_agent should exist")
        assertNotNull(transformAgent, "transform_feedback should exist")
        assertNotNull(fixAgent, "fix_agent should exist")

        // Verify transition structure
        val verifyToFinish = flowConfig.transitions.find {
            it.from == "verify_agent" && it.to == "__finish__"
        }
        val verifyToTransform = flowConfig.transitions.find {
            it.from == "verify_agent" && it.to == "transform_feedback"
        }
        val transformToFix = flowConfig.transitions.find {
            it.from == "transform_feedback" && it.to == "fix_agent"
        }

        assertNotNull(verifyToFinish, "verify_agent -> __finish__ transition should exist")
        assertNotNull(verifyToTransform, "verify_agent -> transform_feedback transition should exist")
        assertNotNull(transformToFix, "transform_feedback -> fix_agent transition should exist")

        // Verify conditions on transitions from verify_agent
        assertNotNull(verifyToFinish.condition, "verify_agent -> __finish__ should have a condition")
        assertEquals("input.success", verifyToFinish.condition!!.variable)

        assertNotNull(verifyToTransform.condition, "verify_agent -> transform_feedback should have a condition")
        assertEquals("input.success", verifyToTransform.condition!!.variable)
    }

    /**
     * Test that different FlowAgentInput types serialize/deserialize correctly.
     */
    @Test
    fun testFlowAgentInput_allTypesSerialization() {
        val testCases = listOf<FlowAgentInput>(
            FlowAgentInput.InputString("test string"),
            FlowAgentInput.InputInt(42),
            FlowAgentInput.InputDouble(3.14),
            FlowAgentInput.InputBoolean(true),
            FlowAgentInput.InputArrayStrings(arrayOf("a", "b", "c")),
            FlowAgentInput.InputArrayInt(arrayOf(1, 2, 3)),
            FlowAgentInput.InputArrayDouble(arrayOf(1.1, 2.2, 3.3)),
            FlowAgentInput.InputArrayBooleans(arrayOf(true, false, true)),
            FlowAgentInput.InputCritiqueResult(
                success = true,
                feedback = "All good",
                input = FlowAgentInput.InputString("original")
            )
        )

        for (original in testCases) {
            val json = kotlinx.serialization.json.Json.encodeToString(
                FlowAgentInput.serializer(),
                original
            )

            val deserialized = kotlinx.serialization.json.Json.decodeFromString(
                FlowAgentInput.serializer(),
                json
            )

            assertEquals(
                original::class,
                deserialized::class,
                "Type should be preserved for ${original::class.simpleName}"
            )
        }
    }

    //endregion Verify and Transform Tests

    //region Private Methods

    private suspend fun withMcpServer(port: Int, block: suspend (mcpServer: TestMcpServer) -> FlowAgentInput): FlowAgentInput {
        val mcpServer = TestMcpServer(port)
        try {
            logger.info { "Starting MCP server on port $port" }
            mcpServer.start()
            delay(1.seconds)
            return block(mcpServer)
        } finally {
            logger.info { "Stopping MCP server" }
            mcpServer.stop()
        }
    }

    //endregion Private Methods
}
