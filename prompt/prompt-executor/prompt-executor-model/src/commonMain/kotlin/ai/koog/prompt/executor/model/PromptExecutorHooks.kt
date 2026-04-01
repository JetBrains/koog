package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecutionArgOverrides.NoOverrides
import ai.koog.prompt.executor.model.ExecutionArgOverrides.UseDifferentPrompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Container for lifecycle hook sets covering each executor operation.
 *
 * Pass an instance via the `hooks` parameter on any [PromptExecutorAPI] method.
 * The executor invokes the appropriate sub-hook at each lifecycle point, allowing callers
 * to observe or alter the effective prompt and model without wrapping the executor.
 *
 * By default, all hooks are No-Ops. Overriding each hook is opt-in.
 */
public interface PromptExecutorHooks {

    /** Hooks for [PromptExecutorAPI.execute]. */
    public val execute: SimpleExecutorHook<List<Message.Response>>
        get() = object : SimpleExecutorHook<List<Message.Response>> {}

    /** Hooks for [PromptExecutorAPI.executeMultipleChoices]. */
    public val multipleChoices: SimpleExecutorHook<List<LLMChoice>>
        get() = object : SimpleExecutorHook<List<LLMChoice>> {}

    /** Hooks for [PromptExecutorAPI.moderate]. */
    public val moderation: SimpleExecutorHook<ModerationResult>
        get() = object : SimpleExecutorHook<ModerationResult> {}

    /** Hooks for [PromptExecutorAPI.executeStreaming]. */
    public val streaming: StreamingExecutorHook
        get() = object : StreamingExecutorHook {}
}

/**
 * Base lifecycle callbacks shared by all executor hook types.
 *
 * Hooks are invoked by the executor in the following order:
 * 1. [onModelChoiceFailed] — if the requested model has no matching client and no fallback is available.
 * 2. [beforeExecution] — before the LLM call; may return [ExecutionArgOverrides] to substitute the prompt.
 * 3. [onFailure] — if the LLM call throws.
 *
 * Subtypes add operation-specific completion callbacks: [SimpleExecutorHook.onCompleted]
 * for non-streaming operations and [StreamingExecutorHook.onFrame] /
 * [StreamingExecutorHook.onCompleted] for streaming.
 */
public interface ExecutorHook {

    /**
     * Called when no LLM client can be found for the model requested in [intent]
     * and no fallback is configured. The [error] should be rethrown by the executor immediately after.
     */
    public suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) {}

    /**
     * Called before the LLM call with the [InitialExecutionIntent] and the resolved [effectiveModel].
     *
     * Return [ExecutionArgOverrides.UseDifferentPrompt] to substitute the prompt forwarded to the
     * model; return [ExecutionArgOverrides.NoOverrides] to use the original prompt unchanged.
     * The effective prompt is then accessible via [ResolvedExecutionIntent.prompt].
     */
    public suspend fun beforeExecution(intent: InitialExecutionIntent, effectiveModel: LLModel): ExecutionArgOverrides =
        NoOverrides

    /**
     * Called when the LLM call throws [error].
     * The error is rethrown by the executor after this callback returns.
     */
    public suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) {}
}

/**
 * Extends [ExecutorHook] with a completion callback for operations that return a single result [T].
 *
 * Used for [PromptExecutorHooks.execute], [PromptExecutorHooks.multipleChoices],
 * and [PromptExecutorHooks.moderation].
 */
public interface SimpleExecutorHook<T> : ExecutorHook {

    /**
     * Called after a successful LLM call with the [result] that was produced.
     */
    public suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: T) {}
}

/**
 * Extends [ExecutorHook] with per-frame and completion callbacks for streaming operations.
 *
 * Used for [PromptExecutorHooks.streaming].
 * [onCompleted] is always invoked — even after [onFailure].
 */
public interface StreamingExecutorHook : ExecutorHook {

    /**
     * Called for each [StreamFrame] emitted by the model before the frame is forwarded to the collector.
     */
    public suspend fun onFrame(intent: ResolvedExecutionIntent, effectiveModel: LLModel, frame: StreamFrame) {}

    /**
     * Called when streaming finishes, whether successfully or after a failure.
     */
    public suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel) {}
}

/**
 * Snapshot of the arguments passed to an executor operation.
 *
 * [InitialExecutionIntent] holds the arguments as originally requested by the caller.
 * [ResolvedExecutionIntent] holds the effective arguments after [ExecutorHook.beforeExecution]
 * has had a chance to apply [ExecutionArgOverrides].
 */
public sealed interface ExecutionIntent {
    /** The prompt to be sent to the model. May be substituted in [ResolvedExecutionIntent] by an override. */
    public val prompt: Prompt

    /** The tools made available to the model for this call. */
    public val tools: List<ToolDescriptor>

    /** The originally requested model. */
    public val model: LLModel
}

/**
 * Captures the execution arguments as supplied by the caller before any hook processing.
 * Passed to [ExecutorHook.beforeExecution] so that hooks can inspect and optionally override them.
 */
public class InitialExecutionIntent(
    override val prompt: Prompt,
    override val tools: List<ToolDescriptor> = emptyList(),
    override val model: LLModel
) : ExecutionIntent

/**
 * Captures the effective execution arguments after [ExecutorHook.beforeExecution] has been applied.
 *
 * [prompt] may differ from [InitialExecutionIntent.prompt] if a hook returned
 * [ExecutionArgOverrides.UseDifferentPrompt]. [tools] and [model] always reflect the originally
 * requested values — only the prompt can be overridden through hooks.
 */
public class ResolvedExecutionIntent private constructor(
    override val prompt: Prompt,
    override val tools: List<ToolDescriptor> = emptyList(),
    override val model: LLModel
) : ExecutionIntent {

    public constructor(
        initialExecutionIntent: InitialExecutionIntent,
        executionArgOverrides: ExecutionArgOverrides?
    ) : this(
        prompt = when (executionArgOverrides) {
            null -> initialExecutionIntent.prompt
            NoOverrides -> initialExecutionIntent.prompt
            is UseDifferentPrompt -> executionArgOverrides.prompt
        },
        tools = initialExecutionIntent.tools,
        model = initialExecutionIntent.model
    )
}

/**
 * Returned by [ExecutorHook.beforeExecution] to optionally redirect the arguments used for the LLM call.
 *
 * [NoOverrides] — use the original arguments unchanged.
 * [UseDifferentPrompt] — substitute the prompt while keeping tools and model as-is.
 */
public sealed interface ExecutionArgOverrides {

    /**
     * Merges this override with [nestedOverrides] produced by an inner hook call.
     * The innermost (most specific) override wins.
     */
    public fun combineWith(nestedOverrides: ExecutionArgOverrides): ExecutionArgOverrides

    /** Signals that no argument substitution is needed; the original prompt and tools are used as-is. */
    public object NoOverrides : ExecutionArgOverrides {
        override fun combineWith(nestedOverrides: ExecutionArgOverrides): ExecutionArgOverrides {
            when (nestedOverrides) {
                is NoOverrides -> return this
                is UseDifferentPrompt -> return nestedOverrides
            }
        }
    }

    /** Signals that [prompt] should replace the originally requested prompt for the LLM call. */
    public data class UseDifferentPrompt(val prompt: Prompt) : ExecutionArgOverrides {
        override fun combineWith(nestedOverrides: ExecutionArgOverrides): ExecutionArgOverrides {
            when (nestedOverrides) {
                is NoOverrides -> return this
                is UseDifferentPrompt -> return nestedOverrides
            }
        }
    }
}

/**
 * Shared hook-lifecycle implementations used by some [PromptExecutor] subclasses.
 *
 * Each helper encapsulates the repetitive scaffolding around a single LLM call:
 * resolving [ExecutionArgOverrides] from [ExecutorHook.beforeExecution], constructing
 * [ResolvedExecutionIntent], running the actual call inside a try/catch, and dispatching
 * the appropriate completion or failure callback.
 *
 * Executors call these helpers rather than duplicating the lifecycle sequence themselves,
 * keeping each `execute` / `executeStreaming` override focused on client selection and
 * the LLM call itself.
 */
public object ExecutorHooksHelper {

    /**
     * Runs [block] with the full [SimpleExecutorHook] lifecycle:
     * - resolves overrides via [SimpleExecutorHook.beforeExecution]
     * - builds [ResolvedExecutionIntent],
     * - calls the block, and notifies [SimpleExecutorHook.onCompleted] or [SimpleExecutorHook.onFailure].
     *
     * The [effectiveModel] parameter should be populated by [PromptExecutor] instances that support dynamic model selection
     * (ie [ai.koog.prompt.executor.llms.MultiLLMPromptExecutor.fallback]).
     * If given [PromptExecutor] implementation does not support dynamic model selection, it should use initially provided model - [InitialExecutionIntent.model]
     */
    public suspend fun <T> executeWithHook(
        initialIntent: InitialExecutionIntent,
        effectiveModel: LLModel = initialIntent.model,
        hook: SimpleExecutorHook<T>?,
        block: suspend (ResolvedExecutionIntent) -> T
    ): T {
        val overrides = hook?.beforeExecution(initialIntent, effectiveModel) ?: NoOverrides
        val finalIntent = ResolvedExecutionIntent(initialIntent, overrides)
        val result = try {
            block(finalIntent)
        } catch (e: Throwable) {
            hook?.onFailure(finalIntent, effectiveModel, e)
            throw e
        }
        hook?.onCompleted(finalIntent, effectiveModel, result)
        return result
    }

    /**
     * Runs [block] with the full [StreamingExecutorHook] lifecycle:
     * - resolves overrides via [StreamingExecutorHook.beforeExecution]
     * - builds [ResolvedExecutionIntent],
     * - collects the inner flow while forwarding each frame to [StreamingExecutorHook.onFrame],
     * - always calls [StreamingExecutorHook.onCompleted] on both success and failure.
     *
     * The [effectiveModel] parameter should be populated by [PromptExecutor] instances that support dynamic model selection
     * (ie [ai.koog.prompt.executor.llms.MultiLLMPromptExecutor.fallback]).
     * If given [PromptExecutor] implementation does not support dynamic model selection, it should use initially provided model - [InitialExecutionIntent.model]
     */
    public fun streamingWithHook(
        initialIntent: InitialExecutionIntent,
        effectiveModel: LLModel = initialIntent.model,
        hook: StreamingExecutorHook?,
        block: (ResolvedExecutionIntent) -> Flow<StreamFrame>
    ): Flow<StreamFrame> = flow {
        val overrides = hook?.beforeExecution(initialIntent, effectiveModel) ?: NoOverrides
        val finalIntent = ResolvedExecutionIntent(initialIntent, overrides)
        try {
            block(finalIntent).collect { frame ->
                hook?.onFrame(finalIntent, effectiveModel, frame)
                emit(frame)
            }
            hook?.onCompleted(finalIntent, effectiveModel)
        } catch (e: Throwable) {
            hook?.onFailure(finalIntent, effectiveModel, e)
            hook?.onCompleted(finalIntent, effectiveModel)
            throw e
        }
    }
}
