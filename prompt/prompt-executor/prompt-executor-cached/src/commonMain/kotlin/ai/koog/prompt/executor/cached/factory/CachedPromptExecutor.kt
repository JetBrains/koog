package ai.koog.prompt.executor.cached.factory

import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.executor.cached.builder.CachedPromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.utils.time.KoogClock

/**
 * Source-compatible factory for [CachedPromptExecutorBuilder]. Returns a built [PromptExecutor].
 */
@Suppress("FunctionName")
public fun CachedPromptExecutor(
    cache: PromptCache,
    nested: PromptExecutor,
    clock: KoogClock = KoogClock.System,
): PromptExecutor = CachedPromptExecutorBuilder(cache, nested, clock).build()
