package ai.koog.protocol

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.ext.agent.CriticResultFromLLM
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
import kotlinx.serialization.json.Json
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

        private val finalizeTaskTool = SubgraphWithTaskUtils.finishTool<FlowAgentInput>()

        @OptIn(InternalAgentsApi::class)
        private val finalizeVerifyTool = SubgraphWithTaskUtils.finishTool<CriticResultFromLLM>()

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

        // Mock executor: the first agent returns "42 58", the second returns "100"
        val testExecutor = getMockExecutor {
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("42 58")) onCondition { request ->
                request.contains(generateNumbersAgentTask)
            }
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("100")) onCondition { request ->
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
                finalizeTaskTool,
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
        assertEquals("World", deserialized.input.data)
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
        assertEquals(42, nestedResult.input.data)
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
        assertEquals("input.success", verifyToFinish.condition.variable)

        assertNotNull(verifyToTransform.condition, "verify_agent -> transform_feedback should have a condition")
        assertEquals("input.success", verifyToTransform.condition.variable)
    }

    /**
     * Test that different FlowAgentInput types serialize/deserialize correctly.
     */
    @Test
    fun testFlowAgentInput_allTypesSerialization() {
        val testCases = listOf(
            FlowAgentInput.InputString("test string"),
            FlowAgentInput.InputInt(42),
            FlowAgentInput.InputDouble(3.14),
            FlowAgentInput.InputBoolean(true),
            FlowAgentInput.InputArrayString(arrayOf("a", "b", "c")),
            FlowAgentInput.InputArrayInt(arrayOf(1, 2, 3)),
            FlowAgentInput.InputArrayDouble(arrayOf(1.1, 2.2, 3.3)),
            FlowAgentInput.InputArrayBoolean(arrayOf(true, false, true)),
            FlowAgentInput.InputCritiqueResult(
                success = true,
                feedback = "All good",
                input = FlowAgentInput.InputString("original")
            )
        )

        for (original in testCases) {
            val json = Json.encodeToString(
                FlowAgentInput.serializer(),
                original
            )

            val deserialized = Json.decodeFromString(
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

    //region Examples

    @Test
    fun testConditionalBranchingFlow_highScore() = runTest {
        val jsonContent = readFlow("conditional_branching_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Score analyzer returns a high score
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputInt(95)) onCondition { request ->
                request.contains("Extract the numeric score")
            }

            // High score feedback
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Excellent performance!")) onCondition { request ->
                request.contains("Congratulate the user")
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

        val result = flow.run(FlowAgentInput.InputString("Score: 95"))

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("Excellent") || result.data.contains("performance"))
    }

    @Test
    fun testConditionalBranchingFlow_lowScore() = runTest {
        val jsonContent = readFlow("conditional_branching_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Score analyzer returns a low score
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputInt(30)) onCondition { request ->
                request.contains("Extract the numeric score")
            }

            // Low score feedback
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Constructive feedback provided")) onCondition { request ->
                request.contains("constructive feedback")
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

        val result = flow.run(FlowAgentInput.InputString("Score: 30"))

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("feedback") || result.data.contains("Constructive"))
    }

    @Test
    fun testRetryLoopFlow_successOnFirstTry() = runTest {
        val jsonContent = readFlow("retry_loop_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Initial generator produces code
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("def hello(): pass")) onCondition { request ->
                request.contains("generate", ignoreCase = true)
            }

            // Verifier succeeds immediately
            @OptIn(InternalAgentsApi::class)
            mockLLMToolCall(
                finalizeVerifyTool,
                CriticResultFromLLM(
                    isCorrect = true,
                    feedback = "Code looks good!"
                )
            ) onCondition { request ->
                request.contains("verify", ignoreCase = true) || request.contains("check", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("Create a hello function"))

        assertIs<FlowAgentInput.InputCritiqueResult>(result)
        assertTrue(result.success)
    }

    @Test
    fun testSequentialPipelineFlow() = runTest {
        val jsonContent = readFlow("sequential_pipeline_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Data collector
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Structured: name=John, age=30")) onCondition { request ->
                request.contains("collect", ignoreCase = true) &&
                    request.contains("structure data", ignoreCase = true)
            }

            // Data enricher
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Enriched: name=John, age=30, location=USA")) onCondition { request ->
                request.contains("enrich data", ignoreCase = true) &&
                    request.contains("additional context", ignoreCase = true)
            }

            // Data formatter
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Formatted: John (30) - USA")) onCondition { request ->
                request.contains("format data", ignoreCase = true) &&
                    request.contains("final output", ignoreCase = true)
            }

            // Quality checker
            @OptIn(InternalAgentsApi::class)
            mockLLMToolCall(
                finalizeVerifyTool,
                CriticResultFromLLM(
                    isCorrect = true,
                    feedback = "Output is complete"
                )
            ) onCondition { request ->
                request.contains("verify", ignoreCase = true) ||
                    request.contains("check", ignoreCase = true)
            }

            // Fallback for any non-verify requests to ensure task agents always finalize
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("OK")) onCondition { request ->
                !request.contains("verify", ignoreCase = true) &&
                    !request.contains("check", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("Raw data: John is 30"))

        assertIs<FlowAgentInput.InputCritiqueResult>(result)
        assertTrue(result.success)
    }

    @Test
    fun testStringComparisonFlow_english() = runTest {
        val jsonContent = readFlow("string_comparison_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Language detector detects English
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("en")) onCondition { request ->
                request.contains("Detect the language", ignoreCase = true)
            }

            // English processor
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Processed English text")) onCondition { request ->
                request.contains("Process this English text", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("Hello, how are you?"))

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("English"))
    }

    @Test
    fun testMultiConditionRoutingFlow_safeContent() = runTest {
        val jsonContent = readFlow("multi_condition_routing_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Content analyzer returns safe
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputBoolean(true)) onCondition { request ->
                request.contains("Analyze the input content", ignoreCase = true)
            }

            // Safe content processor
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Content approved for publication")) onCondition { request ->
                request.contains("approved content", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("This is safe content"))

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("approved") || result.data.contains("publication"))
    }

    @Test
    fun testComplexDecisionTreeFlow_invoice() = runTest {
        val jsonContent = readFlow("complex_decision_tree_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Document classifier identifies invoice
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("invoice")) onCondition { request ->
                request.contains("Classify the document type", ignoreCase = true) || request.contains("classify", ignoreCase = true)
            }

            // Invoice processor
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Invoice data extracted")) onCondition { request ->
                request.contains("Extract invoice number", ignoreCase = true) || request.contains("process invoices", ignoreCase = true)
            }

            // Invoice validator succeeds
            @OptIn(InternalAgentsApi::class)
            mockLLMToolCall(
                finalizeVerifyTool,
                CriticResultFromLLM(
                    isCorrect = true,
                    feedback = "All fields valid"
                )
            ) onCondition { request ->
                request.contains("validate", ignoreCase = true) || request.contains("Verify all required", ignoreCase = true)
            }

            // Final archiver
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Document archived")) onCondition { request ->
                request.contains("Archive the processed document", ignoreCase = true) || request.contains("archive", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("Invoice #12345, Date: 2024-01-01, Amount: $100"))

        assertIs<FlowAgentInput.InputString>(result)
        assertTrue(result.data.contains("archived") || result.data.contains("Document"))
    }

    @Test
    fun testVerifyTransformFlow_successPath() = runTest {
        val jsonContent = readFlow("verify_transform_flow.json")
        val parser = FlowJsonConfigParser()
        val flowConfig = parser.parse(jsonContent)

        val testExecutor = getMockExecutor {
            // Task agent generates greeting
            mockLLMToolCall(finalizeTaskTool, FlowAgentInput.InputString("Hello, World!")) onCondition { request ->
                request.contains("generate", ignoreCase = true)
            }

            // Verify agent validates successfully
            @OptIn(InternalAgentsApi::class)
            mockLLMToolCall(
                finalizeVerifyTool,
                CriticResultFromLLM(
                    isCorrect = true,
                    feedback = "Valid greeting"
                )
            ) onCondition { request ->
                request.contains("verify", ignoreCase = true)
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

        val result = flow.run(FlowAgentInput.InputString("Create a greeting"))

        assertIs<FlowAgentInput.InputCritiqueResult>(result)
        assertTrue(result.success)
    }

    //endregion Examples

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
