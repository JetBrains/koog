package ai.koog.a2a.utils

import ai.koog.a2a.annotations.InternalA2AApi
import kotlinx.coroutines.CancellationException

// FIXME copied from agents-core module, because a2a does not depend on other Koog modules.
//  Do we want to make a global utils module for cases like this?
/**
 * Same as [runCatching], but does not catch [CancellationException], throwing it instead, making it safe to use with coroutines.
 */
@InternalA2AApi
public inline fun <R> runCatchingCancellable(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Result.failure(e)
    }
}
