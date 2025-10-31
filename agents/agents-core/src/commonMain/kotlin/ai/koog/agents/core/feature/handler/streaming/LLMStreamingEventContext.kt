package ai.koog.agents.core.feature.handler.streaming

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame

/**
 * Represents the context for handling streaming-specific events within the framework.
 */
public interface LLMStreamingEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a before-stream event.
 * This context is provided when streaming is about to begin.
 *
 * @property runId The unique identifier for this streaming session.
 * @property callId The unique identifier for the streaming call.
 * @property prompt The prompt that will be sent to the language model for streaming.
 * @property model The language model instance being used for streaming.
 * @property tools The list of tool descriptors available for the streaming call.
 */
public data class LLMStreamingStartingContext(
    val runId: String,
    val callId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingStarting
}

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property runId The unique identifier for this streaming session.
 * @property callId The unique identifier for the streaming call.
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class LLMStreamingFrameReceivedContext(
    val runId: String,
    val callId: String,
    val streamFrame: StreamFrame,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFrameReceived
}

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property runId The unique identifier for this streaming session.
 * @property callId The unique identifier for the streaming call.
 * @property error The exception or error that occurred during streaming.
 */
public data class LLMStreamingFailedContext(
    val runId: String,
    val callId: String,
    val error: Throwable
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFailed
}

/**
 * Represents the context for handling an after-stream event.
 * This context is provided when streaming is complete.
 *
 * @property runId The unique identifier for this streaming session.
 * @property callId The unique identifier for the streaming call.
 * @property prompt The prompt that was sent to the language model for streaming.
 * @property model The language model instance that was used for streaming.
 * @property tools The list of tool descriptors that were available for the streaming call.
 */
public data class LLMStreamingCompletedContext(
    val runId: String,
    val callId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingCompleted
}
