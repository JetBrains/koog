package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.mock.MockContextFactory
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.features.opentelemetry.span.endInferenceSpan
import ai.koog.agents.features.opentelemetry.span.startInferenceSpan
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryResponseMetadataTest {

    private val tracer = MockTracer()
    private val contextFactory = MockContextFactory()
    private val provider = MockLLMProvider()
    private val model = LLModel(provider, "test-model")
    private val clock = KoogClock.System

    private fun createInferenceSpan(id: String) = startInferenceSpan(
        tracer = tracer,
        contextFactory = contextFactory,
        parentSpan = null,
        id = id,
        provider = provider,
        runId = "run-$id",
        model = model,
        messages = emptyList(),
        llmParams = LLMParams(),
        tools = emptyList()
    )

    @Test
    fun `test response metadata from ResponseMetaInfo is forwarded to inference span`() {
        val metadata = JsonObject(
            mapOf(
                "cache_status" to JsonPrimitive("hit"),
                "region" to JsonPrimitive("us-east-1"),
            )
        )

        val span = createInferenceSpan("metadata-forwarded")

        val responseMessage = Message.Assistant(
            "test response",
            ResponseMetaInfo(
                timestamp = clock.now(),
                inputTokensCount = 10,
                outputTokensCount = 20,
                metadata = metadata,
            )
        )

        endInferenceSpan(span = span, message = responseMessage, model = model, verbose = true)

        val metadataAttribute = span.attributes.find { it.key == "gen_ai.response.metadata" }
        assertNotNull(metadataAttribute, "gen_ai.response.metadata attribute should be present")
        assertEquals(metadata.toString(), metadataAttribute.value)
    }

    @Test
    fun `test response metadata attribute is omitted when ResponseMetaInfo has no metadata`() {
        val span = createInferenceSpan("no-metadata")

        val responseMessage = Message.Assistant(
            "test response",
            ResponseMetaInfo(
                timestamp = clock.now(),
                inputTokensCount = 10,
                outputTokensCount = 20,
            )
        )

        endInferenceSpan(span = span, message = responseMessage, model = model, verbose = true)

        val metadataAttribute = span.attributes.find { it.key == "gen_ai.response.metadata" }
        assertNull(metadataAttribute, "gen_ai.response.metadata attribute should not be present when metadata is null")
    }
}
