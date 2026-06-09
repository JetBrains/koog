package ai.koog.prompt.executor.clients.foundationmodels

/**
 * Thin seam over the Foundation Models framework so the client logic is testable
 * with a fake. The production implementation wraps the bundled `@objc` shim via
 * cinterop and lives in the iOS leaf source sets (cinterop is not visible from the
 * shared `appleMain` source set without the commonizer).
 */
internal interface FoundationModelsSession {
    /**
     * Null when the on-device model is available, else a stable availability token
     * (mapped by [foundationModelsAvailabilityFromToken]; not display text).
     */
    fun availabilityToken(): String?

    /** One-shot generation. Returns the model's text; throws [FoundationModelsException.Generation] on failure. */
    suspend fun respond(prompt: String, instructions: String?): String
}

/** Per-target factory for the production [FoundationModelsSession] (actuals in leaf source sets). */
internal expect fun defaultFoundationModelsSession(): FoundationModelsSession
