package ai.koog.agents.core.utils

import kotlinx.coroutines.CancellationException

/**
 * Same as [runCatching], but does not catch [CancellationException], throwing it instead, making it safe to use with coroutines.
 */
internal inline fun <R> runCatchingCancellable(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Result.failure(e)
    }
}
