package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorHooks
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * A [PromptExecutor] that caches responses from a nested executor.
 *
 * On a cache hit, the response is returned directly without invoking the nested executor,
 * which means any [PromptExecutorHooks] passed to [execute] or [executeStreaming] are
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
        hooks: PromptExecutorHooks?
    ): List<Message.Response> {
        return getOrPut(prompt, tools, model, hooks)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): Flow<StreamFrame> =
        flow {
            getOrPut(prompt, tools, model, hooks).toStreamFrames().forEach { emit(it) }
        }

    private suspend fun getOrPut(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
        hooks: PromptExecutorHooks?
    ): List<Message.Response> {
        return cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, model, tools, hooks).also { cache.put(prompt, tools, it) }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hooks: PromptExecutorHooks?
    ): ModerationResult = nested.moderate(prompt, model, hooks)

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
