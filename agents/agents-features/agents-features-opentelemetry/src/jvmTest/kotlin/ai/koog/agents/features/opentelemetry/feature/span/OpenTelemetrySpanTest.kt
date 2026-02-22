package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_LONDON
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_LONDON
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertTrue

/**
 * Tests for the OpenTelemetry feature.
 *
 * These tests verify that spans are created correctly during agent execution
 * and that the structure of spans matches the expected hierarchy.
 */
class OpenTelemetrySpanTest : OpenTelemetryTestBase() {

    val systemPrompt = SYSTEM_PROMPT

    val userPrompt0 = USER_PROMPT_PARIS
    val nodeOutput0 = MOCK_LLM_RESPONSE_PARIS

    val userPrompt1 = USER_PROMPT_LONDON
    val nodeOutput1 = MOCK_LLM_RESPONSE_LONDON

    val agentId = "test-agent-id"
    val promptId = "test-prompt-id"

    val strategyName = "test-strategy"
    val nodeName = "test-node"

    private fun getExpectedCommonSpans(
        runId: String,
        model: LLModel,
        strategyEvent: String,
        actualCreateAgentEvent: String
    ) = listOf(
        mapOf(
            "strategy $strategyName" to mapOf(
                "attributes" to mapOf(
                    "koog.strategy.name" to strategyName,
                    "gen_ai.conversation.id" to runId,
                    "koog.event.id" to strategyEvent
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
                    "gen_ai.input.messages" to getMessagesString(
                        listOf(
                            Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                            // User message is not added in invoked agent span
                            // as it is propagated through user input in run() agent method
                        )
                    ),
                    "system_instructions" to getSystemInstructionsString(listOf(systemPrompt)),
                    "gen_ai.response.model" to model.id,
                    "gen_ai.usage.input_tokens" to 0L,
                    "gen_ai.usage.output_tokens" to 0L,
                    "gen_ai.output.messages" to getMessagesString(
                        listOf(
                            Message.System(systemPrompt, RequestMetaInfo(OpenTelemetryTestAPI.testClock.now())),
                            // User message is not added in invoked agent span
                            // as it is propagated through user input in run() agent method
                        )
                    ),
                    "koog.event.id" to runId,
                ),
                "events" to emptyMap()
            )
        ),
        mapOf(
            "${OperationNameType.CREATE_AGENT.id} $agentId" to mapOf(
                "attributes" to mapOf(
                    "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                    "gen_ai.provider.name" to model.provider.id,
                    "gen_ai.agent.id" to agentId,
                    "gen_ai.request.model" to model.id,
                    "system_instructions" to getSystemInstructionsString(listOf(systemPrompt)),
                    "koog.event.id" to actualCreateAgentEvent
                ),
                "events" to emptyMap()
            )
        )
    )

    private fun getExpectedNodeSpans(
        runId: String,
        userPrompt: String,
        nodeOutput: String,
        startNodeEvent: String,
        testNodeEvent: String,
        finishNodeEvent: String
    ) = listOf(
        // First run
        mapOf(
            "node $START_NODE_PREFIX" to mapOf(
                "attributes" to mapOf(
                    "gen_ai.conversation.id" to runId,
                    "koog.node.id" to START_NODE_PREFIX,
                    "koog.node.input" to "\"$userPrompt\"",
                    "koog.node.output" to "\"$userPrompt\"",
                    "koog.event.id" to startNodeEvent,
                ),
                "events" to emptyMap()
            )
        ),
        mapOf(
            "node $nodeName" to mapOf(
                "attributes" to mapOf(
                    "gen_ai.conversation.id" to runId,
                    "koog.node.id" to nodeName,
                    "koog.node.input" to "\"$userPrompt\"",
                    "koog.node.output" to "\"$nodeOutput\"",
                    "koog.event.id" to testNodeEvent,
                ),
                "events" to emptyMap()
            )
        ),
        mapOf(
            "node $FINISH_NODE_PREFIX" to mapOf(
                "attributes" to mapOf(
                    "gen_ai.conversation.id" to runId,
                    "koog.node.id" to FINISH_NODE_PREFIX,
                    "koog.node.input" to "\"$nodeOutput\"",
                    "koog.node.output" to "\"$nodeOutput\"",
                    "koog.event.id" to finishNodeEvent,
                ),
                "events" to emptyMap()
            )
        )
    )

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test spans for same agent run multiple times`(agentType: AgentType) = runTest {
        MockSpanExporter().use { mockExporter ->
            var index = 0

            val strategy = when (agentType) {
                AgentType.Graph -> strategy<String, String>(strategyName) {
                    val nodeBlank by node<String, String>(nodeName) {
                        if (index == 0) {
                            nodeOutput0
                        } else {
                            nodeOutput1
                        }
                    }
                    nodeStart then nodeBlank then nodeFinish
                }

                AgentType.Functional -> functionalStrategy(strategyName) { it }
            }

            val collectedTestData = OpenTelemetryTestData().apply {
                this.collectedSpans = mockExporter.collectedSpans
            }

            val agentService = OpenTelemetryTestAPI.createAgentService(
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
                temperature = OpenTelemetryTestAPI.Parameter.TEMPERATURE,
            ) {
                addSpanExporter(mockExporter)
                setVerbose(true)
            }

            agentService.createAgentAndRun(userPrompt0, id = agentId)
            index++
            agentService.createAgentAndRun(userPrompt1, id = agentId)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            // Check spans
            val model = defaultModel

            val actualCreateAgentEvents = collectedTestData.filterCreateAgentEventIds(agentId)
            val strategyEvents = collectedTestData.filterStrategyEventIds(strategyName)

            val expectedCommonSpansFirstRun = getExpectedCommonSpans(
                mockExporter.runIds[0],
                model,
                strategyEvents[0],
                actualCreateAgentEvents[0]
            )
            val expectedCommonSpansSecondRun = getExpectedCommonSpans(
                mockExporter.runIds[1],
                model,
                strategyEvents[1],
                actualCreateAgentEvents[1]
            )

            val expectedSpans = when (agentType) {
                AgentType.Graph -> {
                    val startNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(START_NODE_PREFIX)
                    val testNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(nodeName)
                    val finishNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(FINISH_NODE_PREFIX)

                    val expectedNodeSpansFirstRun = getExpectedNodeSpans(
                        mockExporter.runIds[0],
                        userPrompt0,
                        nodeOutput0,
                        startNodeEvents[0],
                        testNodeEvents[0],
                        finishNodeEvents[0]
                    )

                    val expectedNodeSpansSecondRun = getExpectedNodeSpans(
                        mockExporter.runIds[1],
                        userPrompt1,
                        nodeOutput1,
                        startNodeEvents[1],
                        testNodeEvents[1],
                        finishNodeEvents[1]
                    )

                    expectedNodeSpansFirstRun + expectedCommonSpansFirstRun + expectedNodeSpansSecondRun + expectedCommonSpansSecondRun
                }

                AgentType.Functional -> expectedCommonSpansFirstRun + expectedCommonSpansSecondRun
            }

            assertSpans(expectedSpans, collectedSpans)
        }
    }
}
