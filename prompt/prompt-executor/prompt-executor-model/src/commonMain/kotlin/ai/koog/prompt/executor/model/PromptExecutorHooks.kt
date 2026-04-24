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
 * 2. [beforeExecution] — before the LLM call; may return [ExecutionArgOverrides] to substitute the prompt.
 * 3. [onFailure] — if the LLM call throws.
 *
 * Subtypes add operation-specific completion callbacks: [SimpleExecutorHook.onCompleted]
 * for non-streaming operations and [StreamingHook.onFrame] /
 * [StreamingHook.onCompleted] for streaming.
 */
public sealed interface ExecutorHook {

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
 * Used for [ExecuteHook], [MultipleChoicesHook], and [ModerateHook].
 */
public interface SimpleExecutorHook<T> : ExecutorHook {

    /**
     * Called after a successful LLM call with the [result] that was produced.
     */
    public suspend fun onCompleted(intent: ResolvedExecutionIntent, effectiveModel: LLModel, result: T) {}
}

/**
 * Hook used for [HookablePromptExecutor.execute]
 */
public interface ExecuteHook : SimpleExecutorHook<List<Message.Response>>

/**
 * Hook used for [HookablePromptExecutor.executeMultipleChoices]
 */
public interface MultipleChoicesHook : SimpleExecutorHook<List<LLMChoice>>

/**
 * Hook used for [HookablePromptExecutor.moderate]
 */
public interface ModerateHook : SimpleExecutorHook<ModerationResult>

/**
 * Hook used for [HookablePromptExecutor.executeStreaming]
 */
public interface StreamingHook : ExecutorHook {

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

    /** Signals that no argument substitution is needed; the original prompt and tools are used as-is. */
    public object NoOverrides : ExecutionArgOverrides

    /** Signals that [prompt] should replace the originally requested prompt for the LLM call. */
    public data class UseDifferentPrompt(val prompt: Prompt) : ExecutionArgOverrides
}

/**
 * Combines this [ExecutionArgOverrides] with [nested] overrides from an inner hook.
 * Nested overrides take precedence: if [nested] is [ExecutionArgOverrides.UseDifferentPrompt], it wins;
 * otherwise this (outer) override is used unchanged.
 */
public fun ExecutionArgOverrides.combineWith(nested: ExecutionArgOverrides): ExecutionArgOverrides = when (nested) {
    is ExecutionArgOverrides.NoOverrides -> this
    is ExecutionArgOverrides.UseDifferentPrompt -> nested
}
