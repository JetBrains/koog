package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Represents a CoroutineDispatcher that is suitable for IO-intensive operations.
 * Delegates to the default dispatcher, which is optimized for tasks such as file I/O,
 * network operations, or database queries that do not heavily utilize CPU resources.
 * It is designed to handle a large number of tasks with minimal thread overhead.
 */
public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.Default
