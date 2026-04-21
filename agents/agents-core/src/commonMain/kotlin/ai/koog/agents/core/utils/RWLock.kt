package ai.koog.agents.core.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A Kotlin Multiplatform read-write lock that allows concurrent read access while guaranteeing
 * exclusive write access.
 *
 * The implementation is based on two [kotlinx.coroutines.sync.Mutex] instances:
 * - [writeMutex] is held while any writer runs, or while there is at least one active reader.
 *   The first reader acquires it and the last reader releases it (a classic "readers hold the
 *   write mutex" scheme).
 * - [readersCountMutex] protects [readersCount] so that reader acquisition/release is atomic.
 *
 * Concurrency caveats (applies equally to all call sites):
 *
 * 1. **Not reentrant.** Coroutines do not have thread/owner identity associated with a
 *    [kotlinx.coroutines.sync.Mutex], and this lock does not track owners either. Therefore:
 *    - Calling [withWriteLock] from inside another [withWriteLock] on the same instance will
 *      deadlock.
 *    - Calling [withWriteLock] from inside [withReadLock] will deadlock (the write mutex is
 *      already held by the outer read section).
 *    - Nested [withReadLock] calls from the same coroutine do **not** deadlock (they only bump
 *      [readersCount]), but they still serialize briefly on [readersCountMutex] at entry/exit.
 *    Do not rely on reentrancy in any of these cases.
 *
 * 2. **Fairness / writer starvation.** Fairness follows [kotlinx.coroutines.sync.Mutex]'s FIFO
 *    policy. A writer waits until all current readers release the lock, but because readers
 *    acquire the write mutex only on the first-reader transition, a steady stream of overlapping
 *    readers can briefly extend the critical section. A waiting writer on [writeMutex] does not
 *    block new readers from incrementing [readersCount] — new readers that arrive while another
 *    reader is already active will proceed without contending for [writeMutex] at all, which can
 *    delay a pending writer.
 *
 * 3. **Cancellation safety.** The `block` runs inside a `try { ... } finally { ... }` pair, so a
 *    cancellation or exception thrown from within the block will correctly decrement
 *    [readersCount] and release [writeMutex] when appropriate. However, cancellation occurring
 *    while suspended on [readersCountMutex] or [writeMutex] is propagated by the underlying
 *    mutex and the lock is not acquired — callers must be prepared for [kotlinx.coroutines.CancellationException].
 *
 * 4. **Suspension inside critical sections.** Because `block` is `suspend`, the lock may be held
 *    across arbitrary suspension points. Keep critical sections short and avoid launching
 *    long-running child coroutines that depend on re-acquiring the same lock.
 *
 * 5. **Not safe for non-suspending / blocking use.** This lock is only usable from suspending
 *    code; there is no `tryLock` or blocking API.
 */
internal class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * Executes [block] while holding the read lock. Multiple coroutines may execute their
     * [block]s concurrently. See the class-level documentation for reentrancy and cancellation
     * caveats.
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
     * Executes [block] while holding the write lock. Only one coroutine at a time may execute
     * its [block] under the write lock, and no readers are active while it runs. See the
     * class-level documentation for reentrancy and cancellation caveats — in particular, this
     * method must **not** be called from a coroutine that already holds either the read or the
     * write lock on the same [RWLock] instance, as doing so will deadlock.
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
