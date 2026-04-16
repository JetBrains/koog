package ai.koog.prompt.execution.utils

import ai.koog.prompt.annotations.InternalPromptAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

internal fun ExecutorService?.asCoroutineContext(
    fallbackDispatcher: CoroutineDispatcher = Dispatchers.Default
): CoroutineContext =
    this?.asCoroutineDispatcher() ?: fallbackDispatcher

@InternalPromptAPI
public fun <T> runOnIOBoundDispatcher(
    executorService: ExecutorService? = null,
    block: suspend () -> T
): T =
    runBlocking(
        executorService.asCoroutineContext(
            fallbackDispatcher = Dispatchers.IO
        )
    ) {
        block()
    }
