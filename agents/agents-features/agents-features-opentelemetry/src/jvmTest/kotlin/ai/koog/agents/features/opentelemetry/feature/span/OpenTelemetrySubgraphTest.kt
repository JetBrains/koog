package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertSpans
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetrySubgraphTest : OpenTelemetryTestBase() {

    @Test
    fun `test node execution spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeSubgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }

                nodeStart then nodeSubgraphBlank then nodeFinish
            }

            nodeStart then nodeSubgraph then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput)

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphName.${collectedTestData.singleNodeIdByName(subgraphName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphName,
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__finish__$subgraphName.${collectedTestData.singleNodeIdByName("__finish__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__$subgraphName",
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphNodeName.${collectedTestData.singleNodeIdByName(subgraphNodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphNodeName,
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "node.__start__$subgraphName.${collectedTestData.singleNodeIdByName("__start__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__$subgraphName",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__start__.${collectedTestData.singleNodeIdByName("__start__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test node execution spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeSubgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }

                nodeStart then nodeSubgraphBlank then nodeFinish
            }

            nodeStart then nodeSubgraph then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = false,
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphName.${collectedTestData.singleNodeIdByName(subgraphName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphName,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__finish__$subgraphName.${collectedTestData.singleNodeIdByName("__finish__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__$subgraphName",
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphNodeName.${collectedTestData.singleNodeIdByName(subgraphNodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphNodeName,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "node.__start__$subgraphName.${collectedTestData.singleNodeIdByName("__start__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__$subgraphName",
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__start__.${collectedTestData.singleNodeIdByName("__start__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__",
                        "koog.node.input" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.node.output" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inner and outer node execution spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS

        val rootNodeName = "test-root-node"
        val rootNodeOutput = "$userInput (root)"

        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphNodeOutput = "$userInput (subgraph)"

        val strategy = strategy<String, String>("test-strategy") {
            val nodeSubgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphBlank by node<String, String>(subgraphNodeName) { subgraphNodeOutput }

                nodeStart then nodeSubgraphBlank then nodeFinish
            }

            val nodeBlank by node<String, String>(rootNodeName) { rootNodeOutput }

            nodeStart then nodeSubgraph then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput)

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to "\"$rootNodeOutput\"",
                        "koog.node.input" to "\"$rootNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$rootNodeName.${collectedTestData.singleNodeIdByName(rootNodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to rootNodeName,
                        "koog.node.output" to "\"$rootNodeOutput\"",
                        "koog.node.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphName.${collectedTestData.singleNodeIdByName(subgraphName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphName,
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__finish__$subgraphName.${collectedTestData.singleNodeIdByName("__finish__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__$subgraphName",
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$subgraphNodeOutput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphNodeName.${collectedTestData.singleNodeIdByName(subgraphNodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphNodeName,
                        "koog.node.output" to "\"$subgraphNodeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),

            mapOf(
                "node.__start__$subgraphName.${collectedTestData.singleNodeIdByName("__start__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__$subgraphName",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__start__.${collectedTestData.singleNodeIdByName("__start__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
