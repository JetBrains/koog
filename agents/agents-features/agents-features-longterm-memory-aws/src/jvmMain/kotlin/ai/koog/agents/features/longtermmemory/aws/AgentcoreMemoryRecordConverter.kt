package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import aws.sdk.kotlin.services.bedrockagentcore.model.LeftExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryMetadataFilterExpression
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.MetadataValue
import aws.sdk.kotlin.services.bedrockagentcore.model.OperatorType
import aws.sdk.kotlin.services.bedrockagentcore.model.RightExpression

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

    private fun mapMetadata(agentcoreMetadata: Map<String, MetadataValue>?): Map<String, Any> {
        if (agentcoreMetadata.isNullOrEmpty()) return emptyMap()
        return agentcoreMetadata.mapValues { (_, v) -> v.asStringValueOrNull() ?: "" }
    }

    /**
     * Parses a [filterExpression] string into a list of [MemoryMetadataFilterExpression] suitable
     * for the Bedrock AgentCore `RetrieveMemoryRecords` API.
     *
     * Supported grammar (clauses are separated by commas; whitespace around tokens is ignored):
     * - `key = value`         → [OperatorType.EqualsTo] with the given metadata key and string value.
     * - `key EXISTS`          → [OperatorType.Exists] with the given metadata key (no right-hand value).
     * - `key NOT_EXISTS`      → [OperatorType.NotExists] with the given metadata key (no right-hand value).
     *
     * Operator keywords (`EXISTS`, `NOT_EXISTS`) are matched case-insensitively. A blank or `null`
     * input yields an empty list.
     *
     * The metadata key must match `[a-zA-Z0-9\s._:/=+@-]{1,128}` and the value (when present) must
     * match `[a-zA-Z0-9\s._:/=+@-]{0,256}`, per the AgentCore API constraints.
     *
     * @throws IllegalArgumentException if a clause cannot be parsed or violates AgentCore constraints.
     */
    internal fun parseFilterExpression(filterExpression: String?): List<MemoryMetadataFilterExpression> {
        if (filterExpression.isNullOrBlank()) return emptyList()

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
                left = LeftExpression.MetadataKey(rawKey)
                operator = OperatorType.EqualsTo
                right = RightExpression.MetadataValue(MetadataValue.StringValue(rawValue))
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
            "EXISTS" -> OperatorType.Exists
            "NOT_EXISTS" -> OperatorType.NotExists
            else -> throw IllegalArgumentException(
                "Unknown operator '$op' in filter expression clause: '$clause' " +
                    "(supported: '=', 'EXISTS', 'NOT_EXISTS')"
            )
        }
        return MemoryMetadataFilterExpression {
            left = LeftExpression.MetadataKey(rawKey)
            operator = operatorType
        }
    }
}
