package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.storage.search.ScoreMetric
import aws.sdk.kotlin.services.bedrockagentcore.model.LeftExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryContent
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.MetadataValue
import aws.sdk.kotlin.services.bedrockagentcore.model.OperatorType
import aws.sdk.kotlin.services.bedrockagentcore.model.RightExpression
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun testParseFilterExpressionNullReturnsEmpty() {
        assertEquals(emptyList(), AgentcoreMemoryRecordConverter.parseFilterExpression(null))
    }

    @Test
    fun testParseFilterExpressionBlankReturnsEmpty() {
        assertEquals(emptyList(), AgentcoreMemoryRecordConverter.parseFilterExpression("   "))
    }

    @Test
    fun testParseFilterExpressionEqualsTo() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("topic = sports")

        assertEquals(1, result.size)
        val expr = result[0]
        assertEquals(OperatorType.EqualsTo, expr.operator)
        val left = assertIs<LeftExpression.MetadataKey>(expr.left)
        assertEquals("topic", left.value)
        val right = assertIs<RightExpression.MetadataValue>(expr.right)
        val value = assertIs<MetadataValue.StringValue>(right.value)
        assertEquals("sports", value.value)
    }

    @Test
    fun testParseFilterExpressionExists() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("topic EXISTS")

        assertEquals(1, result.size)
        val expr = result[0]
        assertEquals(OperatorType.Exists, expr.operator)
        val left = assertIs<LeftExpression.MetadataKey>(expr.left)
        assertEquals("topic", left.value)
        assertNull(expr.right)
    }

    @Test
    fun testParseFilterExpressionNotExists() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("topic NOT_EXISTS")

        assertEquals(1, result.size)
        val expr = result[0]
        assertEquals(OperatorType.NotExists, expr.operator)
        val left = assertIs<LeftExpression.MetadataKey>(expr.left)
        assertEquals("topic", left.value)
        assertNull(expr.right)
    }

    @Test
    fun testParseFilterExpressionOperatorKeywordsAreCaseInsensitive() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("a exists, b not_exists")

        assertEquals(2, result.size)
        assertEquals(OperatorType.Exists, result[0].operator)
        assertEquals(OperatorType.NotExists, result[1].operator)
    }

    @Test
    fun testParseFilterExpressionMultipleClauses() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression(
            "topic = sports, author EXISTS, draft NOT_EXISTS"
        )

        assertEquals(3, result.size)
        assertEquals(OperatorType.EqualsTo, result[0].operator)
        assertEquals(OperatorType.Exists, result[1].operator)
        assertEquals(OperatorType.NotExists, result[2].operator)

        assertEquals("topic", (result[0].left as LeftExpression.MetadataKey).value)
        assertEquals("author", (result[1].left as LeftExpression.MetadataKey).value)
        assertEquals("draft", (result[2].left as LeftExpression.MetadataKey).value)
    }

    @Test
    fun testParseFilterExpressionTrimsWhitespaceAndIgnoresEmptyClauses() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("  topic   =   sports  , ,  author EXISTS  ")

        assertEquals(2, result.size)
        assertEquals("topic", (result[0].left as LeftExpression.MetadataKey).value)
        assertEquals(
            "sports",
            ((result[0].right as RightExpression.MetadataValue).value as MetadataValue.StringValue).value
        )
        assertEquals("author", (result[1].left as LeftExpression.MetadataKey).value)
    }

    @Test
    fun testParseFilterExpressionAllowsEmptyValueForEquals() {
        val result = AgentcoreMemoryRecordConverter.parseFilterExpression("topic =")

        assertEquals(1, result.size)
        val value = (result[0].right as RightExpression.MetadataValue).value as MetadataValue.StringValue
        assertEquals("", value.value)
    }

    @Test
    fun testParseFilterExpressionRejectsEmptyKey() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("= value")
        }
    }

    @Test
    fun testParseFilterExpressionRejectsUnknownOperator() {
        val ex = assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("topic FOO")
        }
        assertTrue(ex.message!!.contains("FOO"))
    }

    @Test
    fun testParseFilterExpressionRejectsMalformedClause() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("loneKey")
        }
    }

    @Test
    fun testParseFilterExpressionRejectsInvalidKeyCharacters() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("bad!key = v")
        }
    }

    @Test
    fun testParseFilterExpressionRejectsInvalidValueCharacters() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("k = bad!value")
        }
    }

    @Test
    fun testParseFilterExpressionRejectsTooLongKey() {
        val longKey = "a".repeat(129)
        assertFailsWith<IllegalArgumentException> {
            AgentcoreMemoryRecordConverter.parseFilterExpression("$longKey = v")
        }
    }
}
