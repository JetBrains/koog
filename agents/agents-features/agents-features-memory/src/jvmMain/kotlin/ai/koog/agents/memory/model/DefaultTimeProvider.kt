package ai.koog.agents.memory.model

/**
 * DefaultTimeProvider provides a platform-specific implementation of the TimeProvider interface.
 *
 * This object is responsible for generating the current timestamp in milliseconds
 * using the system clock. It serves as the default implementation for time-related
 * functionalities across the memory system.
 *
 * Responsibilities:
 * - Provide the current timestamp for memory storage and retrieval operations.
 * - Support consistent timestamp generation for platform-independent features.
 *
 * This implementation relies on `System.currentTimeMillis()` to retrieve the current
 * timestamp, ensuring precise and predictable behavior for time-critical operations.
 */
public actual object DefaultTimeProvider : TimeProvider {
    actual override fun getCurrentTimestamp(): Long = System.currentTimeMillis()
}
