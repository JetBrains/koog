package ai.koog.agents.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class KeyedMutexTest {

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testRandomlyCancelledLockersKeepLockConsistent() = runTest {
        val mutex = KeyedMutex<Int>()
        val keys = List(2) { it }
        val holders = keys.map { AtomicInt(0) }
        val violations = AtomicInt(0)

        withContext(Dispatchers.Default) {
            withTimeout(20.seconds) {
                repeat(2_000) {
                    val jobs = List(8) { i ->
                        launch {
                            val key = keys[i % keys.size]
                            mutex.withLock(key) {
                                if (holders[key].incrementAndFetch() != 1) violations.incrementAndFetch()
                                try {
                                    yield()
                                } finally {
                                    holders[key].decrementAndFetch()
                                }
                            }
                        }
                    }
                    repeat(Random.nextInt(100)) { yield() }
                    jobs.filter { Random.nextBoolean() }.forEach { it.cancel() }
                    jobs.joinAll()
                }
                // A leaked key lock would keep these lockers suspended forever.
                keys.forEach { key -> mutex.withLock(key) { } }
            }
        }

        assertEquals(0, violations.load(), "Two holders of the same key must never overlap")
    }
}
