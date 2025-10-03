package ai.koog.a2a.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A keyed, suspend-friendly mutex for Kotlin Multiplatform.
 *
 * Guarantees mutual exclusion per key, without blocking threads.
 * API mirrors kotlinx.coroutines Mutex:
 * - lock(key, owner)
 * - tryLock(key, owner)
 * - unlock(key, owner)
 * - withLock(key, owner) { ... }
 *
 * Internals:
 * - A per-key entry holds a Mutex and a reference count (holders/waiters).
 * - We increment refs before suspending for lock(key) so entries aren’t removed while waiting.
 * - Cleanup removes the entry only when refs == 0 and the mutex is not locked.
 */
public class KeyedMutex<K> {
    private class Entry(
        val mutex: Mutex = Mutex(),
        var refs: Int = 0
    )

    /**
     * Protects access to [entries] and [Entry.refs] updates.
     */
    private val mapMutex = Mutex()
    private val entries = mutableMapOf<K, Entry>()

    /**
     * Suspends until the lock for [key] is acquired.
     * Not re-entrant for the same key within the same coroutine.
     */
    public suspend fun lock(key: K, owner: Any? = null) {
        val entry = mapMutex.withLock {
            val e = entries.getOrPut(key) { Entry() }
            e.refs += 1
            e
        }

        try {
            entry.mutex.lock(owner)
        } catch (t: Throwable) {
            // If lock failed/cancelled before acquiring, roll back the ref and maybe cleanup.
            mapMutex.withLock {
                entry.refs -= 1
                if (entry.refs == 0 && !entry.mutex.isLocked && entries[key] == entry) {
                    entries.remove(key)
                }
            }
            throw t
        }
    }

    /**
     * Attempts to acquire the lock for [key] without suspension.
     * Returns true if lock was acquired.
     */
    public suspend fun tryLock(key: K, owner: Any? = null): Boolean {
        return mapMutex.withLock {
            val existing = entries[key]
            if (existing != null) {
                if (existing.mutex.tryLock(owner)) {
                    existing.refs += 1
                    true
                } else {
                    false
                }
            } else {
                // Avoid inserting if we cannot lock; create a fresh entry and try immediately.
                val e = Entry()
                val locked = e.mutex.tryLock(owner)
                if (locked) {
                    e.refs = 1
                    entries[key] = e
                    true
                } else {
                    // Unlikely with a fresh Mutex, but don't insert on failure.
                    false
                }
            }
        }
    }

    /**
     * Releases the lock for [key].
     * Must be called exactly once per successful lock/tryLock.
     * @throws IllegalStateException on misuse (mirrors Mutex behavior).
     */
    public suspend fun unlock(key: K, owner: Any? = null) {
        val entry = mapMutex.withLock {
            entries[key] ?: throw IllegalStateException("Unlock requested for key without active entry")
        }

        // Perform the actual unlock; may throw if not locked or wrong owner.
        entry.mutex.unlock(owner)

        // Decrement refs and cleanup if safe.
        mapMutex.withLock {
            entry.refs -= 1
            if (entry.refs == 0 && !entry.mutex.isLocked && entries[key] == entry) {
                entries.remove(key)
            }
        }
    }

    /**
     * Optional: observe whether a key appears locked.
     * For diagnostics/metrics only (not a synchronization primitive).
     */
    public suspend fun isLocked(key: K): Boolean =
        mapMutex.withLock { entries[key]?.mutex?.isLocked == true }

    /**
     * Checks whether this key is locked by the specified owner.
     */
    public suspend fun holdsLock(key: K, owner: Any): Boolean =
        mapMutex.withLock { entries[key]?.mutex?.holdsLock(owner) == true }
}

/**
 * Convenience function mirroring [Mutex.withLock]
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun <K, T> KeyedMutex<K>.withLock(
    key: K,
    owner: Any? = null,
    action: suspend () -> T
): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    lock(key, owner)
    try {
        return action()
    } finally {
        unlock(key, owner)
    }
}
