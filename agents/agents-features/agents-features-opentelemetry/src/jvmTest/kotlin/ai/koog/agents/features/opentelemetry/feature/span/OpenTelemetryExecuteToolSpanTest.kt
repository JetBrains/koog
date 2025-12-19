package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.MockToolCallResponse
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_LONDON
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleToolCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.utils.HiddenString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetryExecuteToolSpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test execute tool spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args("Paris"),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
        )
        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse,
        )

        val actualSpans = collectedTestData.filterExecuteToolSpans()
        assertTrue(actualSpans.isNotEmpty(), "ExecuteTool spans should be created during agent execution")

        val actualToolCallEventIds = collectedTestData.collectedToolEventIds
        assertTrue(actualSpans.isNotEmpty(), "Tool Call event ids should be collected during agent execution")

        val serializedArgs = TestGetWeatherTool.encodeArgsToString(mockToolCallResponse.arguments)

        val expectedSpans = listOf(
            mapOf(
                "${TestGetWeatherTool.name}.${actualToolCallEventIds[0]}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to TestGetWeatherTool.encodeResult(mockToolCallResponse.toolResult).toString(),
                        "input.value" to serializedArgs,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.tool.call.id" to mockToolCallResponse.toolCallId,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                    ),
                    "events" to mapOf()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test execute tool spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args("Paris"),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
        )
        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse,
            verbose = false,
        )

        val actualSpans = collectedTestData.filterExecuteToolSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val actualToolCallEventIds = collectedTestData.collectedToolEventIds
        assertTrue(actualSpans.isNotEmpty(), "Tool Call event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "${TestGetWeatherTool.name}.${actualToolCallEventIds[0]}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "input.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.tool.call.id" to mockToolCallResponse.toolCallId,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                    ),
                    "events" to mapOf()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test execute tool spans with parallel tools execution`() = runTest {
        val userPrompt = "What is the weather in Paris and London?"

        val strategy = strategy("test-tool-calls-strategy") {
            val nodeCallLLM by nodeLLMRequestMultiple("test-llm-call")
            val nodeExecuteTool by nodeExecuteMultipleTools("test-multiple-tool-calls", parallelTools = true)
            val nodeSendToolResult by nodeLLMSendMultipleToolResults("test-node-llm-send-multiple-tool-results")

            edge(nodeStart forwardTo nodeCallLLM)
            edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
            edge(
                nodeCallLLM forwardTo nodeFinish
                    onMultipleAssistantMessages { true }
                    transformed { it.joinToString("\n") { message -> message.content } }
            )
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
            edge(
                nodeSendToolResult forwardTo nodeFinish
                    onMultipleAssistantMessages { true }
                    transformed { it.joinToString("\n") { message -> message.content } }
            )
        }

        val toolRegistry = ToolRegistry {
            tool(TestGetWeatherTool)
        }

        val toolArgsParis = TestGetWeatherTool.Args("Paris")
        val toolArgsLondon = TestGetWeatherTool.Args("London")

        val serializedToolArgsParis = TestGetWeatherTool.encodeArgsToString(toolArgsParis)
        val serializedToolArgsLondon = TestGetWeatherTool.encodeArgsToString(toolArgsLondon)

        val serializedToolResultParis = TestGetWeatherTool.encodeResultToString(TestGetWeatherTool.DEFAULT_PARIS_RESULT)
        val serializedToolResultLondon = TestGetWeatherTool.encodeResultToString(TestGetWeatherTool.DEFAULT_LONDON_RESULT)

        val executor = getMockExecutor(clock = testClock) {
            // Mock tool call
            val toolCalls = listOf(
                TestGetWeatherTool to toolArgsParis,
                TestGetWeatherTool to toolArgsLondon,
            )
            mockLLMToolCall(toolCalls) onRequestEquals userPrompt

            // Mock responses from the "send tool result" node
            mockLLMAnswer(MOCK_LLM_RESPONSE_PARIS) onRequestContains serializedToolResultParis
            mockLLMAnswer(MOCK_LLM_RESPONSE_LONDON) onRequestContains serializedToolResultLondon
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            executor = executor,
            toolRegistry = toolRegistry,
            userPrompt = userPrompt
        )

        val actualSpans = collectedTestData.filterExecuteToolSpans()
            .sortedBy { span ->
                // Filter by tool attributes stored in the 'input.value' span attribute
                val inputValueAttributes = span.attributes.asMap().filter { entry -> entry.key.key == "input.value" }
                inputValueAttributes.values.singleOrNull().toString()
            }

        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val actualToolCallEventIds = collectedTestData.collectedToolEventIds
        assertTrue(actualSpans.isNotEmpty(), "Tool Call event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                // London
                // Note! Do not include the 'toolCallId' property as it is not provided for a case on multiple tools calls
                "${TestGetWeatherTool.name}.${actualToolCallEventIds[1]}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to "\"$serializedToolResultLondon\"",
                        "input.value" to serializedToolArgsLondon,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                    ),
                    "events" to mapOf()
                )
            ),
            // Paris
            // Note! Do not include the 'toolCallId' property as it is not provided for a case on multiple tools calls
            mapOf(
                "${TestGetWeatherTool.name}.${actualToolCallEventIds[0]}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to "\"$serializedToolResultParis\"",
                        "input.value" to serializedToolArgsParis,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                    ),
                    "events" to mapOf()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
