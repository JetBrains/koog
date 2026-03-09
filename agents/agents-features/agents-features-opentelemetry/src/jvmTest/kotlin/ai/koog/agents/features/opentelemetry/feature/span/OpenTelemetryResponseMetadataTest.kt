package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.features.opentelemetry.span.endInferenceSpan
import ai.koog.agents.features.opentelemetry.span.startInferenceSpan
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class OpenTelemetryResponseMetadataTest {

    private val tracer = MockTracer()
    private val provider = MockLLMProvider()
    private val model = LLModel(provider, "test-model")
    private val clock = Clock.System

    private fun createInferenceSpan(id: String) = startInferenceSpan(
        tracer = tracer,
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

        val responseMessages = listOf(
            Message.Assistant(
                "test response",
                ResponseMetaInfo(
                    timestamp = clock.now(),
                    inputTokensCount = 10,
                    outputTokensCount = 20,
                    metadata = metadata,
                )
            )
        )

        endInferenceSpan(span = span, messages = responseMessages, model = model, verbose = true)

        val metadataAttribute = span.attributes.find { it.key == "gen_ai.response.metadata" }
        assertNotNull(metadataAttribute, "gen_ai.response.metadata attribute should be present")
        assertEquals(metadata.toString(), metadataAttribute.value)
    }

    @Test
    fun `test response metadata attribute is omitted when ResponseMetaInfo has no metadata`() {
        val span = createInferenceSpan("no-metadata")

        val responseMessages = listOf(
            Message.Assistant(
                "test response",
                ResponseMetaInfo(
                    timestamp = clock.now(),
                    inputTokensCount = 10,
                    outputTokensCount = 20,
                )
            )
        )

        endInferenceSpan(span = span, messages = responseMessages, model = model, verbose = true)

        val metadataAttribute = span.attributes.find { it.key == "gen_ai.response.metadata" }
        assertNull(metadataAttribute, "gen_ai.response.metadata attribute should not be present when metadata is null")
    }

    @Test
    fun `test response metadata from multiple response messages is merged into single attribute`() {
        val metadata1 = JsonObject(mapOf("request_id" to JsonPrimitive("req-123")))
        val metadata2 = JsonObject(mapOf("served_by" to JsonPrimitive("node-7")))

        val span = createInferenceSpan("merged-metadata")

        val responseMessages = listOf(
            Message.Assistant(
                "response 1",
                ResponseMetaInfo(timestamp = clock.now(), metadata = metadata1)
            ),
            Message.Assistant(
                "response 2",
                ResponseMetaInfo(timestamp = clock.now(), metadata = metadata2)
            ),
        )

        endInferenceSpan(span = span, messages = responseMessages, model = model, verbose = true)

        val metadataAttribute = span.attributes.find { it.key == "gen_ai.response.metadata" }
        assertNotNull(metadataAttribute, "gen_ai.response.metadata attribute should be present")

        val expectedMerged = JsonObject(
            mapOf(
                "request_id" to JsonPrimitive("req-123"),
                "served_by" to JsonPrimitive("node-7"),
            )
        )
        assertEquals(expectedMerged.toString(), metadataAttribute.value)
    }
}
