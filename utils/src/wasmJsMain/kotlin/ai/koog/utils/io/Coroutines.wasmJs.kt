package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A [kotlinx.coroutines.CoroutineDispatcher] that is suitable for IO operations.
 * It delegates to `Dispatchers.Default`, which is optimized for CPU-intensive tasks
 * but can also be used for IO-bound or mixed workloads when a specific dispatcher
 * for IO is not required or unavailable.
 */
public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.Default
