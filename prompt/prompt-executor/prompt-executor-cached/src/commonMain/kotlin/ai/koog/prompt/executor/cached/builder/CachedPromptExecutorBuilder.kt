package ai.koog.prompt.executor.cached.builder

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Builder for a [PromptExecutor] that caches responses from a [nested] executor.
 *
 * Model resolution is delegated to [nested]; the cache key is computed from prompt + tools only,
 * so model substitutions in [nested] do not affect cache hits.
 *
 * @param cache The cache implementation to use.
 * @param nested The nested executor to use for cache misses.
 */
public class CachedPromptExecutorBuilder(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: KoogClock = KoogClock.System,
) : PromptExecutorBuilder() {

    override fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel =
        nested.resolveModel(model, operation)

    override suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = getOrPut(prompt, tools, model)

    override fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = flow {
        getOrPut(prompt, tools, model).toStreamFrames().forEach { emit(it) }
    }

    override suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult =
        nested.moderate(prompt, model)

    override suspend fun onModels(): List<LLModel> = nested.models()

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator =
        nested.getStandardJsonSchemaGenerator(model)

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator =
        nested.getBasicJsonSchemaGenerator(model)

    override fun onClose() {
        nested.close()
    }

    private suspend fun getOrPut(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel,
    ): Message.Assistant =
        cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) }
}
