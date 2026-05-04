package ai.koog.agents.lock

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

/**
 * Executes the given [action] within a lock provided by the [Mutex]. Before acquiring the lock,
 * it checks a condition specified by [check]. If the condition is satisfied, the lock is not acquired,
 * and the operation returns early after logging a message provided by [message]. If the condition is
 * not satisfied, the lock is acquired, the condition is rechecked, and if still not satisfied,
 * the [action] is executed.
 */
public suspend fun Mutex.withLockCheck(check: () -> Boolean, message: () -> String, action: suspend () -> Unit) {
    if (check()) {
        logger.debug(message)
        return
    }

    withLock {
        if (check()) {
            logger.debug(message)
            return
        }

        action()
    }
}
