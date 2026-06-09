package ai.koog.prompt.executor.clients.foundationmodels

import kotlin.test.Test
import kotlin.test.assertEquals

class FoundationModelsAvailabilityTest {

    @Test
    fun testNullTokenMapsToAvailable() {
        assertEquals(
            FoundationModelsAvailability.Available,
            foundationModelsAvailabilityFromToken(null),
        )
    }

    @Test
    fun testKnownTokensMapToTypedCases() {
        assertEquals(
            FoundationModelsAvailability.Unavailable.DeviceNotEligible,
            foundationModelsAvailabilityFromToken("deviceNotEligible"),
        )
        assertEquals(
            FoundationModelsAvailability.Unavailable.AppleIntelligenceNotEnabled,
            foundationModelsAvailabilityFromToken("appleIntelligenceNotEnabled"),
        )
        assertEquals(
            FoundationModelsAvailability.Unavailable.ModelNotReady,
            foundationModelsAvailabilityFromToken("modelNotReady"),
        )
        assertEquals(
            FoundationModelsAvailability.Unavailable.OSVersionTooOld,
            foundationModelsAvailabilityFromToken("osVersionTooOld"),
        )
    }

    @Test
    fun testUnrecognizedTokenMapsToUnknownCarryingTheToken() {
        assertEquals(
            FoundationModelsAvailability.Unavailable.Unknown("unknown:somethingNew"),
            foundationModelsAvailabilityFromToken("unknown:somethingNew"),
        )
    }
}
