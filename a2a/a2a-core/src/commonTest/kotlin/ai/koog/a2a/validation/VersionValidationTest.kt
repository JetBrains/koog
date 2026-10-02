package ai.koog.a2a.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionValidationTest {
    @Test
    fun testIsVersionCompatibleComparesMajorMinorOnly() {
        assertTrue(isVersionCompatible("1.0", "1.0"))
        assertTrue(isVersionCompatible("1.0.1", "1.0"))
        assertFalse(isVersionCompatible("1.1", "1.0"))
        assertFalse(isVersionCompatible("2.0", "1.0"))
        // A blank version is treated as 0.3
        assertTrue(isVersionCompatible("", "0.3"))
        assertTrue(isVersionCompatible(null, "0.3.0"))
        assertFalse(isVersionCompatible("", "1.0"))
    }
}
