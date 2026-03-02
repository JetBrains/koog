package ai.koog.rag.base.storage.capability

import ai.koog.rag.base.storage.ScoreMetric

/**
 * Optional details for capability behavior.
 */
public data class CapabilityDetails(
    val maxTopK: Int? = null,
    val supportedMetrics: Set<ScoreMetric> = emptySet(),
    val defaultMetric: ScoreMetric? = null,
)
