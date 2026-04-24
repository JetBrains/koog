package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.ExecutorHook
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.SimpleExecutorHook
import ai.koog.prompt.executor.model.StreamingHook
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal typealias EffectiveExecutionSubject = Pair<LLMClient, LLModel>
internal val EffectiveExecutionSubject.client get() = first
internal val EffectiveExecutionSubject.model get() = second

internal object PromptExecutorHelper {

    suspend fun <T> executeWithHook(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList(),
        chooseExecutionSubject: suspend (InitialExecutionIntent) -> EffectiveExecutionSubject,
        hook: SimpleExecutorHook<T>?,
        block: suspend (ResolvedExecutionIntent, EffectiveExecutionSubject) -> T
    ): T {
        val (finalIntent, effectiveSubject) = executionSetup(prompt, model, tools, chooseExecutionSubject, hook)
        val result = try {
            block(finalIntent, effectiveSubject)
        } catch (e: Throwable) {
            hook?.onFailure(finalIntent, effectiveSubject.model, e)
            throw e
        }
        hook?.onCompleted(finalIntent, effectiveSubject.model, result)
        return result
    }

    fun streamWithHook(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        chooseExecutionSubject: suspend (InitialExecutionIntent) -> EffectiveExecutionSubject,
        hook: StreamingHook?,
        block: (ResolvedExecutionIntent, EffectiveExecutionSubject) -> Flow<StreamFrame>
    ): Flow<StreamFrame> = flow {
        val (finalIntent, effectiveSubject) = executionSetup(
            prompt,
            model,
            tools,
            chooseExecutionSubject,
            hook
        )
        try {
            block(finalIntent, effectiveSubject).collect { frame ->
                hook?.onFrame(finalIntent, effectiveSubject.model, frame)
                emit(frame)
            }
            hook?.onCompleted(finalIntent, effectiveSubject.model)
        } catch (e: Throwable) {
            hook?.onFailure(finalIntent, effectiveSubject.model, e)
            hook?.onCompleted(finalIntent, effectiveSubject.model)
            throw e
        }
    }

    private suspend fun executionSetup(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        chooseExecutionSubject: suspend (InitialExecutionIntent) -> EffectiveExecutionSubject,
        hook: ExecutorHook?,
    ): Pair<ResolvedExecutionIntent, EffectiveExecutionSubject> {
        val initialIntent = InitialExecutionIntent(prompt, tools, model)
        val effectiveSubject = try {
            chooseExecutionSubject(initialIntent)
        } catch (e: Throwable) {
            hook?.onModelChoiceFailed(initialIntent, e)
            throw e
        }
        val overrides = hook?.beforeExecution(initialIntent, effectiveSubject.model) ?: ExecutionArgOverrides.NoOverrides
        val finalIntent = ResolvedExecutionIntent(initialIntent, overrides)
        return finalIntent to effectiveSubject
    }
}
