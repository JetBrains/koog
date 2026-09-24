package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSimpleStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenTelemetryAgentSpanTest : OpenTelemetryTestBase() {

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test create and invoke agent spans are collected`(agentType: AgentType) = runTest {
        val userInput = "User input"

        val strategy = getSimpleStrategy(agentType)

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = true,
        )

        val runId = collectedTestData.lastRunId
        val agentId = DEFAULT_AGENT_ID
        val model = defaultModel

        val actualCreateAgentSpans = collectedTestData.filterCreateAgentSpans()
        val actualInvokeAgentSpans = collectedTestData.filterAgentInvokeSpans()
        val actualSpans = actualCreateAgentSpans + actualInvokeAgentSpans
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val createAgentEventId = collectedTestData.singleAttributeValue(
            actualCreateAgentSpans.single(),
            "koog.event.id"
        )

        val expectedInputMessages = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CREATE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.model" to model.id,
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to createAgentEventId
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "${OperationNameType.INVOKE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to runId,
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    /**
     * Test create and invoke agent spans with verbose logging disabled
     *
     * Verbose level does not affect logs visibility for agent create and invoke spans
     */
    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test create and invoke agent spans with verbose logging disabled`(agentType: AgentType) = runTest {
        val userInput = "User input"

        val strategy = getSimpleStrategy(agentType)

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = false,
        )

        val runId = collectedTestData.lastRunId
        val agentId = DEFAULT_AGENT_ID
        val model = defaultModel

        val actualCreateAgentSpans = collectedTestData.filterCreateAgentSpans()
        val actualInvokeAgentSpans = collectedTestData.filterAgentInvokeSpans()
        val actualSpans = actualCreateAgentSpans + actualInvokeAgentSpans
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val createAgentEventId = collectedTestData.singleAttributeValue(
            actualCreateAgentSpans.single(),
            "koog.event.id"
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CREATE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.model" to model.id,
                        "system_instructions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.event.id" to createAgentEventId
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "${OperationNameType.INVOKE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "system_instructions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.event.id" to runId,
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun testInvokeAgentSpanReportsTheResponseAndRealUsage() = runTest {
        val mockResponse = "The weather in Paris is rainy, around 14 degrees."

        // getSingleLLMCallStrategy: the simple strategy never calls the LLM, so there would be no tokens to count.
        val collectedTestData = runAgentWithStrategy(
            strategy = getSingleLLMCallStrategy(AgentType.Graph),
            userPrompt = "User input",
            executor = getMockExecutor(tokenizer = SimpleRegexBasedTokenizer()) {
                mockLLMAnswer(mockResponse).asDefaultResponse
            },
            verbose = true,
        )

        val invokeSpan = collectedTestData.filterAgentInvokeSpans().single()
        val inputTokens = collectedTestData.singleAttributeValue(invokeSpan, "gen_ai.usage.input_tokens")
        val outputTokens = collectedTestData.singleAttributeValue(invokeSpan, "gen_ai.usage.output_tokens")

        assertTrue(inputTokens.toString().toLong() > 0L, "invoke_agent should report input tokens from the live conversation")
        assertTrue(outputTokens.toString().toLong() > 0L, "invoke_agent should report output tokens from the live conversation")

        // gen_ai.output.messages must carry the assistant's actual reply, not an echo of gen_ai.input.messages
        // (the bug this branch fixes: invoke_agent reported its own input as its output).
        val expectedOutputMessages = getMessagesString(listOf(OpenTelemetryTestAPI.assistantMessage(mockResponse)))
        val actualOutputMessages = collectedTestData.singleAttributeValue(invokeSpan, "gen_ai.output.messages")

        assertEquals(
            expectedOutputMessages,
            actualOutputMessages,
            "invoke_agent should report the assistant's response on gen_ai.output.messages, not the request echoed back"
        )
    }
}
