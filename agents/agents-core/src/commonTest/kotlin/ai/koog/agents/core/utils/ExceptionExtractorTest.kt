package ai.koog.agents.core.utils

import ai.koog.agents.core.utils.ExceptionExtractor.rootCause
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExceptionExtractorTest {
    private class TestCancellationException(
        message: String,
        override val cause: Throwable? = null,
    ) : CancellationException(message)

    @Test
    fun testRootCauseReturnsUnderlyingNonCancellationException() {
        val root = IllegalStateException("root")
        val nested = TestCancellationException(
            message = "outer",
            cause = TestCancellationException(
                message = "inner",
                cause = root,
            ),
        )

        assertSame(root, nested.rootCause)
    }

    @Test
    fun testRootCauseReturnsNullWhenOnlyCancellationExceptionsPresent() {
        val nested = TestCancellationException(
            message = "outer",
            cause = TestCancellationException("inner"),
        )

        assertNull(nested.rootCause)
    }
}
