package ai.koog.agents.utils

import kotlinx.serialization.Serializable

/**
 * Represents model information for agent events with a focus on essential data.
 *
 * This data class provides the core model identification needed for agent events,
 * including key model characteristics that are useful for debugging and monitoring.
 * It serves as a lightweight representation optimized for serialization and backwards compatibility.
 *
 * @property provider The provider identifier (e.g., "openai", "google", "anthropic")
 * @property model The model identifier (e.g., "gpt-4", "claude-3")
 * @property displayName Optional human-readable display name for the model
 * @property contextLength Maximum number of tokens the model can process
 * @property maxOutputTokens Maximum number of tokens the model can generate
 */
@Serializable
public data class ModelInfo(
    val provider: String,
    val model: String,
    val displayName: String? = null,
    val contextLength: Long? = null,
    val maxOutputTokens: Long? = null
) {
    /**
     * Backwards compatibility: provides the same string format as legacy model representation
     * Format: "provider:model"
     * @deprecated This format is maintained for backwards compatibility.
     *             Consider using modelIdentifierName for display purposes.
     */
    @Deprecated("Use modelIdentifierName for display, or access provider/model directly")
    public val eventString: String get() = "$provider:$model"

    /**
     * Model identifier name for display purposes
     * Falls back to "provider/model" if displayName is not provided
     */
    public val modelIdentifierName: String get() = displayName ?: "$provider/$model"

    public companion object {
        /**
         * Represents an undefined or invalid ModelInfo instance
         * Used when model information cannot be determined or parsed
         */
        public val UNDEFINED: ModelInfo = ModelInfo(provider = "unknown", model = "undefined")

        /**
         * Creates a ModelInfo instance from a string in the format "provider:model"
         * This provides backwards compatibility with existing string-based model representations
         *
         * @param modelString The model string in "provider:model" format
         * @return A ModelInfo instance parsed from the string, or UNDEFINED if parsing fails
         */
        public fun fromString(modelString: String): ModelInfo = modelString
            .split(":", limit = 2)
            .let { parts ->
                if (parts.size == 2) {
                    ModelInfo(provider = parts[0], model = parts[1])
                } else {
                    UNDEFINED
                }
            }
    }
}
