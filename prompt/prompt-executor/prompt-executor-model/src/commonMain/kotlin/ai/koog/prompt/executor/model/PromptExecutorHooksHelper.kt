package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
public object PromptExecutorHooksHelper {

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
        val overrides = hook?.beforeExecution(initialIntent, effectiveModel) ?: ExecutionArgOverrides.NoOverrides
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
        val overrides = hook?.beforeExecution(initialIntent, effectiveModel)
            ?: ExecutionArgOverrides.NoOverrides
        val finalIntent = ResolvedExecutionIntent(initialIntent, overrides)
        try {
            block(finalIntent).collect { frame ->
                hook?.onFrame(finalIntent, effectiveModel, frame)
                emit(frame)
            }
        } catch (e: Throwable) {
            hook?.onFailure(finalIntent, effectiveModel, e)
            throw e
        } finally {
            hook?.onCompleted(finalIntent, effectiveModel)
        }
    }
}
