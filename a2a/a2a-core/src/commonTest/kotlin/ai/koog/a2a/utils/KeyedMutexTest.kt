package ai.koog.a2a.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyedMutexTest {

    @Test
    fun basicLockUnlock() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        assertFalse(mutex.isLocked(key), "Key should not be locked initially")

        mutex.lock(key)
        assertTrue(mutex.isLocked(key), "Key should be locked after lock()")

        mutex.unlock(key)
        assertFalse(mutex.isLocked(key), "Key should be unlocked after unlock()")
    }

    @Test
    fun basicTryLock() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        assertTrue(mutex.tryLock(key), "tryLock should succeed on unlocked key")
        assertTrue(mutex.isLocked(key), "Key should be locked after tryLock")
        assertFalse(mutex.tryLock(key), "tryLock should fail on locked key")

        mutex.unlock(key)
        assertFalse(mutex.isLocked(key), "Key should be unlocked after unlock")
    }

    @Test
    fun withLockConvenienceFunction() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        var executed = false

        val result = mutex.withLock(key) {
            assertTrue(mutex.isLocked(key), "Key should be locked inside withLock block")
            executed = true
            "result"
        }

        assertEquals("result", result, "withLock should return block result")
        assertTrue(executed, "Block should have been executed")
        assertFalse(mutex.isLocked(key), "Key should be unlocked after withLock")
    }

    @Test
    fun differentKeysCanBeLocked() = runTest {
        val mutex = KeyedMutex<String>()
        val key1 = "key1"
        val key2 = "key2"

        mutex.lock(key1)
        mutex.lock(key2)

        assertTrue(mutex.isLocked(key1), "Key1 should be locked")
        assertTrue(mutex.isLocked(key2), "Key2 should be locked")

        mutex.unlock(key1)
        assertFalse(mutex.isLocked(key1), "Key1 should be unlocked")
        assertTrue(mutex.isLocked(key2), "Key2 should still be locked")

        mutex.unlock(key2)
        assertFalse(mutex.isLocked(key2), "Key2 should be unlocked")
    }

    @Test
    fun concurrentAccessSameKey() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val results = mutableListOf<Int>()
        val accessOrder = mutableListOf<Int>()

        val jobs = (1..3).map { i ->
            this.async {
                mutex.withLock(key) {
                    accessOrder.add(i)
                    delay(10) // Simulate some work
                    results.add(i)
                }
            }
        }

        jobs.awaitAll()

        assertEquals(3, results.size, "All coroutines should complete")
        assertEquals(accessOrder, results, "Access order should match completion order due to mutex")
        assertFalse(mutex.isLocked(key), "Key should be unlocked after all operations")
    }

    @Test
    fun parallelAccessDifferentKeys() = runTest {
        val mutex = KeyedMutex<String>()
        val results = mutableListOf<String>()
        val completionOrder = mutableListOf<String>()

        // Use different keys with different work durations to verify parallelism
        val keyWorkMap = mapOf(
            "fast" to 10L,
            "medium" to 15L,
            "slow" to 25L
        )

        val jobs = keyWorkMap.map { (key, workDuration) ->
            this.async {
                mutex.withLock(key) {
                    results.add("$key-started")
                    delay(workDuration)
                    results.add("$key-finished")
                    completionOrder.add(key)
                }
            }
        }

        jobs.awaitAll()

        assertEquals(6, results.size, "All operations should complete")
        assertEquals(3, completionOrder.size, "All keys should complete")

        // If running in parallel, fast operations should finish before slow ones
        // even if they started later. Check that "fast" completes before "slow"
        val fastIndex = completionOrder.indexOf("fast")
        val slowIndex = completionOrder.indexOf("slow")
        assertTrue(
            fastIndex < slowIndex,
            "Fast operation should complete before slow operation if running in parallel. " +
                "Completion order: $completionOrder"
        )
    }

    @Test
    fun unlockWithoutLockThrowsException() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        assertFailsWith<IllegalStateException>("Should throw when unlocking non-existent key") {
            mutex.unlock(key)
        }
    }

    @Test
    fun unlockAfterAlreadyUnlockedThrowsException() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        mutex.lock(key)
        mutex.unlock(key)

        assertFailsWith<IllegalStateException>("Should throw when unlocking already unlocked key") {
            mutex.unlock(key)
        }
    }

    @Test
    fun ownerParameterEnforcement() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val owner1 = "owner1"
        val owner2 = "owner2"

        mutex.lock(key, owner1)

        assertFailsWith<IllegalStateException>("Should throw when unlocking with wrong owner") {
            mutex.unlock(key, owner2)
        }

        // Should succeed with correct owner
        mutex.unlock(key, owner1)
        assertFalse(mutex.isLocked(key), "Key should be unlocked")
    }

    @Test
    fun tryLockWithOwnerParameterEnforcement() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val owner1 = "owner1"
        val owner2 = "owner2"

        assertTrue(mutex.tryLock(key, owner1), "First tryLock should succeed")
        assertFalse(mutex.tryLock(key, owner2), "Second tryLock with different owner should fail")

        mutex.unlock(key, owner1)
        assertTrue(mutex.tryLock(key, owner2), "tryLock should succeed after unlock")
        mutex.unlock(key, owner2)
    }

    @Test
    fun exceptionDuringLockRollsBackRefCount() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        // First, acquire the lock to create an entry
        mutex.lock(key)
        assertTrue(mutex.isLocked(key), "Key should be locked")

        // Start another coroutine that will try to lock and fail
        val job = this.async {
            try {
                // This should wait since the key is already locked
                mutex.lock(key, "owner")
                // If we somehow get here, unlock to clean up
                mutex.unlock(key, "owner")
            } catch (e: Exception) {
                // Expected to be cancelled
            }
        }

        yield() // Let the other coroutine start waiting

        // Cancel the waiting coroutine to simulate an exception during lock
        job.cancel()

        // Unlock the original lock
        mutex.unlock(key)

        // The entry should be cleaned up properly despite the cancelled waiter
        assertFalse(mutex.isLocked(key), "Key should be unlocked and cleaned up")

        // Should be able to lock again normally
        assertTrue(mutex.tryLock(key), "Should be able to lock after cleanup")
        mutex.unlock(key)
    }

    @Test
    fun memoryLeakPrevention() = runTest {
        val mutex = KeyedMutex<String>()
        val keys = (1..100).map { "key$it" }

        // Lock and unlock many keys
        keys.forEach { key ->
            mutex.withLock(key) {
                // Do nothing, just acquire and release
            }
        }

        // All keys should be unlocked and entries cleaned up
        keys.forEach { key ->
            assertFalse(mutex.isLocked(key), "Key $key should be unlocked")
        }

        // Test with concurrent access to same key
        repeat(50) {
            val key = "shared-key"
            val jobs = (1..10).map {
                async {
                    mutex.withLock(key) {
                        delay(1)
                    }
                }
            }
            jobs.awaitAll()
            assertFalse(mutex.isLocked(key), "Shared key should be unlocked after round $it")
        }
    }

    @Test
    fun highConcurrencyStressTest() = runTest {
        val mutex = KeyedMutex<String>()
        val sharedCounter = mutableMapOf<String, Int>()
        val keys = listOf("key1", "key2", "key3")

        // Initialize counters
        keys.forEach { key -> sharedCounter[key] = 0 }

        val jobs = (1..300).map { i ->
            this.async {
                val key = keys[i % keys.size]
                mutex.withLock(key) {
                    val current = sharedCounter[key]!!
                    yield() // Force context switch to test race conditions
                    sharedCounter[key] = current + 1
                }
            }
        }

        jobs.awaitAll()

        // Verify that each counter was incremented exactly the right number of times
        keys.forEach { key ->
            val expected = 300 / keys.size
            assertEquals(expected, sharedCounter[key], "Counter for $key should be exactly $expected")
            assertFalse(mutex.isLocked(key), "Key $key should be unlocked")
        }
    }

    @Test
    fun isLockedObservationOnly() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"

        // isLocked should work for non-existent keys
        assertFalse(mutex.isLocked(key), "Non-existent key should not be locked")

        mutex.lock(key)
        assertTrue(mutex.isLocked(key), "Locked key should return true")

        // isLocked should be usable from concurrent contexts
        val job = this.async {
            mutex.isLocked(key)
        }

        assertTrue(job.await(), "isLocked should work from concurrent context")
        mutex.unlock(key)
    }

    @Test
    fun holdsLockOwnershipCheck() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val owner1 = "owner1"
        val owner2 = "owner2"

        // holdsLock should return false for non-existent keys
        assertFalse(mutex.holdsLock(key, owner1), "Non-existent key should not be held by any owner")

        // Lock with owner1
        mutex.lock(key, owner1)
        assertTrue(mutex.holdsLock(key, owner1), "owner1 should hold the lock")
        assertFalse(mutex.holdsLock(key, owner2), "owner2 should not hold the lock")

        // After unlock, no one should hold the lock
        mutex.unlock(key, owner1)
        assertFalse(mutex.holdsLock(key, owner1), "owner1 should not hold the lock after unlock")
        assertFalse(mutex.holdsLock(key, owner2), "owner2 should not hold the lock after unlock")
    }

    @Test
    fun holdsLockWithTryLock() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val owner1 = "owner1"
        val owner2 = "owner2"

        // Acquire lock with tryLock
        assertTrue(mutex.tryLock(key, owner1), "tryLock should succeed")
        assertTrue(mutex.holdsLock(key, owner1), "owner1 should hold the lock after tryLock")
        assertFalse(mutex.holdsLock(key, owner2), "owner2 should not hold the lock")

        // Second tryLock with different owner should fail
        assertFalse(mutex.tryLock(key, owner2), "tryLock with different owner should fail")
        assertTrue(mutex.holdsLock(key, owner1), "owner1 should still hold the lock")
        assertFalse(mutex.holdsLock(key, owner2), "owner2 should still not hold the lock")

        mutex.unlock(key, owner1)
        assertFalse(mutex.holdsLock(key, owner1), "owner1 should not hold lock after unlock")
    }

    @Test
    fun holdsLockConcurrentAccess() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        val owner = "test-owner"

        mutex.lock(key, owner)

        // holdsLock should be usable from concurrent contexts
        val job = this.async {
            mutex.holdsLock(key, owner)
        }

        assertTrue(job.await(), "holdsLock should work from concurrent context")
        mutex.unlock(key, owner)
    }

    @Test
    fun reentrantBehaviorShouldDeadlock() = runTest {
        val mutex = KeyedMutex<String>()
        val key = "test-key"
        var innerReached = false

        // This test verifies that the mutex is NOT re-entrant
        // According to the documentation, it's "Not re-entrant for the same key within the same coroutine"

        val job = this.async {
            mutex.withLock(key) {
                try {
                    // This should deadlock since we're trying to lock the same key again
                    // in the same coroutine. We use a timeout to detect this.
                    mutex.withLock(key) {
                        innerReached = true
                    }
                } catch (e: Exception) {
                    // May throw if timeout or cancellation occurs
                }
            }
        }

        // Give it a short time to potentially complete
        delay(100)

        // The job should still be running (deadlocked)
        assertFalse(job.isCompleted, "Re-entrant lock should deadlock")
        assertFalse(innerReached, "Inner block should not be reached")

        // Clean up by cancelling
        job.cancel()

        // Verify the key gets cleaned up properly after cancellation
        delay(10)
        assertFalse(mutex.isLocked(key), "Key should be cleaned up after cancellation")
    }
}
