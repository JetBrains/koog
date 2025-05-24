package ai.koog.agents.features.common

import kotlinx.coroutines.CancellationException

internal object ExceptionExtractor {

    /**
     * Extension property that retrieves the root cause of a [CancellationException].
     *
     * This is used in cases when non-suspended code launches a coroutine that raises exceptions.
     * The method helps to get the root exception causing the outer coroutine cancellation.
     */
    val CancellationException.rootCause: Throwable?
        get() {
            var rootCause: Throwable? = this
            while (rootCause != null && rootCause is CancellationException) {
                rootCause = rootCause.cause
            }
            return rootCause
        }
}
