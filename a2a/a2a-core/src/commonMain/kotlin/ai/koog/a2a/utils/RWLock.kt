package ai.koog.a2a.utils

import ai.koog.a2a.annotations.InternalA2AApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// FIXME copied from agents-core module, because a2a does not depend on other Koog modules.
//  Do we want to make a global utils module for cases like this?
/**
 * A KMP read-write lock implementation that allows concurrent read access but ensures exclusive write access.
 *
 * This implementation uses [Mutex] to coordinate access for both readers and writers.
 */
@InternalA2AApi
public class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * Run the given [block] of code while holding the read lock.
     */
    public suspend fun <T> withReadLock(block: suspend () -> T): T {
        readersCountMutex.withLock {
            if (++readersCount == 1) {
                writeMutex.lock()
            }
        }

        return try {
            block()
        } finally {
            readersCountMutex.withLock {
                if (--readersCount == 0) {
                    writeMutex.unlock()
                }
            }
        }
    }

    /**
     * Run the given [block] of code while holding the write lock.
     */
    public suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
