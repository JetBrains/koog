package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A platform-specific CoroutineDispatcher optimized for executing IO-intensive operations.
 *
 * This dispatcher is suitable for tasks that involve blocking or non-blocking IO, such as reading
 * or writing to files, database access, and network communications. It provides an environment
 * optimized for managing IO-bound workloads, ensuring efficient thread utilization.
 *
 * The actual implementation of this dispatcher may vary depending on the platform, but it is
 * expected to provide a mechanism to handle IO operations without causing unnecessary contention
 * or overloading the system.
 */
public expect val Dispatchers.SuitableForIO: CoroutineDispatcher
