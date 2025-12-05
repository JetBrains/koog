package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertSpans
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.MockToolCallResponse
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleToolCallStrategy
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.features.opentelemetry.span.sha256base64
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
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val serializedArgs = TestGetWeatherTool.encodeArgsToString(mockToolCallResponse.arguments)

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the Tool Call event
                "tool.${TestGetWeatherTool.name}.args.${serializedArgs.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to TestGetWeatherTool.encodeResult(mockToolCallResponse.toolResult).toString(),
                        "input.value" to serializedArgs,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.tool.call.id" to mockToolCallResponse.toolCallId,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.description,
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

        val serializedArgs = TestGetWeatherTool.encodeArgsToString(mockToolCallResponse.arguments)

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the Tool Call event
                "tool.${TestGetWeatherTool.name}.args.${serializedArgs.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "output.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "input.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.tool.name" to TestGetWeatherTool.name,
                        "gen_ai.tool.call.id" to mockToolCallResponse.toolCallId,
                        "gen_ai.operation.name" to OperationNameType.EXECUTE_TOOL.id,
                        "gen_ai.tool.description" to TestGetWeatherTool.description,
                    ),
                    "events" to mapOf()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
