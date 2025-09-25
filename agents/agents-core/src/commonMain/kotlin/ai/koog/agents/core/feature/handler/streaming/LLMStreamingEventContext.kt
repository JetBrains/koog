package ai.koog.agents.core.feature.handler.streaming

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.prompt.streaming.StreamFrame

/**
 * Represents the context for handling streaming-specific events within the framework.
 */
public interface LLMStreamingEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property runId The unique identifier for this streaming session.
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class LLMStreamingFrameReceivedContext(
    val runId: String,
    val streamFrame: StreamFrame,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFrameReceived
}

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property runId The unique identifier for this streaming session.
 * @property error The exception or error that occurred during streaming.
 */
public data class LLMStreamingFailedContext(
    val runId: String,
    val error: Throwable
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFailed
}
