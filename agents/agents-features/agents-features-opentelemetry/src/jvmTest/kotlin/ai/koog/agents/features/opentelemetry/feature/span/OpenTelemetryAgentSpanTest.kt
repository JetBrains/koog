package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
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

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput)

        val runId = collectedTestData.lastRunId
        val agentId = DEFAULT_AGENT_ID
        val model = defaultModel

        val actualSpans = collectedTestData.filterCreateAgentSpans() + collectedTestData.filterAgentInvokeSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CREATE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.request.model" to model.id
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "${OperationNameType.INVOKE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId
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

        val actualSpans = collectedTestData.filterCreateAgentSpans() + collectedTestData.filterAgentInvokeSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CREATE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.request.model" to model.id
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "${OperationNameType.INVOKE_AGENT.id} $agentId" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.agent.id" to agentId,
                        "gen_ai.conversation.id" to runId
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
