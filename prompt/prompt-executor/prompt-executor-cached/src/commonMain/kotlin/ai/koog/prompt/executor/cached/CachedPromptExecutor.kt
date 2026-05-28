package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.executor.model.ResolvedModel
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmSynthetic

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: KoogClock = KoogClock.System
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant {
        val resolvedModel = nested.resolveModel(model, PromptExecutorOperation.Execute)
        return getOrPut(prompt, tools, resolvedModel)
    }

    @JvmSynthetic
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        flow {
            val resolvedModel = nested.resolveModel(model, PromptExecutorOperation.Streaming)
            getOrPut(prompt, tools, resolvedModel).toStreamFrames().forEach { emit(it) }
        }

    private suspend fun getOrPut(prompt: Prompt, model: LLModel): Message.Assistant {
        return cache.get(prompt, emptyList(), clock) as? Message.Assistant? ?: nested
            .execute(prompt, model, emptyList())
            .let { it as Message.Assistant }
            .also { cache.put(prompt, emptyList(), it) }
    }

    private suspend fun getOrPut(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        resolvedModel: ResolvedModel
    ): Message.Assistant {
        return cache.get(prompt, tools, clock)
            ?: nested.execute(prompt, resolvedModel, tools).also { cache.put(prompt, tools, it) }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        val resolvedModel = nested.resolveModel(model, PromptExecutorOperation.Moderate)
        return nested.moderate(prompt, resolvedModel)
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
