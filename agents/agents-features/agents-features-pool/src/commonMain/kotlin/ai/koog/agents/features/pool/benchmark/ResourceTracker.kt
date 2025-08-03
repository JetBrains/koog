package ai.koog.agents.features.pool.benchmark

import kotlinx.serialization.Serializable

/**
 * Resource usage metrics
 */
@Serializable
public data class ResourceMetrics(
    val peakMemoryMB: Double,
    val avgMemoryMB: Double,
    val gcCollections: Int,
    val cpuUtilizationPercent: Double
)

/**
 * Cross-platform resource tracker with platform-specific implementations
 */
public expect class ResourceTracker() {
    public fun startTracking()
    public fun recordMemorySnapshot()
    public fun getResourceMetrics(): ResourceMetrics
}