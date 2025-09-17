package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame

/**
 * Represents the context for handling streaming-specific events within the framework.
 */
public interface StreamHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a before-stream event.
 * This context is provided when streaming is about to begin.
 *
 * @property runId The unique identifier for this streaming session.
 * @property prompt The prompt that will be sent to the language model for streaming.
 * @property model The language model instance being used for streaming.
 * @property tools The list of tool descriptors available for the streaming call.
 */
public data class BeforeStreamContext(
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
) : StreamHandlerContext

/**
 * Represents the context for handling individual stream frame events.
 * This context is provided when stream frames are sent out during the streaming process.
 *
 * @property runId The unique identifier for this streaming session.
 * @property streamFrame The individual stream frame containing partial response data from the LLM.
 */
public data class StreamFrameContext(
    val runId: String,
    val streamFrame: StreamFrame,
) : StreamHandlerContext

/**
 * Represents the context for handling an error event during streaming.
 * This context is provided when an error occurs during streaming.
 *
 * @property runId The unique identifier for this streaming session.
 * @property error The exception or error that occurred during streaming.
 */
public data class StreamErrorContext(
    val runId: String,
    val error: Throwable
) : StreamHandlerContext

/**
 * Represents the context for handling an after-stream event.
 * This context is provided when streaming is complete.
 *
 * @property runId The unique identifier for this streaming session.
 * @property prompt The prompt that was sent to the language model for streaming.
 * @property model The language model instance that was used for streaming.
 * @property tools The list of tool descriptors that were available for the streaming call.
 */
public data class AfterStreamContext(
    val runId: String,
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>
) : StreamHandlerContext
