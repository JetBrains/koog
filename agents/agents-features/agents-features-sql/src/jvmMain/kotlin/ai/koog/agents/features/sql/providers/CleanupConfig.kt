package ai.koog.agents.features.sql.providers

/**
 * Configuration for TTL cleanup behavior
 *
 * @property enabled Whether TTL cleanup should be performed automatically
 * @property intervalMs Minimum interval between cleanup operations in milliseconds (default: 1 minute)
 */
public data class CleanupConfig(
    val enabled: Boolean = true,
    val intervalMs: Long = 60_000L
) {
    /**
     * Companion object for CleanupConfig providing factory methods for creating configuration instances.
     */
    public companion object {
        /**
         * Creates a default CleanupConfig instance with automatic TTL cleanup enabled
         * and a default interval of 1 minute between cleanup operations.
         *
         * @return A CleanupConfig instance with default settings.
         */
        public fun default(): CleanupConfig = CleanupConfig()

        /**
         * Creates a CleanupConfig instance with automatic TTL cleanup disabled.
         *
         * @return A CleanupConfig instance with the `enabled` property set to `false`.
         */
        public fun disabled(): CleanupConfig = CleanupConfig(enabled = false)
    }
}
