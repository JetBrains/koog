package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ExecutionArgOverrides
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerateHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.ResolvedExecutionIntent
import ai.koog.prompt.executor.model.StreamingHook
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * On a cache hit the hook is not invoked. On a cache miss the full [ExecuteHook] lifecycle
 * ([ExecuteHook.beforeExecution], [ExecuteHook.onCompleted], [ExecuteHook.onFailure]) is
 * delegated to the hook provided to [execute].
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: Clock = Clock.System
) : HookablePromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response> {
        val cached = cache.get(prompt, tools, clock)
        if (cached != null) return cached

        val intent = InitialExecutionIntent(prompt, tools, model)
        val overrides = hook?.beforeExecution(intent, model) ?: ExecutionArgOverrides.NoOverrides
        val resolved = ResolvedExecutionIntent(intent, overrides)
        val effectivePrompt = (overrides as? ExecutionArgOverrides.UseDifferentPrompt)?.prompt ?: prompt

        return try {
            val result = nested.execute(effectivePrompt, model, tools)
            cache.put(effectivePrompt, tools, result)
            hook?.onCompleted(resolved, model, result)
            result
        } catch (e: Throwable) {
            hook?.onFailure(resolved, model, e)
            throw e
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingHook?
    ): Flow<StreamFrame> = flow {
        getOrPut(prompt, tools, model).toStreamFrames().forEach { emit(it) }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: MultipleChoicesHook?
    ): List<LLMChoice> = listOf(execute(prompt, model, tools))

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerateHook?
    ): ModerationResult = nested.moderate(prompt, model)

    private suspend fun getOrPut(prompt: Prompt, model: LLModel): Message.Assistant {
        return cache.get(prompt, emptyList(), clock)
            ?.first() as Message.Assistant?
            ?: nested
                .execute(prompt, model, emptyList()).first()
                .let { it as Message.Assistant }
                .also { cache.put(prompt, emptyList(), listOf(it)) }
    }

    private suspend fun getOrPut(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel): List<Message.Response> {
        return cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) }
    }

    override suspend fun models(): List<LLModel> = nested.models()

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return nested.getStandardJsonSchemaGenerator(model)
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return nested.getBasicJsonSchemaGenerator(model)
    }

    override fun close() {
        nested.close()
    }
}
