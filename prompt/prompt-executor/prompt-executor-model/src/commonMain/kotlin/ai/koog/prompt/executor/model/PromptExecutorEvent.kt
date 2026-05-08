package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame

/**
 * Event emitted by a [PromptExecutor] during prompt, streaming, multiple-choice, and moderation operations.
 *
 * Events with the same [promptExecutionId] belong to the same executor operation. Each event
 * carries the prompt, model, and tools snapshot that is true for its own phase, so these values may differ between
 * requested and dispatched events for the same operation.
 */
public sealed interface PromptExecutorEvent {
    public val promptExecutionId: String
    public val prompt: Prompt
    public val model: LLModel
}

public sealed interface ExecutionEvent : PromptExecutorEvent {
    public val tools: List<ToolDescriptor>
}

//region execute

/**
 * Emitted when [PromptExecutor.execute] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.execute].
 */
public data class ExecutionRequested(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted from [PromptExecutor.execute] when execution is dispatched to the underlying execution mechanism.
 *
 * The prompt, model, and tools describe the effective execution dispatched by [PromptExecutor.execute].
 */
public data class ExecutionDispatched(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted when [PromptExecutor.execute] completes successfully.
 *
 * The prompt, model, and tools describe the execution that produced [responses].
 */
public data class ExecutionCompleted(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val responses: List<Message.Response>,
) : ExecutionEvent

/**
 * Emitted when [PromptExecutor.execute] fails.
 *
 * The prompt, model, and tools describe the execution phase where [error] was raised.
 */
public data class ExecutionFailed(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val error: Throwable,
) : ExecutionEvent

//endregion execute

//region executeMultipleChoices

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.executeMultipleChoices].
 */
public data class MultipleChoicesRequested(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted from [PromptExecutor.executeMultipleChoices] when execution is dispatched to the underlying execution
 * mechanism.
 *
 * The prompt, model, and tools describe the effective execution dispatched by [PromptExecutor.executeMultipleChoices].
 */
public data class MultipleChoicesDispatched(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] completes successfully.
 *
 * The prompt, model, and tools describe the execution that produced [choices].
 */
public data class MultipleChoicesCompleted(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val choices: List<LLMChoice>,
) : ExecutionEvent

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] fails.
 *
 * The prompt, model, and tools describe the execution phase where [error] was raised.
 */
public data class MultipleChoicesFailed(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val error: Throwable,
) : ExecutionEvent

//endregion executeMultipleChoices

//region executeStreaming

/**
 * Emitted when [PromptExecutor.executeStreaming] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.executeStreaming].
 */
public data class StreamingRequested(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted from [PromptExecutor.executeStreaming] when streaming execution is dispatched to the underlying execution
 * mechanism.
 *
 * The prompt, model, and tools describe the effective streaming execution dispatched by
 * [PromptExecutor.executeStreaming].
 */
public data class StreamingDispatched(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted when [PromptExecutor.executeStreaming] receives a streaming [frame].
 *
 * The prompt, model, and tools describe the streaming execution that produced [frame].
 */
public data class StreamingFrameReceived(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val frame: StreamFrame,
) : ExecutionEvent

/**
 * Emitted when the flow returned by [PromptExecutor.executeStreaming] completes successfully.
 *
 * The prompt, model, and tools describe the completed streaming execution.
 */
public data class StreamingCompleted(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEvent

/**
 * Emitted when the flow returned by [PromptExecutor.executeStreaming] fails.
 *
 * The prompt, model, and tools describe the streaming execution phase where [error] was raised.
 */
public data class StreamingFailed(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public val error: Throwable,
) : ExecutionEvent

//endregion executeStreaming

//region moderate

/**
 * Emitted when [PromptExecutor.moderate] receives a request from its caller.
 *
 * The prompt and model describe the request as it was passed to [PromptExecutor.moderate].
 */
public data class ModerationRequested(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
) : PromptExecutorEvent

/**
 * Emitted from [PromptExecutor.moderate] when moderation is dispatched to the underlying execution mechanism.
 *
 * The prompt and model describe the effective moderation request dispatched by [PromptExecutor.moderate].
 */
public data class ModerationDispatched(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
) : PromptExecutorEvent

/**
 * Emitted when [PromptExecutor.moderate] completes successfully.
 *
 * The prompt and model describe the moderation request that produced [result].
 */
public data class ModerationCompleted(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val result: ModerationResult,
) : PromptExecutorEvent

/**
 * Emitted when [PromptExecutor.moderate] fails.
 *
 * The prompt and model describe the moderation phase where [error] was raised.
 */
public data class ModerationFailed(
    override val promptExecutionId: String,
    override val prompt: Prompt,
    override val model: LLModel,
    public val error: Throwable,
) : PromptExecutorEvent

//endregion moderate
