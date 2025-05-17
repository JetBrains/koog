package ai.koog.agents.core.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A KMP read-write lock implementation that allows concurrent read access but ensures exclusive write access.
 *
 * This implementation uses `kotlinx.coroutines.sync.Mutex` to coordinate access for both readers and writers.
 */
internal class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    suspend fun <T> withReadLock(block: suspend () -> T): T {
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

    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
