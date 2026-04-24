package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchResult
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.MetadataValue

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
}
