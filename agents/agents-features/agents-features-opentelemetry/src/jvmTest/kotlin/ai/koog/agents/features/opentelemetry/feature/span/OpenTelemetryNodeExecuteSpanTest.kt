package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertSpans
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.utils.HiddenString
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
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$nodeName.${collectedTestData.singleNodeIdByName(nodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeName,
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$nodeOutput\"",
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
                "node.$nodeName.${collectedTestData.singleNodeIdByName(nodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeName,
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
    fun `test node execute spans with parallel nodes execution`() = runTest {
        val userInput = "Generate a joke"

        val nodeGenerateJokesName = "node-generate-jokes"
        val nodeFirstJokeName = "node-first-joke"
        val nodeSecondJokeName = "node-second-joke"
        val nodeThirdJokeName = "node-third-joke"

        val nodeFirstJokeOutput = "First joke: Why do programmers prefer dark mode? Because light attracts bugs!"
        val nodeSecondJokeOutput = "Second joke: Why do Java developers wear glasses? Because they don't C#!"
        val nodeThirdJokeOutput = "Third joke: A SQL query walks into a bar, walks up to two tables and asks, 'Can I join you?'"

        val strategy = strategy("test-parallel-strategy") {
            val nodeFirstJoke by node<String, String>(nodeFirstJokeName) { nodeFirstJokeOutput }
            val nodeSecondJoke by node<String, String>(nodeSecondJokeName) { nodeSecondJokeOutput }
            val nodeThirdJoke by node<String, String>(nodeThirdJokeName) { nodeThirdJokeOutput }

            // Define a node to run joke generation in parallel
            val nodeGenerateJokes by parallel(nodeFirstJoke, nodeSecondJoke, nodeThirdJoke, name = nodeGenerateJokesName) {
                selectByIndex {
                    // Always select the first joke for testing purposes
                    0
                }
            }

            edge(nodeStart forwardTo nodeGenerateJokes)
            edge(nodeGenerateJokes forwardTo nodeFinish)
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
            .sortedWith { one, other ->
                if (one.name.contains("-joke.") && other.name.contains("-joke.")) {
                    one.name.compareTo(other.name)
                } else {
                    0
                }
            }

        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to "\"$result\"",
                        "koog.node.input" to "\"$result\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$nodeGenerateJokesName.${collectedTestData.singleNodeIdByName(nodeGenerateJokesName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeGenerateJokesName,
                        "koog.node.output" to "\"$nodeFirstJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$nodeFirstJokeName.${collectedTestData.singleNodeIdByName(nodeFirstJokeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeFirstJokeName,
                        "koog.node.output" to "\"$nodeFirstJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$nodeSecondJokeName.${collectedTestData.singleNodeIdByName(nodeSecondJokeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeSecondJokeName,
                        "koog.node.output" to "\"$nodeSecondJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$nodeThirdJokeName.${collectedTestData.singleNodeIdByName(nodeThirdJokeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeThirdJokeName,
                        "koog.node.output" to "\"$nodeThirdJokeOutput\"",
                        "koog.node.input" to "\"$userInput\"",
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
                "node.$nodeWithErrorName.${collectedTestData.singleNodeIdByName(nodeWithErrorName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to nodeWithErrorName,
                        "koog.node.input" to "\"$userInput\"",
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
