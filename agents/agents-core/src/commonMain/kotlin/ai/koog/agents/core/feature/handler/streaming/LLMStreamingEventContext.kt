package ai.koog.agents.core.feature.handler.streaming

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame

/**
 * Represents the context for handling streaming-specific events within the framework.
 */
public interface LLMStreamingEventContext : AgentLifecycleEventContext {
    /**
     * The agent context associated with the streaming event.
     */
    public val context: AIAgentContext

    /**
     * The unique identifier for this streaming session.
     */
    public val runId: String

    /**
     * The prompt that will be sent to the language model for streaming.
     */
    public val prompt: Prompt

    /**
     * The language model instance being used for streaming.
     */
    public val model: LLModel
}

/**
 * Represents the context for handling a before-stream event.
 * This context is provided when streaming is about to begin.
 *
 * @property tools The list of tool descriptors available for the streaming call.
 */
public data class LLMStreamingStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val tools: List<ToolDescriptor>,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingStarting
}

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class LLMStreamingFrameReceivedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val streamFrame: StreamFrame,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFrameReceived
}

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property error The exception or error that occurred during streaming.
 */
public data class LLMStreamingFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val error: Throwable,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingFailed
}

/**
 * Represents the context for handling an after-stream event.
 * This context is provided when streaming is complete.
 *
 * @property tools The list of tool descriptors that were available for the streaming call.
 */
public data class LLMStreamingCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentContext,
    override val runId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val tools: List<ToolDescriptor>,
) : LLMStreamingEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.LLMStreamingCompleted
}
