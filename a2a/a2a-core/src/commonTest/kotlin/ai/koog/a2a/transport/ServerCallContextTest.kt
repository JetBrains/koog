package ai.koog.a2a.transport

import ai.koog.a2a.consts.A2AHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerCallContextTest {
    @Test
    fun testNoHeadersMeansVersion03AndNoExtensions() {
        val ctx = ServerCallContext()

        assertEquals("0.3", ctx.requestedVersion)
        assertEquals(emptySet(), ctx.requestedExtensions)
    }

    @Test
    fun testEmptyVersionHeaderMeansVersion03() {
        assertEquals("0.3", ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf(""))).requestedVersion)
        assertEquals("0.3", ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to emptyList())).requestedVersion)
    }

    @Test
    fun testRequestedVersionIsNormalizedToMajorMinor() {
        assertEquals("1.0", ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("1.0"))).requestedVersion)
        assertEquals("1.0", ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf(" 1.0.5 "))).requestedVersion)
    }

    @Test
    fun testHeaderNamesAreLookedUpCaseInsensitively() {
        val ctx = ServerCallContext(
            mapOf(
                "a2a-version" to listOf("1.0"),
                "A2A-EXTENSIONS" to listOf("urn:a"),
            )
        )

        assertEquals("1.0", ctx.requestedVersion)
        assertEquals(setOf("urn:a"), ctx.requestedExtensions)
    }

    @Test
    fun testExtensionsAreSplitTrimmedAndDeduplicatedAcrossHeaderValues() {
        val ctx = ServerCallContext(
            mapOf(A2AHeaders.A2A_EXTENSIONS to listOf("urn:a, urn:b ,, ", "", "urn:c,urn:a", "  "))
        )

        assertEquals(setOf("urn:a", "urn:b", "urn:c"), ctx.requestedExtensions)
    }

    @Test
    fun testCopyKeepsRequestedValuesWhenHeadersAreKept() {
        val ctx = ServerCallContext(
            mapOf(A2AHeaders.A2A_VERSION to listOf("1.0"), A2AHeaders.A2A_EXTENSIONS to listOf("urn:a"))
        )

        val copy = ctx.copy(state = emptyMap())

        assertEquals("1.0", copy.requestedVersion)
        assertEquals(setOf("urn:a"), copy.requestedExtensions)
    }

    @Test
    fun testCopyRederivesRequestedValuesFromNewHeaders() {
        val ctx = ServerCallContext(mapOf(A2AHeaders.A2A_VERSION to listOf("1.0")))

        val copy = ctx.copy(headers = mapOf("a2a-version" to listOf("2.0"), "a2a-extensions" to listOf("urn:b")))

        assertEquals("2.0", copy.requestedVersion)
        assertEquals(setOf("urn:b"), copy.requestedExtensions)
    }

    @Test
    fun testCaseInsensitiveHeadersLookup() {
        val headers = mapOf("a2a-version" to listOf("1.0"), "X-Other" to listOf("a"), "x-other" to listOf("b"))
            .withCaseInsensitiveNames()

        assertEquals(listOf("1.0"), headers["A2A-Version"])
        assertEquals(listOf("a", "b"), headers["X-OTHER"])
        assertEquals(true, headers.containsKey("A2A-VERSION"))
        assertNull(headers["missing"])
        // Names are kept as received
        assertEquals(setOf("a2a-version", "X-Other", "x-other"), headers.keys)
    }
}
