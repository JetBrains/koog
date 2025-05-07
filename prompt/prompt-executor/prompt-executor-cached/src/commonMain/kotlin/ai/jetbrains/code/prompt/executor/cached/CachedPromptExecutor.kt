package ai.jetbrains.code.prompt.executor.cached

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.cache.model.CodePromptCache
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
class CachedPromptExecutor(
    private val cache: CodePromptCache,
    private val nested: PromptExecutor
) : PromptExecutor {

    override suspend fun execute(prompt: Prompt): String {
        return getOrPut(prompt).content
    }

    override suspend fun execute(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return getOrPut(prompt, tools)
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow { emit(getOrPut(prompt).content) }

    private suspend fun getOrPut(prompt: Prompt): Message.Assistant {
        return cache.get(prompt)
            ?.first() as Message.Assistant?
            ?: nested
                .execute(prompt)
                .let { Message.Assistant(it) }
                .also { cache.put(prompt, emptyList(), listOf(it)) }
    }

    private suspend fun getOrPut(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        return cache.get(prompt, tools) ?: nested.execute(prompt, tools).also { cache.put(prompt, tools, it) }
    }
}
