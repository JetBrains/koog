@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

internal fun ExecutorService?.asCoroutineContext(
    defaultExecutorService: ExecutorService? = null,
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    (this ?: defaultExecutorService)?.asCoroutineDispatcher() ?: fallbackDispatcher

@InternalAgentsApi
public fun <T> AIAgentConfig.runOnLLMDispatcher(executorService: ExecutorService?, block: suspend () -> T): T =
    runBlocking(
        executorService.asCoroutineContext(
            defaultExecutorService = llmRequestExecutorService,
            fallbackDispatcher = Dispatchers.IO
        )
    ) {
        block()
    }

@InternalAgentsApi
public fun <T> AIAgentConfig.runOnStrategyDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T =
    runBlocking(
        executorService.asCoroutineContext(
            defaultExecutorService = strategyExecutorService,
            fallbackDispatcher = Dispatchers.Default
        )
    ) {
        block()
    }

@InternalAgentsApi
public suspend fun <T> AIAgentConfig.submitToMainDispatcher(block: () -> T): T {
    val result = CompletableDeferred<T>()

    (strategyExecutorService ?: Dispatchers.Default.asExecutor()).execute {
        result.complete(block())
    }

    return result.await()
}
