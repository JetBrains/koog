package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_STRATEGY_NAME
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSimpleStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertTrue

class OpenTelemetryStrategyTest : OpenTelemetryTestBase() {

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test strategy spans are collected`(agentType: AgentType) = runTest {
        val userInput = USER_PROMPT_PARIS
        val strategyName = DEFAULT_STRATEGY_NAME

        val strategy = getSimpleStrategy(agentType)

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

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test strategy spans with verbose logging disabled`(agentType: AgentType) = runTest {
        val userInput = USER_PROMPT_PARIS
        val strategyName = DEFAULT_STRATEGY_NAME

        val strategy = getSimpleStrategy(agentType)

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
