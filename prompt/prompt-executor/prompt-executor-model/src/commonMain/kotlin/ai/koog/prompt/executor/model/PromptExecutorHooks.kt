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

/**
 * Base lifecycle callbacks shared by all executor hook types.
 *
 * Hooks are invoked by the executor in the following order:
 * 1. [onModelChoiceFailed] — if the requested model has no matching client and no fallback is available.
 * 2. [beforeExecution] — right before the LLM call; may return [ExecutionArgOverrides] to substitute the final arguments.
 * 3. [onFailure] — if the LLM call throws.
 *
 * Subtypes add operation-specific completion callbacks: [SimpleExecutorHook.onCompleted]
 * for non-streaming operations and [StreamingExecutorHook.onFrame] /
 * [StreamingExecutorHook.onCompleted] for streaming.
 */
public interface ExecutorHook {

    /**
     * Called when no LLM client can be found for the model requested in [intent]
     * and no fallback is configured. The [error] will be rethrown by the executor immediately after.
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
 * Used for [PromptExecutorAPI.execute], [PromptExecutorAPI.executeMultipleChoices],
 * and [PromptExecutorAPI.moderate].
 */
public interface SimpleExecutorHook<T> : ExecutorHook {

    /**
     * Called after a successful LLM call with the [result] that was produced.
     */
    public suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: T) {}
}

/**
 * Adaptor that transforms the result type [U] into [T] for [SimpleExecutorHook.onCompleted].
 * Useful when dealing with hooks implementing the same logic but returning different types.
 */
public fun <T, U> SimpleExecutorHook<T>.adaptResult(transform: (U) -> T): SimpleExecutorHook<U> =
    object : SimpleExecutorHook<U> {
        override suspend fun onModelChoiceFailed(intent: InitialExecutionIntent, error: Throwable) =
            this@adaptResult.onModelChoiceFailed(intent, error)

        override suspend fun beforeExecution(
            intent: InitialExecutionIntent,
            effectiveModel: LLModel
        ): ExecutionArgOverrides = this@adaptResult.beforeExecution(intent, effectiveModel)

        override suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: U) =
            this@adaptResult.onCompleted(intent, effectiveModel, transform(result))

        override suspend fun onFailure(intent: ResolvedExecutionIntent, effectiveModel: LLModel, error: Throwable) =
            this@adaptResult.onFailure(intent, effectiveModel, error)
    }

/**
 * Extends [ExecutorHook] with per-frame and completion callbacks for streaming operations.
 *
 * Used for [PromptExecutorAPI.executeStreaming].
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

/** Type alias for the hook parameter of [PromptExecutorAPI.execute]. */
public typealias ExecuteHook = SimpleExecutorHook<List<Message.Response>>

/** Type alias for the hook parameter of [PromptExecutorAPI.executeMultipleChoices]. */
public typealias MultipleChoicesHook = SimpleExecutorHook<List<LLMChoice>>

/** Type alias for the hook parameter of [PromptExecutorAPI.moderate]. */
public typealias ModerationHook = SimpleExecutorHook<ModerationResult>

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
 *
 * Note: the [model] might not represent the final model selected by the executor if it supports dynamic model selection.
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
