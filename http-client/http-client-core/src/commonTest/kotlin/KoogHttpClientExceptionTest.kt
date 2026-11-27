import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.lowercaseHeaderKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoogHttpClientExceptionTest {

    @Test
    fun testConstructorNormalizesHeaderKeysToLowercase() {
        val exception = KoogHttpClientException(
            clientName = "TestClient",
            statusCode = 429,
            headers = mapOf(
                "Retry-After" to listOf("5"),
                "X-RateLimit-Reset-Tokens" to listOf("6m0s")
            )
        )

        assertEquals(listOf("5"), exception.headers["retry-after"])
        assertEquals(listOf("6m0s"), exception.headers["x-ratelimit-reset-tokens"])
        assertEquals(setOf("retry-after", "x-ratelimit-reset-tokens"), exception.headers.keys)
    }

    @Test
    fun testConstructorMergesKeysDifferingOnlyInCase() {
        val exception = KoogHttpClientException(
            headers = mapOf(
                "Set-Cookie" to listOf("a=1"),
                "set-cookie" to listOf("b=2")
            )
        )

        assertEquals(listOf("a=1", "b=2"), exception.headers["set-cookie"])
    }

    @Test
    fun testHeadersDefaultToEmpty() {
        assertTrue(KoogHttpClientException(clientName = "TestClient").headers.isEmpty())
    }

    @Test
    fun testLowercaseHeaderKeysOnEmptyMapAvoidsAllocation() {
        val empty: Map<String, List<String>> = emptyMap()
        assertTrue(empty.lowercaseHeaderKeys().isEmpty())
    }
}
