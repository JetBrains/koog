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
 * Events with the same [promptExecutionId] belong to the same executor operation. Each event carries the prompt,
 * model, and tools snapshot that is true for its own phase, so these values may differ between requested and
 * submitted events for the same operation.
 */
public sealed interface PromptExecutorEvent {
    public val context: PromptExecutionContext
}

public sealed class ExecutionEventBase(
    override val context: PromptExecutionContext,
    public open val prompt: Prompt,
    public open val model: LLModel,
    public open val tools: List<ToolDescriptor>,
) : PromptExecutorEvent

public sealed class ExecutionCompletedBase<T>(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public open val result: T,
) : ExecutionEventBase(context, prompt, model, tools)

public sealed class ExecutionFailedBase(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    public open val error: Throwable,
) : ExecutionEventBase(context, prompt, model, tools)

//region execute

/**
 * Emitted when [PromptExecutor.execute] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.execute].
 */
public data class ExecutionRequested(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEventBase(context, prompt, model, tools)

/**
 * Emitted from [PromptExecutor.execute] when execution is submitted to the underlying execution mechanism.
 *
 * The prompt, model, and tools describe the effective execution submitted by [PromptExecutor.execute].
 */
public data class ExecutionSubmitted(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEventBase(context, prompt, model, tools)

/**
 * Emitted when [PromptExecutor.execute] completes successfully.
 *
 * The prompt, model, and tools describe the execution that produced [responses].
 */
public data class ExecutionCompleted(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val result: List<Message.Response>
) : ExecutionCompletedBase<List<Message.Response>>(context, prompt, model, tools, result)

/**
 * Emitted when [PromptExecutor.execute] fails.
 *
 * The prompt, model, and tools describe the execution phase where [error] was raised.
 */
public data class ExecutionFailed(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val error: Throwable,
) : ExecutionFailedBase(context, prompt, model, tools, error)

//endregion execute

//region executeMultipleChoices

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.executeMultipleChoices].
 */
public data class MultipleChoicesRequested(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEventBase(context, prompt, model, tools)

/**
 * Emitted from [PromptExecutor.executeMultipleChoices] when execution is submitted to the underlying execution
 * mechanism.
 *
 * The prompt, model, and tools describe the effective execution submitted by [PromptExecutor.executeMultipleChoices].
 */
public data class MultipleChoicesSubmitted(
    override val context: PromptExecutionContext,
    override val prompt: Prompt,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
) : ExecutionEventBase(context, prompt, model, tools)

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] completes successfully.
 *
 * The prompt, model, and tools describe the execution that produced [result].
 */
public data class MultipleChoicesCompleted(
    override val prompt: Prompt,
    override val context: PromptExecutionContext,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val result: List<LLMChoice>,
) : ExecutionCompletedBase<List<LLMChoice>>(context, prompt, model, tools, result)

/**
 * Emitted when [PromptExecutor.executeMultipleChoices] fails.
 *
 * The prompt, model, and tools describe the execution phase where [error] was raised.
 */
public data class MultipleChoicesFailed(
    override val prompt: Prompt,
    override val context: PromptExecutionContext,
    override val model: LLModel,
    override val tools: List<ToolDescriptor>,
    override val error: Throwable,
) : ExecutionFailedBase(context, prompt, model, tools, error)

//endregion executeMultipleChoices

//region executeStreaming

/**
 * Emitted when [PromptExecutor.executeStreaming] receives a request from its caller.
 *
 * The prompt, model, and tools describe the request as it was passed to [PromptExecutor.executeStreaming].
 */
public data class StreamingRequested(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val tools: List<ToolDescriptor>,
) : PromptExecutorEvent

/**
 * Emitted from [PromptExecutor.executeStreaming] when streaming execution is submitted to the underlying execution
 * mechanism.
 *
 * The prompt, model, and tools describe the effective streaming execution submitted by
 * [PromptExecutor.executeStreaming].
 */
public data class StreamingSubmitted(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val tools: List<ToolDescriptor>,
) : PromptExecutorEvent

/**
 * Emitted when [PromptExecutor.executeStreaming] receives a streaming [frame].
 *
 * The prompt, model, and tools describe the streaming execution that produced [frame].
 */
public data class StreamingFrameReceived(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val tools: List<ToolDescriptor>,
    public val frame: StreamFrame,
) : PromptExecutorEvent

/**
 * Emitted when the flow returned by [PromptExecutor.executeStreaming] completes successfully.
 *
 * The prompt, model, and tools describe the completed streaming execution.
 */
public data class StreamingCompleted(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val tools: List<ToolDescriptor>,
) : PromptExecutorEvent

/**
 * Emitted when the flow returned by [PromptExecutor.executeStreaming] fails.
 *
 * The prompt, model, and tools describe the streaming execution phase where [error] was raised.
 */
public data class StreamingFailed(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val tools: List<ToolDescriptor>,
    public val error: Throwable,
) : PromptExecutorEvent

//endregion executeStreaming

//region moderate

/**
 * Emitted when [PromptExecutor.moderate] receives a request from its caller.
 *
 * The prompt and model describe the request as it was passed to [PromptExecutor.moderate].
 */
public data class ModerationRequested(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
) : PromptExecutorEvent

/**
 * Emitted from [PromptExecutor.moderate] when moderation is submitted to the underlying execution mechanism.
 *
 * The prompt and model describe the effective moderation request submitted by [PromptExecutor.moderate].
 */
public data class ModerationSubmitted(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
) : PromptExecutorEvent

/**
 * Emitted when [PromptExecutor.moderate] completes successfully.
 *
 * The prompt and model describe the moderation request that produced [result].
 */
public data class ModerationCompleted(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val result: ModerationResult,
) : PromptExecutorEvent

/**
 * Emitted when [PromptExecutor.moderate] fails.
 *
 * The prompt and model describe the moderation phase where [error] was raised.
 */
public data class ModerationFailed(
    override val context: PromptExecutionContext,
    public val prompt: Prompt,
    public val model: LLModel,
    public val error: Throwable,
) : PromptExecutorEvent

//endregion moderate
