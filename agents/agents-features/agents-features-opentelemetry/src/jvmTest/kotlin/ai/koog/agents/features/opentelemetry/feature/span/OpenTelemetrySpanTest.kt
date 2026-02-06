package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.dsl.builder.strategy
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
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
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

            val agentService = OpenTelemetryTestAPI.createAgentService(
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
                temperature = OpenTelemetryTestAPI.Parameter.TEMPERATURE,
            ) {
                install(OpenTelemetry.Feature) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
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
            val startNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(START_NODE_PREFIX)
            val testNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(nodeName)
            val finishNodeEvents = collectedTestData.filterNodeEventIdsByNodeId(FINISH_NODE_PREFIX)

            val expectedSpans = listOf(
                // First run
                mapOf(
                    "node $START_NODE_PREFIX" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to START_NODE_PREFIX,
                            "koog.node.input" to "\"$userPrompt0\"",
                            "koog.node.output" to "\"$userPrompt0\"",
                            "koog.event.id" to startNodeEvents[0],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node $nodeName" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to nodeName,
                            "koog.node.input" to "\"$userPrompt0\"",
                            "koog.node.output" to "\"$nodeOutput0\"",
                            "koog.event.id" to testNodeEvents[0],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node $FINISH_NODE_PREFIX" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.id" to FINISH_NODE_PREFIX,
                            "koog.node.input" to "\"$nodeOutput0\"",
                            "koog.node.output" to "\"$nodeOutput0\"",
                            "koog.event.id" to finishNodeEvents[0],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "strategy $strategyName" to mapOf(
                        "attributes" to mapOf(
                            "koog.strategy.name" to strategyName,
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.event.id" to strategyEvents[0]
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
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
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
                            "koog.event.id" to mockExporter.runIds[0],
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
                            "koog.event.id" to actualCreateAgentEvents[0]
                        ),
                        "events" to emptyMap()
                    )
                ),

                // Second run
                mapOf(
                    "node $START_NODE_PREFIX" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to START_NODE_PREFIX,
                            "koog.node.input" to "\"$userPrompt1\"",
                            "koog.node.output" to "\"$userPrompt1\"",
                            "koog.event.id" to startNodeEvents[1],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node $nodeName" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to nodeName,
                            "koog.node.input" to "\"$userPrompt1\"",
                            "koog.node.output" to "\"$nodeOutput1\"",
                            "koog.event.id" to testNodeEvents[1],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node $FINISH_NODE_PREFIX" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.id" to FINISH_NODE_PREFIX,
                            "koog.node.input" to "\"$nodeOutput1\"",
                            "koog.node.output" to "\"$nodeOutput1\"",
                            "koog.event.id" to finishNodeEvents[1],
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "strategy $strategyName" to mapOf(
                        "attributes" to mapOf(
                            "koog.strategy.name" to strategyName,
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.event.id" to strategyEvents[1]
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
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
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
                            "koog.event.id" to mockExporter.runIds[1],
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
                            "koog.event.id" to actualCreateAgentEvents[1]
                        ),
                        "events" to emptyMap()
                    )
                ),
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }
}
