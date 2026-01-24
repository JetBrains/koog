package ai.koog.test.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility.await
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AwaitilityExtensionsTest {

    @Test
    fun `untilAsserted with suspend block eventually succeeds`() = runTest {
        val counter = AtomicInteger(0)

        val result = await()
            .atMost(2.seconds)
            .untilAsserted(block = {
                delay(30)
                val current = counter.incrementAndGet()
                if (current < 5) {
                    throw AssertionError("Not enough yet: $current")
                }
                "success-$current"
            })

        assertEquals("success-5", result)
        assertEquals(5, counter.get())
    }

    @Test
    fun `untilAsserted with suspend block fails on timeout`() = runTest {
        assertThrows<ConditionTimeoutException> {
            await()
                .atMost(200.milliseconds)
                .pollInterval(10.milliseconds)
                .untilAsserted(block = {
                    delay(50)
                    fail("Always failing")
                })
        }
    }

    @Test
    fun `untilAsserted with scope and suspend block should eventually succeed`() = runTest {
        val counter = AtomicInteger(0)

        val result = await()
            .atMost(2.seconds)
            .atLeast(40.milliseconds)
            .pollInterval(10.milliseconds)
            .untilAsserted(this) {
                val current = counter.incrementAndGet()
                if (current < 5) {
                    throw AssertionError("Not enough yet: $current")
                }
                "result-$current"
            }

        assertEquals("result-5", result)
        assertEquals(5, counter.get())
    }

    @Test
    fun `untilAsserted with scope and suspend block fails on timeout`() = runTest {
        assertThrows<ConditionTimeoutException> {
            await()
                .pollDelay(0.milliseconds)
                .atLeast(10.milliseconds)
                .atMost(200.milliseconds)
                .pollInterval(10.milliseconds)
                .untilAsserted(this) {
                    delay(50)
                    throw AssertionError("Always failing")
                }
        }
    }
}
