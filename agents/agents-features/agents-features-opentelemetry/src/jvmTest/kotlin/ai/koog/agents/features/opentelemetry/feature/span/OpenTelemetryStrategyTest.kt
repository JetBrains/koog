package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetryStrategyTest : OpenTelemetryTestBase() {

    @Test
    fun `test strategy spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val strategyName = "test-strategy"
        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>(strategyName) {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = true
        )

        val runId = collectedTestData.lastRunId

        val strategySpans = collectedTestData.filterStrategySpans()
        assertTrue(strategySpans.isNotEmpty(), "Strategy spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "strategy $strategyName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.strategy.name" to strategyName,
                        "koog.event.id" to collectedTestData.singleStrategyEventIds(strategyName),
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, strategySpans)
    }

    @Test
    fun `test strategy spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val strategyName = "test-strategy"
        val nodeName = "test-node"
        val nodeOutput = "$userInput (node)"

        val strategy = strategy<String, String>(strategyName) {
            val nodeBlank by node<String, String>(nodeName) { nodeOutput }
            nodeStart then nodeBlank then nodeFinish
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            verbose = false
        )

        val runId = collectedTestData.lastRunId

        val strategySpans = collectedTestData.filterStrategySpans()
        assertTrue(strategySpans.isNotEmpty(), "Strategy spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "strategy $strategyName" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.strategy.name" to strategyName,
                        "koog.event.id" to collectedTestData.singleStrategyEventIds(strategyName),
                    ),
                    "events" to emptyMap()
                )
            ),
        )

        assertSpans(expectedSpans, strategySpans)
    }
}
