package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.cache.model.get
import ai.koog.prompt.cache.model.put
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor,
    private val clock: Clock = Clock.System
) : PromptExecutor {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return getOrPut(prompt, tools, model)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        flow {
            getOrPut(prompt, tools, model).forEach {
                emit(it.toStreamFrame())
            }
        }

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

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = nested.moderate(prompt, model)

    override suspend fun models(): List<LLModel> = nested.models()

    override fun close() {
        nested.close()
    }
}
