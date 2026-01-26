package ai.koog.agents.features.opentelemetry.metric

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

internal interface MetricEvent {
    val id: String
    val timestamp: Long
}

internal data class LLMCallStarted(
    override val id: String,
    override val timestamp: Long,
    val model: LLModel,
    val modelProvider: LLMProvider
) : MetricEvent


internal data class LLMCallEnded(
    override val id: String,
    override val timestamp: Long,
    val model: LLModel,
    val modelProvider: LLMProvider,
    val inputTokenSpend: Long?,
    val outputTokenSpend: Long?
) : MetricEvent


internal data class ToolCallStarted(
    override val id: String,
    override val timestamp: Long,
    val toolName: String
) : MetricEvent


internal enum class ToolCallStatus {
    SUCCESS,
    FAILED,
    VALIDATION_FAILED,
}

internal data class ToolCallEnded(
    override val id: String,
    override val timestamp: Long,
    val toolName: String,
    val status: ToolCallStatus,
) : MetricEvent
