package ai.koog.agents.lock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Deterministic cancellation tests that contend the locks' internal bookkeeping mutex (accessed via
 * reflection), which otherwise only happens under true parallelism.
 */
class LockCancellationJvmTest {

    private fun Any.privateField(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    @Test
    fun testReaderCancelledWhileReleasingWithContendedCountReleasesLock() = runTest {
        val rwLock = RWLock()
        val readersCountMutex = rwLock.privateField("readersCountMutex") as Mutex
        val releaseReader = CompletableDeferred<Unit>()
        val reader = launch { rwLock.withReadLock { releaseReader.await() } }
        val cancelledReader = launch { rwLock.withReadLock { awaitCancellation() } }
        runCurrent()

        readersCountMutex.lock()
        cancelledReader.cancel()
        runCurrent()
        readersCountMutex.unlock()
        cancelledReader.join()
        releaseReader.complete(Unit)
        reader.join()

        withTimeout(1.seconds) { rwLock.withWriteLock { } }
    }

    @Test
    fun testHolderCancelledWhileUnlockingWithContendedMapReleasesKey() = runTest {
        val mutex = KeyedMutex<String>()
        val mapMutex = mutex.privateField("mapMutex") as Mutex
        val holder = launch { mutex.withLock("key") { awaitCancellation() } }
        runCurrent()

        mapMutex.lock()
        holder.cancel()
        runCurrent()
        mapMutex.unlock()
        holder.join()

        withTimeout(1.seconds) { mutex.withLock("key") { } }
    }

    @Test
    fun testWaiterCancelledWithContendedMapDoesNotLeakEntry() = runTest {
        val mutex = KeyedMutex<String>()
        val mapMutex = mutex.privateField("mapMutex") as Mutex
        val releaseHolder = CompletableDeferred<Unit>()
        val holder = launch { mutex.withLock("key") { releaseHolder.await() } }
        val waiter = launch { mutex.lock("key") }
        runCurrent()

        mapMutex.lock()
        waiter.cancel()
        runCurrent()
        mapMutex.unlock()
        waiter.join()
        releaseHolder.complete(Unit)
        holder.join()

        assertEquals(emptyMap<Any?, Any?>(), mutex.privateField("entries"))
    }
}
