package ai.jetbrains.code.prompt.executor.cached

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.cache.model.PromptCache
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor
) : PromptExecutor {

    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        return getOrPut(prompt, model).content
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return getOrPut(prompt, tools, model)
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> =
        flow { emit(getOrPut(prompt, model).content) }

    private suspend fun getOrPut(prompt: Prompt, model: LLModel): Message.Assistant {
        return cache.get(prompt)
            ?.first() as Message.Assistant?
            ?: nested
                .execute(prompt, model)
                .let { Message.Assistant(it) }
                .also { cache.put(prompt, emptyList(), listOf(it)) }
    }

    private suspend fun getOrPut(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel): List<Message.Response> {
        return cache.get(prompt, tools) ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) }
    }
}
