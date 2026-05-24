package ai.koog.http.client

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun testMergeHeadersPreservesUnrelatedHeaders() {
        val result = mergeHeaders(
            mapOf("Authorization" to "Bearer token"),
            mapOf("Content-Type" to "application/json")
        )

        assertEquals(
            mapOf(
                "Authorization" to "Bearer token",
                "Content-Type" to "application/json"
            ),
            result
        )
    }

    @Test
    fun testMergeHeadersUsesLaterGroupsForSameHeaderIgnoringCase() {
        val result = mergeHeaders(
            mapOf("Content-Type" to "text/plain"),
            mapOf("content-type" to "application/json")
        )

        assertEquals(mapOf("content-type" to "application/json"), result)
    }

    @Test
    fun testMergeHeadersUsesArgumentOrderAsPrecedence() {
        val result = mergeHeaders(
            mapOf("X-Request-Id" to "default", "Authorization" to "Bearer default"),
            mapOf("x-request-id" to "inferred"),
            mapOf("X-REQUEST-ID" to "request")
        )

        assertEquals(
            mapOf(
                "Authorization" to "Bearer default",
                "X-REQUEST-ID" to "request"
            ),
            result
        )
    }
}
