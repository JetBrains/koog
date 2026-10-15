package ai.koog.agents.lock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RWLockTest {

    @Test
    fun testMultipleReaders() = runTest {
        val rwLock = RWLock()
        var counter = 0

        // Launch multiple readers concurrently
        val jobs = List(5) {
            launch {
                rwLock.withReadLock {
                    counter++
                    delay(10) // Simulate some work
                    counter--
                }
            }
        }

        // Wait for all readers to complete
        jobs.joinAll()

        // Counter should be 0 after all readers have completed
        assertEquals(0, counter)
    }

    @Test
    fun testExclusiveWriter() = runTest {
        val rwLock = RWLock()
        var sharedResource = 0
        var writerActive = false

        // Launch a writer
        val writerJob = launch {
            rwLock.withWriteLock {
                writerActive = true
                sharedResource = 1
                delay(50) // Simulate some work
                sharedResource = 2
                writerActive = false
            }
        }

        // Launch a reader that should wait for the writer to complete
        val readerJob = async {
            rwLock.withReadLock {
                assertFalse(writerActive, "Reader should not be active while writer is active")
                return@withReadLock sharedResource
            }
        }

        writerJob.join()
        val result = readerJob.await()

        // Reader should see the final value written by the writer
        assertEquals(2, result)
    }

    @Test
    fun testReaderBlocksWriter() = runTest {
        val rwLock = RWLock()
        var readerActive = false
        var writerExecuted = false

        // Launch a reader
        val readerJob = launch {
            rwLock.withReadLock {
                readerActive = true
                delay(50) // Hold the read lock for a while
                readerActive = false
            }
        }

        // Give the reader time to acquire the lock
        while (!readerActive) delay(10)

        // Launch a writer that should wait for the reader to complete
        val writerJob = launch {
            rwLock.withWriteLock {
                assertFalse(readerActive, "Writer should not be active while reader is active")
                writerExecuted = true
            }
        }

        readerJob.join()
        writerJob.join()

        assertTrue(writerExecuted, "Writer should have executed after reader completed")
    }

    @Test
    fun testMultipleReadersOneWriter() = runTest {
        val rwLock = RWLock()
        var activeReaders = 0
        var writerActive = false
        var maxActiveReaders = 0

        // Launch multiple readers
        val readerJobs = List(3) {
            launch {
                rwLock.withReadLock {
                    activeReaders++
                    maxActiveReaders = maxOf(maxActiveReaders, activeReaders)
                    assertFalse(writerActive, "Reader should not be active while writer is active")
                    delay(30) // Simulate some work
                    activeReaders--
                }
            }
        }

        // Give readers time to acquire locks
        delay(10)

        // Launch a writer
        val writerJob = launch {
            rwLock.withWriteLock {
                writerActive = true
                assertEquals(0, activeReaders, "No readers should be active during write lock")
                delay(30) // Simulate some work
                writerActive = false
            }
        }

        readerJobs.joinAll()
        writerJob.join()

        assertTrue(maxActiveReaders > 1, "Multiple readers should have been active concurrently")
        assertEquals(0, activeReaders, "All readers should have completed")
    }

    @Test
    fun testExceptionHandling() = runTest {
        val rwLock = RWLock()
        var lockReleased = false

        try {
            rwLock.withReadLock {
                throw RuntimeException("Test exception")
            }
        } catch (_: RuntimeException) {
            // Expected exception
        }

        // Verify that the lock was released by acquiring a write lock
        rwLock.withWriteLock {
            lockReleased = true
        }

        assertTrue(lockReleased, "Lock should be released even if an exception occurs")
    }

    @Test
    fun testReaderCancelledWhileWaitingForWriterKeepsExclusion() = runTest {
        val rwLock = RWLock()
        val releaseWriter = CompletableDeferred<Unit>()
        val writer = launch { rwLock.withWriteLock { releaseWriter.await() } }
        runCurrent()

        // A reader suspended on the active writer is cancelled before it gets the lock.
        val cancelledReader = launch { rwLock.withReadLock { } }
        runCurrent()
        cancelledReader.cancelAndJoin()
        releaseWriter.complete(Unit)
        writer.join()

        val releaseReader = CompletableDeferred<Unit>()
        val reader = launch { rwLock.withReadLock { releaseReader.await() } }
        runCurrent()
        var writerRan = false
        val secondWriter = launch { rwLock.withWriteLock { writerRan = true } }
        runCurrent()

        assertFalse(writerRan, "Writer must not run while a reader holds the lock")
        releaseReader.complete(Unit)
        joinAll(reader, secondWriter)
        assertTrue(writerRan, "Writer should run once the reader releases the lock")
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testRandomlyCancelledReadersAndWritersKeepLockConsistent() = runTest {
        val rwLock = RWLock()
        val activeReaders = AtomicInt(0)
        val activeWriters = AtomicInt(0)
        val violations = AtomicInt(0)

        withContext(Dispatchers.Default) {
            withTimeout(20.seconds) {
                repeat(2_000) {
                    val jobs = List(8) { i ->
                        launch {
                            if (i == 0) {
                                rwLock.withWriteLock {
                                    if (activeWriters.incrementAndFetch() != 1 || activeReaders.load() != 0) {
                                        violations.incrementAndFetch()
                                    }
                                    try {
                                        yield()
                                    } finally {
                                        activeWriters.decrementAndFetch()
                                    }
                                }
                            } else {
                                rwLock.withReadLock {
                                    activeReaders.incrementAndFetch()
                                    try {
                                        if (activeWriters.load() != 0) violations.incrementAndFetch()
                                        yield()
                                    } finally {
                                        activeReaders.decrementAndFetch()
                                    }
                                }
                            }
                        }
                    }
                    repeat(Random.nextInt(100)) { yield() }
                    jobs.filter { Random.nextBoolean() }.forEach { it.cancel() }
                    jobs.joinAll()
                }
                // A leaked read lock would keep the writer suspended forever.
                rwLock.withWriteLock { }
            }
        }

        assertEquals(0, violations.load(), "Readers and writers must never overlap")
    }
}
