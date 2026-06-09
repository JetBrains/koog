package ai.koog.prompt.executor.clients.foundationmodels

/**
 * Availability of the on-device Foundation Models stack, as reported by Apple's
 * `SystemLanguageModel.default.availability`, plus the client-side
 * [Unavailable.OSVersionTooOld] case for systems older than iOS 26.
 *
 * Check this via [FoundationModelsLLMClient.availability] before routing prompts
 * on-device: register the Apple client only when [Available] and configure a cloud
 * fallback otherwise. [FoundationModelsLLMClient.execute] throws
 * [FoundationModelsException.Unavailable] carrying the same case when invoked while
 * unavailable.
 */
public sealed interface FoundationModelsAvailability {

    /** The on-device model is ready; [FoundationModelsLLMClient.execute] can run. */
    public data object Available : FoundationModelsAvailability

    /** The on-device model cannot run; the case says why. */
    public sealed interface Unavailable : FoundationModelsAvailability {
        /** The device hardware is not eligible for Apple Intelligence. */
        public data object DeviceNotEligible : Unavailable

        /** Apple Intelligence is switched off in Settings. */
        public data object AppleIntelligenceNotEnabled : Unavailable

        /** The model assets are not downloaded yet; may resolve on its own, retry later. */
        public data object ModelNotReady : Unavailable

        /** The OS predates iOS 26, where the Foundation Models framework first shipped. */
        public data object OSVersionTooOld : Unavailable

        /** A reason this client version does not know (future framework cases). */
        public data class Unknown(public val reason: String) : Unavailable
    }
}

/**
 * Maps a stable availability token from the Swift shim (`null` = available, see
 * `KoogFMBridge.availabilityToken`) to the public sealed type. Unrecognized tokens map
 * to [FoundationModelsAvailability.Unavailable.Unknown] so a newer shim never breaks
 * older Kotlin.
 */
internal fun foundationModelsAvailabilityFromToken(token: String?): FoundationModelsAvailability = when (token) {
    null -> FoundationModelsAvailability.Available
    "deviceNotEligible" -> FoundationModelsAvailability.Unavailable.DeviceNotEligible
    "appleIntelligenceNotEnabled" -> FoundationModelsAvailability.Unavailable.AppleIntelligenceNotEnabled
    "modelNotReady" -> FoundationModelsAvailability.Unavailable.ModelNotReady
    "osVersionTooOld" -> FoundationModelsAvailability.Unavailable.OSVersionTooOld
    else -> FoundationModelsAvailability.Unavailable.Unknown(token)
}
