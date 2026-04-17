package ai.koog.agents.core.utils

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResultUtilsTest {
    @Test
    fun testRunCatchingCancellableReturnsFailureForRegularException() {
        val result = runCatchingCancellable<String> {
            error("boom")
        }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun testRunCatchingCancellableRethrowsCancellationException() {
        assertFailsWith<CancellationException> {
            runCatchingCancellable<String> {
                throw CancellationException("cancelled")
            }
        }
    }
}
