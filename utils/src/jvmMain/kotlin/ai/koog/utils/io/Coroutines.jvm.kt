package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A platform-specific property that represents a coroutine dispatcher suitable for IO-intensive
 * tasks, such as file or network operations. It typically maps to `Dispatchers.IO` and provides
 * an optimized thread pool for handling such workloads.
 */
public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
