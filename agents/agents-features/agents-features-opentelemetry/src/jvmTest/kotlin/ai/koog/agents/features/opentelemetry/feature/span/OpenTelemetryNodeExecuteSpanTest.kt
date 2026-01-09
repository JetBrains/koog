package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class OpenTelemetryNodeExecuteSpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test node execute spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Node spans should be created during agent execution")

        val actualNodeEventIds = collectedTestData.collectedNodeIds
        assertTrue(actualNodeEventIds.isNotEmpty(), "Node event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node $START_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $nodeName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeName,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$nodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $FINISH_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execute spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            verbose = false,
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val actualNodeEventIds = collectedTestData.collectedNodeIds
        assertTrue(actualNodeEventIds.isNotEmpty(), "Node event ids should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node $START_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $nodeName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeName,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $FINISH_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execute spans with parallel nodes execution`() = runBlocking {
        val userInput = "Test input"

        val nodeParallelName = "node-select-by-index-parallel"
        val node1Name = "node-1-id"
        val node2Name = "node-2-id"
        val node3Name = "node-3-id"

        val node1Output = "first"
        val node2Output = "second"
        val node3Output = "third"

        val strategy = strategy<String, String>("test-select-by-index") {
            val node1 by node<String, String>(node1Name) { node1Output }
            val node2 by node<String, String>(node2Name) { node2Output }
            val node3 by node<String, String>(node3Name) { node3Output }

            val parallelNode by parallel(node1, node2, node3, name = nodeParallelName) {
                val selected = selectByIndex { 1 }
                ParallelNodeExecutionResult(selected.output, this)
            }

            edge(nodeStart forwardTo parallelNode)
            edge(parallelNode forwardTo nodeFinish)
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
            .sortedWith { one, other ->
                if (one.name.contains("node-") && other.name.contains("node-")) {
                    one.name.compareTo(other.name)
                } else {
                    0
                }
            }

        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node $START_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $node1Name" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to node1Name,
                        "koog.node.output" to "\"$node1Output\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $node2Name" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to node2Name,
                        "koog.node.output" to "\"$node2Output\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $node3Name" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to node3Name,
                        "koog.node.output" to "\"$node3Output\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $nodeParallelName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeParallelName,
                        "koog.node.output" to "\"$node2Output\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $FINISH_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to FINISH_NODE_PREFIX,
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execution spans for node with error`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val nodeWithErrorName = "node-with-error"
        val testErrorMessage = "Test error"

        val strategy = strategy("test-strategy") {
            val nodeWithError by node<String, String>(nodeWithErrorName) {
                throw IllegalStateException(testErrorMessage)
            }

            nodeStart then nodeWithError then nodeFinish
        }

        val collectedTestData = OpenTelemetryTestData()

        val throwable = assertFails {
            runAgentWithStrategy(
                userPrompt = userInput,
                strategy = strategy,
                collectedTestData = collectedTestData
            )
        }

        val runId = collectedTestData.lastRunId
        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        assertEquals(testErrorMessage, throwable.message)

        val expectedSpans = listOf(
            mapOf(
                "node $START_NODE_PREFIX" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to START_NODE_PREFIX,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node $nodeWithErrorName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.id" to nodeWithErrorName,
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
