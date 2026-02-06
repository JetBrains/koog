package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetryAgentSpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test create and invoke agent spans are collected`() = runTest {
        val userInput = "User input"

        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

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

        val expectedOutputMessages = listOf(
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
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages),
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
    @Test
    fun `test create and invoke agent spans with verbose logging disabled`() = runTest {
        val userInput = "User input"

        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

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
                        "gen_ai.output.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
