package ai.koog.agents.memory.model

/**
 * DefaultTimeProvider is the platform-specific implementation of the TimeProvider interface
 * for the JavaScript platform. It provides access to the current timestamp in milliseconds
 * since the Unix epoch using the platform's native Date functionality.
 *
 * This implementation ensures:
 * - Platform-specific optimization for timestamp generation.
 * - Consistency in time-related operations within the application.
 *
 * The DefaultTimeProvider is the default time source used throughout the system
 * wherever TimeProvider is required.
 */
public actual object DefaultTimeProvider : TimeProvider {
    actual override fun getCurrentTimestamp(): Long = js("Date.now()").unsafeCast<Long>()
}
