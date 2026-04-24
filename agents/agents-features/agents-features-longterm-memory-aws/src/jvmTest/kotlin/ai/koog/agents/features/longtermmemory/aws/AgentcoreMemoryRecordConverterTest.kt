package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.storage.search.ScoreMetric
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryContent
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.MetadataValue
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AgentcoreMemoryRecordConverterTest {

    private fun makeMemoryRecordSummary(
        id: String = "record-1",
        strategyId: String = "strategy-1",
        text: String? = "Some memory content",
        score: Double? = 0.9,
        metadata: Map<String, MetadataValue>? = null,
    ): MemoryRecordSummary = MemoryRecordSummary {
        memoryRecordId = id
        memoryStrategyId = strategyId
        content = text?.let { MemoryContent.Text(it) }
        this.score = score
        this.metadata = metadata
        createdAt = Instant.fromEpochSeconds(0)
        namespaces = emptyList()
    }

    @Test
    fun testConvertsTextContentAndScore() {
        val summary = makeMemoryRecordSummary(
            id = "rec-42",
            text = "User prefers dark mode",
            score = 0.95
        )

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        assertIs<AgentcoreMemoryRecord>(result.document)
        val record = result.document as AgentcoreMemoryRecord
        assertEquals("User prefers dark mode", record.content)
        assertEquals("rec-42", record.id)
        assertEquals(0.95, result.score.value)
        assertEquals(ScoreMetric.COSINE_SIMILARITY, result.score.metric)
    }

    @Test
    fun testNullContentBecomesEmptyString() {
        val summary = makeMemoryRecordSummary(text = null, score = 0.5)

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals("", record.content)
    }

    @Test
    fun testNullScoreBecomesZero() {
        val summary = makeMemoryRecordSummary(score = null)

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        assertEquals(0.0, result.score.value)
    }

    @Test
    fun testMetadataStringValuesAreMapped() {
        val summary = makeMemoryRecordSummary(
            metadata = mapOf(
                "key1" to MetadataValue.StringValue("value1"),
                "key2" to MetadataValue.StringValue("value2")
            )
        )

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals("value1", record.metadata["key1"])
        assertEquals("value2", record.metadata["key2"])
    }

    @Test
    fun testNullMetadataBecomesEmptyMap() {
        val summary = makeMemoryRecordSummary(metadata = null)

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals(emptyMap(), record.metadata)
    }

    @Test
    fun testEmptyMetadataBecomesEmptyMap() {
        val summary = makeMemoryRecordSummary(metadata = emptyMap())

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals(emptyMap(), record.metadata)
    }

    @Test
    fun testUnknownMetadataValueBecomesEmptyString() {
        val summary = makeMemoryRecordSummary(
            metadata = mapOf("key" to MetadataValue.SdkUnknown)
        )

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals("", record.metadata["key"])
    }

    @Test
    fun testMemoryRecordIdIsPreserved() {
        val summary = makeMemoryRecordSummary(id = "unique-record-id-999")

        val result =
            AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(summary, AgentcoreMemoryStrategy.SUMMARY)

        val record = result.document as AgentcoreMemoryRecord
        assertEquals("unique-record-id-999", record.id)
    }
}
