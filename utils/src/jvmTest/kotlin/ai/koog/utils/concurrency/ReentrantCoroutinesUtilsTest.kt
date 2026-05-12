package ai.koog.utils.concurrency

import ai.koog.utils.annotations.InternalKoogUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(InternalKoogUtils::class)
class ReentrantCoroutinesUtilsTest {
    @Test
    fun `test runBlockingReentrant does not block on a single thread executor when reentering`() {
        val future = Executors.newSingleThreadExecutor().submit {
            val executor = Executors.newSingleThreadExecutor()

            // Verify that a new context is created if it's different, but the interceptor is stripped and no deadlock occurs
            val name1 = CoroutineName("coroutine-1")
            val name2 = CoroutineName("coroutine-2")

            runBlockingReentrant(executor.asCoroutineDispatcher() + name1) {
                assertEquals(name1, currentCoroutineContext()[CoroutineName])

                runBlockingReentrant(executor.asCoroutineDispatcher() + name2) {
                    // should be reached
                    assertEquals(name2, currentCoroutineContext()[CoroutineName])
                }
            }
        }

        future.get(3, TimeUnit.SECONDS)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `test withContextReentrant switches to the provided executor`() {
        val future = Executors.newSingleThreadExecutor().submit {
            runBlocking {
                val executor1 = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "executor-1-${Uuid.random()}")
                }
                val executor2 = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "executor-2-${Uuid.random()}")
                }

                withContextReentrant(executor1.asCoroutineDispatcher()) {
                    assertTrue(
                        Thread.currentThread().name.startsWith("executor-1"),
                        "Should run on executor-1"
                    )

                    withContextReentrant(executor2.asCoroutineDispatcher()) {
                        assertTrue(
                            Thread.currentThread().name.startsWith("executor-2"),
                            "Should run on executor-2"
                        )

                        withContextReentrant(executor1.asCoroutineDispatcher()) {
                            assertTrue(
                                Thread.currentThread().name.startsWith("executor-1"),
                                "Should run on executor-1 again"
                            )
                        }
                    }
                }
            }
        }

        future.get(3, TimeUnit.SECONDS)
    }

    /**
     * Covers the nested-exception case by entering dispatcher1, switching to dispatcher2, throwing and catching an
     * exception, then immediately making a blocking [runBlockingReentrant] call back to dispatcher1.
     *
     * The final call verifies that the original reentrant marker was restored and does not deadlock.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `test withContextReentrant restores reentrant context after nested exception`() {
        val submitExecutor = Executors.newSingleThreadExecutor()

        try {
            val future = submitExecutor.submit {
                runBlocking {
                    val dispatcher1 = Executors
                        .newSingleThreadExecutor { runnable -> Thread(runnable, "executor-1-${Uuid.random()}") }
                        .asCoroutineDispatcher()

                    val dispatcher2 = Executors
                        .newSingleThreadExecutor { runnable -> Thread(runnable, "executor-2-${Uuid.random()}") }
                        .asCoroutineDispatcher()

                    try {
                        withContextReentrant(dispatcher1) {
                            assertTrue(
                                Thread.currentThread().name.startsWith("executor-1"),
                                "Should run on executor-1"
                            )

                            assertFailsWith<IllegalStateException> {
                                withContextReentrant(dispatcher2) {
                                    assertTrue(
                                        Thread.currentThread().name.startsWith("executor-2"),
                                        "Should run on executor-2"
                                    )

                                    throw IllegalStateException("Expected failure")
                                }
                            }

                            val name = CoroutineName("after-failure")
                            runBlockingReentrant(dispatcher1 + name) {
                                assertEquals(name, currentCoroutineContext()[CoroutineName])
                                assertTrue(
                                    Thread.currentThread().name.startsWith("executor-1"),
                                    "Should run on executor-1 without redispatching after nested exception"
                                )
                            }
                        }
                    } finally {
                        dispatcher1.close()
                        dispatcher2.close()
                    }
                }
            }

            future.get(3, TimeUnit.SECONDS)
        } finally {
            submitExecutor.shutdownNow()
        }
    }
}
