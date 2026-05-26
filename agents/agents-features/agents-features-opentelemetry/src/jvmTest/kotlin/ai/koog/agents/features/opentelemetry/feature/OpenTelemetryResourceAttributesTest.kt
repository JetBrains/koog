package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.defaultMockExecutor
import ai.koog.agents.features.opentelemetry.mock.TestSpanProcessor
import ai.koog.utils.io.use
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenTelemetryResourceAttributesTest {

    @Test
    fun testResourceAttributesAppearsInEverySpan() = runBlocking {
        val testProcessor = TestSpanProcessor()

        val customAttributes = mapOf(
            "custom.deploy.env" to "test-env",
            "custom.team" to "koog",
            "custom.build.number" to 42L,
            "custom.debug" to true,
            "custom.threshold" to 0.95,
        )

        createAgent(
            strategy = getSingleLLMCallStrategy(AgentType.Graph),
            executor = defaultMockExecutor,
        ) {
            addSpanProcessor { testProcessor }
            addResourceAttributes(customAttributes)
        }.use { agent ->
            agent.run(USER_PROMPT_PARIS)
        }

        val spans = testProcessor.collectedSpans
        assertTrue(spans.isNotEmpty(), "Spans must be created during agent execution")

        spans.forEach { span ->
            val resource = span.resource.attributes
            customAttributes.forEach { (key, expectedValue) ->
                assertEquals(
                    expectedValue,
                    resource[key],
                    "Span '${span.name}': resource attribute '$key' must equal $expectedValue"
                )
            }
        }
    }

    @Test
    fun testMultipleAddResourceAttributesCallsAreMerged() = runBlocking {
        val testProcessor = TestSpanProcessor()

        createAgent(
            strategy = getSingleLLMCallStrategy(AgentType.Graph),
            executor = defaultMockExecutor,
        ) {
            addSpanProcessor { testProcessor }
            addResourceAttributes(mapOf("custom.first" to "value-a"))
            addResourceAttributes(mapOf("custom.second" to "value-b"))
        }.use { agent ->
            agent.run(USER_PROMPT_PARIS)
        }

        val resource = testProcessor.collectedSpans.first().resource.attributes
        assertEquals("value-a", resource["custom.first"], "First addResourceAttributes call must not be overwritten")
        assertEquals("value-b", resource["custom.second"], "Second addResourceAttributes call must be merged in")
    }
}
