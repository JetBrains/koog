package ai.koog.agents.core.feature.handler.streaming

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
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
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that will be sent to the language model for streaming;
 * @property model The language model instance being used for streaming;
 * @property tools The list of tool descriptors available for the streaming call.
 */
public data class LLMStreamingStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
    val context: AIAgentContext
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingStarting
}

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class LLMStreamingFrameReceivedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val streamFrame: StreamFrame,
    val context: AIAgentContext
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFrameReceived
}

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property runId The unique identifier for this streaming session;
 * @property error The exception or error that occurred during streaming.
 */
public data class LLMStreamingFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val error: Throwable,
    val context: AIAgentContext
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFailed
}

/**
 * Represents the context for handling an after-stream event.
 * This context is provided when streaming is complete.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property runId The unique identifier for this streaming session;
 * @property prompt The prompt that was sent to the language model for streaming;
 * @property model The language model instance that was used for streaming;
 * @property tools The list of tool descriptors that were available for the streaming call.
 */
public data class LLMStreamingCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
    val context: AIAgentContext
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingCompleted
}
