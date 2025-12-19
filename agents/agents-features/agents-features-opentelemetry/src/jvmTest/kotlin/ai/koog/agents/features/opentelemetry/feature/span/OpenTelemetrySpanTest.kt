package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.NodeInfo
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_LONDON
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_LONDON
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.installEventDataCollector
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for the OpenTelemetry feature.
 *
 * These tests verify that spans are created correctly during agent execution
 * and that the structure of spans matches the expected hierarchy.
 */
class OpenTelemetrySpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test spans for same agent run multiple times`() = runTest {
        MockSpanExporter().use { mockExporter ->
            val systemPrompt = SYSTEM_PROMPT

            val userPrompt0 = USER_PROMPT_PARIS
            val nodeOutput0 = MOCK_LLM_RESPONSE_PARIS

            val userPrompt1 = USER_PROMPT_LONDON
            val nodeOutput1 = MOCK_LLM_RESPONSE_LONDON

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"

            val strategyName = "test-strategy"
            val nodeName = "test-node"

            var index = 0

            val strategy = strategy<String, String>(strategyName) {
                val nodeBlank by node<String, String>(nodeName) {
                    if (index == 0) {
                        nodeOutput0
                    } else {
                        nodeOutput1
                    }
                }
                nodeStart then nodeBlank then nodeFinish
            }

            val collectedTestData = OpenTelemetryTestData().apply {
                this.collectedSpans = mockExporter.collectedSpans
            }

            var nodesInfo0 = listOf<NodeInfo>()
            var nodesInfo1 = listOf<NodeInfo>()

            val agentService = OpenTelemetryTestAPI.createAgentService(
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
            ) {
                install(OpenTelemetry.Feature) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }

                installEventDataCollector().also {
                    if (index == 0) {
                        nodesInfo0 = it.nodeData
                    } else {
                        nodesInfo1 = it.nodeData
                    }
                }
            }

            agentService.createAgentAndRun(userPrompt0, id = agentId)
            index++
            agentService.createAgentAndRun(userPrompt1, id = agentId)

            val collectedSpans = collectedTestData.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agentService.closeAll()

            // Check each span

            val model = defaultModel

            val expectedSpans = listOf(
                // First run
                mapOf(
                    "$START_NODE_PREFIX.${nodesInfo0.single { it.nodeId == START_NODE_PREFIX }.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to START_NODE_PREFIX,
                            "koog.node.input" to "\"$userPrompt0\"",
                            "koog.node.output" to "\"$userPrompt0\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "$nodeName.${nodesInfo0.single { it.nodeId == nodeName }.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to nodeName,
                            "koog.node.input" to "\"$userPrompt0\"",
                            "koog.node.output" to "\"$nodeOutput0\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "$FINISH_NODE_PREFIX.${nodesInfo0.single { it.nodeId == FINISH_NODE_PREFIX }.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to FINISH_NODE_PREFIX,
                            "koog.node.input" to "\"$nodeOutput0\"",
                            "koog.node.output" to "\"$nodeOutput0\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    strategyName to mapOf(
                        "attributes" to mapOf(
                            "koog.strategy.name" to strategyName,
                            "gen_ai.conversation.id" to mockExporter.runIds[0]
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "invoke.${mockExporter.runIds[0]}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[0]
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    agentId to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id
                        ),
                        "events" to emptyMap()
                    )
                ),

                // Second run
                mapOf(
                    "$START_NODE_PREFIX.${nodesInfo1.single { it.nodeId == START_NODE_PREFIX }.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to START_NODE_PREFIX,
                            "koog.node.input" to "\"$userPrompt1\"",
                            "koog.node.output" to "\"$userPrompt1\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "$nodeName.${nodesInfo1.single { it.nodeId == nodeName }.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to nodeName,
                            "koog.node.input" to "\"$userPrompt1\"",
                            "koog.node.output" to "\"$nodeOutput1\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "$FINISH_NODE_PREFIX.${nodesInfo1.single { it.nodeId == FINISH_NODE_PREFIX}.eventId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to FINISH_NODE_PREFIX,
                            "koog.node.input" to "\"$nodeOutput1\"",
                            "koog.node.output" to "\"$nodeOutput1\"",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    strategyName to mapOf(
                        "attributes" to mapOf(
                            "koog.strategy.name" to strategyName,
                            "gen_ai.conversation.id" to mockExporter.runIds[1]
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "invoke.${mockExporter.runIds[1]}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[1]
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    agentId to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to OperationNameType.CREATE_AGENT.id,
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id
                        ),
                        "events" to emptyMap()
                    )
                ),
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }
}
