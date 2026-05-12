package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryMetadataFilterExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordLeftExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordMetadataValue
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordOperatorType
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordRightExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary

/**
 * Converts AWS Bedrock AgentCore memory record types to the framework's internal representations.
 *
 * Provides utilities to transform [MemoryRecordSummary] objects returned by the Bedrock AgentCore API
 * into [SearchResult] instances wrapping [TextDocument], including score and metadata mapping.
 */
internal object AgentcoreMemoryRecordConverter {

    internal fun memoryRecordSummaryToSearchResult(
        memoryRecordSummary: MemoryRecordSummary,
        strategyTYpe: AgentcoreMemoryStrategy
    ): SearchResult<TextDocument> {
        return SearchResult(
            AgentcoreMemoryRecord(
                memoryRecordSummary.content?.asTextOrNull() ?: "",
                memoryRecordSummary.memoryRecordId,
                mapMetadata(memoryRecordSummary.metadata),
                strategyTYpe
            ),
            Score(memoryRecordSummary.score ?: 0.0, ScoreMetric.COSINE_SIMILARITY)
        )
    }

    private fun mapMetadata(agentcoreMetadata: Map<String, MemoryRecordMetadataValue>?): Map<String, Any> {
        if (agentcoreMetadata.isNullOrEmpty()) return emptyMap()
        return agentcoreMetadata.mapValues { (_, v) -> mapMetadataValue(v) }
    }

    private fun mapMetadataValue(v: MemoryRecordMetadataValue): Any = when (v) {
        is MemoryRecordMetadataValue.DateTimeValue -> v.value
        is MemoryRecordMetadataValue.NumberValue -> v.value
        is MemoryRecordMetadataValue.StringListValue -> v.value
        is MemoryRecordMetadataValue.StringValue -> v.value
        is MemoryRecordMetadataValue.SdkUnknown -> ""
    }

    /**
     * Parses a [filterExpression] string into a list of [MemoryMetadataFilterExpression] suitable
     * for the Bedrock AgentCore `RetrieveMemoryRecords` API.
     *
     * Supported grammar (clauses are separated by commas; whitespace around tokens is ignored):
     * - `key = value`         → [MemoryRecordOperatorType.EqualsTo] with the given metadata key and string value.
     * - `key EXISTS`          → [MemoryRecordOperatorType.Exists] with the given metadata key (no right-hand value).
     * - `key NOT_EXISTS`      → [MemoryRecordOperatorType.NotExists] with the given metadata key (no right-hand value).
     *
     * Operator keywords (`EXISTS`, `NOT_EXISTS`) are matched case-insensitively. A blank or `null`
     * input yields `null`, which signals to the caller that no metadata filtering should be applied.
     *
     * The metadata key must match `[a-zA-Z0-9\s._:/=+@-]{1,128}` and the value (when present) must
     * match `[a-zA-Z0-9\s._:/=+@-]{0,256}`, per the AgentCore API constraints.
     *
     * @throws IllegalArgumentException if a clause cannot be parsed or violates AgentCore constraints.
     */
    internal fun parseFilterExpression(filterExpression: String?): List<MemoryMetadataFilterExpression>? {
        if (filterExpression.isNullOrBlank()) return null

        return filterExpression.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { clause -> parseClause(clause) }
    }

    private val keyPattern = Regex("[a-zA-Z0-9\\s._:/=+@-]{1,128}")
    private val valuePattern = Regex("[a-zA-Z0-9\\s._:/=+@-]{0,256}")

    private fun parseClause(clause: String): MemoryMetadataFilterExpression {
        val eqIndex = clause.indexOf('=')
        if (eqIndex >= 0) {
            val rawKey = clause.substring(0, eqIndex).trim()
            val rawValue = clause.substring(eqIndex + 1).trim()
            require(rawKey.isNotEmpty()) { "Invalid filter expression clause: '$clause' (empty metadata key)" }
            require(keyPattern.matches(rawKey)) {
                "Invalid metadata key '$rawKey' in filter expression clause: '$clause'"
            }
            require(valuePattern.matches(rawValue)) {
                "Invalid metadata value '$rawValue' in filter expression clause: '$clause'"
            }
            return MemoryMetadataFilterExpression {
                left = MemoryRecordLeftExpression.MetadataKey(rawKey)
                operator = MemoryRecordOperatorType.EqualsTo
                right = MemoryRecordRightExpression.MetadataValue(MemoryRecordMetadataValue.StringValue(rawValue))
            }
        }

        val tokens = clause.split(Regex("\\s+"), limit = 2)
        require(tokens.size == 2) {
            "Invalid filter expression clause: '$clause' (expected 'key = value', 'key EXISTS' or 'key NOT_EXISTS')"
        }
        val rawKey = tokens[0].trim()
        val op = tokens[1].trim().uppercase()
        require(rawKey.isNotEmpty()) { "Invalid filter expression clause: '$clause' (empty metadata key)" }
        require(keyPattern.matches(rawKey)) {
            "Invalid metadata key '$rawKey' in filter expression clause: '$clause'"
        }
        val operatorType = when (op) {
            "EXISTS" -> MemoryRecordOperatorType.Exists
            "NOT_EXISTS" -> MemoryRecordOperatorType.NotExists
            else -> throw IllegalArgumentException(
                "Unknown operator '$op' in filter expression clause: '$clause' " +
                    "(supported: '=', 'EXISTS', 'NOT_EXISTS')"
            )
        }
        return MemoryMetadataFilterExpression {
            left = MemoryRecordLeftExpression.MetadataKey(rawKey)
            operator = operatorType
        }
    }
}
