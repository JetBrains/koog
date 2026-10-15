package ai.koog.agents.lock

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A Kotlin Multiplatform read-write lock that allows concurrent read access while guaranteeing
 * exclusive write access.
 *
 * The implementation uses two [Mutex] instances:
 * - [writeMutex] is held while a writer runs, or while there is at least one active reader.
 *   The first reader acquires it, and the last reader releases it.
 * - [readersCountMutex] protects [readersCount] so that reader acquisition/release is atomic.
 *
 * Concurrency warnings:
 *
 * 1. **Not reentrant.** The underlying [Mutex] has no notion of an owning coroutine, and this
 *    lock does not track owners either. Calling [withWriteLock] from inside another
 *    [withWriteLock], or calling [withWriteLock] from inside [withReadLock] on the same instance,
 *    will deadlock. Nested [withReadLock] calls from the same coroutine do not deadlock but
 *    still serialize briefly on [readersCountMutex] at entry and exit.
 * 2. **Fairness / writer starvation.** A writer suspended on [writeMutex] does not block new
 *    readers from joining an already active read section — new readers only contend for
 *    [writeMutex] on the first-reader transition. Steady overlapping reader traffic can therefore
 *    delay a pending writer.
 * 3. **Cancellation safety.** The reader path uses `try { ... } finally { ... }`, so cancellation
 *    or exceptions inside the block still decrement [readersCount] and release [writeMutex]; the
 *    release runs in a [NonCancellable] context so it cannot be skipped when [readersCountMutex] is
 *    contended. Cancellation while suspended on either mutex at entry is propagated, the lock is not
 *    acquired, and [readersCount] is left unchanged.
 * 4. **Suspension inside critical sections.** The `block` may suspend; keep critical sections
 *    short and avoid launching child coroutines that re-acquire the same lock.
 * 5. **Suspend-only.** There is no blocking or `tryLock` API; this lock is usable only from
 *    coroutine code.
 */
public class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * Run the given [block] of code while holding the read lock. Multiple coroutines may run
     * their read blocks concurrently. See the class-level KDoc for reentrancy and cancellation
     * warnings.
     */
    public suspend fun <T> withReadLock(block: suspend () -> T): T {
        readersCountMutex.withLock {
            // Count the reader only after writeMutex is acquired, so a reader cancelled while
            // waiting for a writer leaves no trace.
            if (readersCount == 0) {
                writeMutex.lock()
            }
            readersCount++
        }

        return try {
            block()
        } finally {
            // Must not be skipped by cancellation, otherwise the read lock leaks forever.
            withContext(NonCancellable) {
                readersCountMutex.withLock {
                    if (--readersCount == 0) {
                        writeMutex.unlock()
                    }
                }
            }
        }
    }

    /**
     * Run the given [block] of code while holding the write lock. Only one coroutine at a time
     * may run under the write lock, and no readers are active while it runs. Must not be called
     * from a coroutine that already holds the read or write lock on this instance — see the
     * class-level KDoc for reentrancy warnings.
     */
    public suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
