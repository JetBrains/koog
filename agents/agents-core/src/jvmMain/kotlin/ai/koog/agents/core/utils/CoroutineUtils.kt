@file:Suppress("MissingKDocForPublicAPI")
package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal fun ExecutorService?.asCoroutineContext(
    defaultExecutorService: ExecutorService? = null,
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    (this ?: defaultExecutorService)?.asCoroutineDispatcher() ?: fallbackDispatcher

@InternalAgentsApi
public fun <T> AIAgentConfig.runOnLLMDispatcher(executorService: ExecutorService?, block: suspend () -> T): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = llmRequestExecutorService,
        fallbackDispatcher = Dispatchers.IO
    )
    return runBlockingIfRequired(context, block)
}

@InternalAgentsApi
public fun <T> AIAgentConfig.runOnStrategyDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T {
    val context = executorService.asCoroutineContext(
        defaultExecutorService = strategyExecutorService,
        fallbackDispatcher = Dispatchers.Default
    )
    return runBlockingIfRequired(context, block)
}

@InternalAgentsApi
public val AGENT_CONTEXT_ELEMENT: ThreadLocal<CoroutineContext> = ThreadLocal()

@OptIn(InternalAgentsApi::class)
private fun <T> runBlockingIfRequired(context: CoroutineContext, block: suspend () -> T): T {
    val existingContext = AGENT_CONTEXT_ELEMENT.get()

    if (existingContext != null) {
        val targetDispatcher = context[ContinuationInterceptor] as? CoroutineDispatcher
        val existingDispatcher = existingContext[ContinuationInterceptor] as? CoroutineDispatcher

        if (targetDispatcher == null || targetDispatcher == existingDispatcher) {
            // We are already on the same dispatcher.
            // Using a new runBlocking with EmptyCoroutineContext will block the current thread
            // but won't try to dispatch to the executor again, avoiding deadlock.
            return runBlocking(EmptyCoroutineContext) {
                block()
            }
        }
    }

    return runBlocking(context) {
        val old = AGENT_CONTEXT_ELEMENT.get()
        AGENT_CONTEXT_ELEMENT.set(coroutineContext)
        try {
            block()
        } finally {
            AGENT_CONTEXT_ELEMENT.set(old)
        }
    }
}

@InternalAgentsApi
public suspend fun <T> AIAgentConfig.submitToMainDispatcher(block: () -> T): T {
    val result = CompletableDeferred<T>()

    (strategyExecutorService ?: Dispatchers.Default.asExecutor()).execute {
        result.complete(block())
    }

    return result.await()
}
