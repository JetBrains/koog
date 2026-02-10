package ai.koog.agents.core.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A KMP read-write lock implementation that allows concurrent read access but ensures exclusive write access.
 *
 * This implementation uses `kotlinx.coroutines.sync.Mutex` to coordinate access for both readers and writers.
 *
 * ### Key limitations
 * - **Writer starvation**: continuous readers can prevent writers from ever acquiring the lock (see [withReadLock]).
 * - **Non-reentrant write lock**: the same coroutine cannot call [withWriteLock] while already holding it;
 *   doing so will deadlock. Callers that need nested write access (e.g., interceptors running inside a
 *   write session) must use an external mechanism to reuse the active session instead of re-acquiring
 *   the lock (see `AIAgentLLMContextImpl.writeSession` with `reuseActiveSession = true`).
 */
internal class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * CAVEAT: allows writer starvation: new readers can continuously acquire the lock while a writer is waiting.
     * If there's a steady stream of read requests, writers may wait indefinitely.
     */
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

    /**
     * CAVEAT: uses kotlinx.coroutines.sync.Mutex which is not reentrant.
     * When the same coroutine tries to acquire the mutex it already holds, it blocks forever waiting for itself to release it.
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
