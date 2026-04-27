package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.ModerationHook
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StreamingExecutorHook
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock

/**
 * A [PromptExecutor] that caches responses from a nested executor.
 *
 * On a cache hit, the response is returned directly without invoking the nested executor,
 * which means any hooks passed to [execute] or [executeStreaming] are
 * **not** called — hooks only fire on cache misses, where the call is delegated to [nested].
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: Clock = Clock.System
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response> {
        return cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, model, tools, hook).also { cache.put(prompt, tools, it) }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingExecutorHook?
    ): Flow<StreamFrame> =
        flow {
            val cached = cache.get(prompt, tools, clock)
            if (cached != null) {
                cached.toStreamFrames().forEach { emit(it) }
            } else {
                val frames = nested.executeStreaming(prompt, model, tools, hook)
                    .onEach { emit(it) }
                    .toList()
                cache.put(prompt, tools, frames.toMessageResponses())
            }
        }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerationHook?
    ): ModerationResult = nested.moderate(prompt, model, hook)

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
