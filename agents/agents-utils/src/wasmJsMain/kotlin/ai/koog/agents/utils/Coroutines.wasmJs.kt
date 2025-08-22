package ai.koog.agents.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A [CoroutineDispatcher] that is suitable for IO operations.
 * It delegates to `Dispatchers.Default`, which is optimized for CPU-intensive tasks
 * but can also be used for IO-bound or mixed workloads when a specific dispatcher
 * for IO is not required or unavailable.
 */
public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.Default
