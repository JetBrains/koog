package ai.koog.a2a.validation

import ai.koog.a2a.exceptions.A2AErrorCodes
import ai.koog.a2a.exceptions.A2AVersionNotSupportedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun testRequireSupportedVersionAcceptsSameMajorMinorAndRejectsOtherVersions() {
        val supported = setOf("1.0")

        requireSupportedVersion("1.0", supported)
        requireSupportedVersion("1.0.5", supported)
        requireSupportedVersion(" 1.0 ", supported)

        listOf("1.1", "2.0", "99.0", "0.3", "1", "abc").forEach { version ->
            val e = assertFailsWith<A2AVersionNotSupportedException>(version) {
                requireSupportedVersion(version, supported)
            }
            assertEquals(A2AErrorCodes.VERSION_NOT_SUPPORTED, e.errorCode)
        }
    }

    @Test
    fun testRequireSupportedVersionTreatsMissingAsV03() {
        assertFailsWith<A2AVersionNotSupportedException> { requireSupportedVersion(null, setOf("1.0")) }
        assertFailsWith<A2AVersionNotSupportedException> { requireSupportedVersion("", setOf("1.0")) }
        assertFailsWith<A2AVersionNotSupportedException> { requireSupportedVersion("  ", setOf("1.0")) }

        // Explicitly allowed, only the check changes: 0.3 is what a missing version stands for
        requireSupportedVersion(null, setOf("1.0", "0.3"))
        requireSupportedVersion("", setOf("0.3"))
    }

    @Test
    fun testRequireSupportedVersionMessageNamesRequestedAndSupportedVersions() {
        val e = assertFailsWith<A2AVersionNotSupportedException> {
            requireSupportedVersion("2.1.3", setOf("1.0", "1.1"))
        }

        assertEquals("A2A version '2.1' is not supported; supported: 1.0, 1.1", e.message)
    }
}
