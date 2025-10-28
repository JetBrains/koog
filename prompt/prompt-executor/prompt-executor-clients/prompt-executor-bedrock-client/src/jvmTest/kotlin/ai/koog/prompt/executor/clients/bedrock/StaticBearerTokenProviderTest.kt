package ai.koog.prompt.executor.clients.bedrock

import aws.smithy.kotlin.runtime.collections.emptyAttributes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaticBearerTokenProviderTest {

    @Test
    fun `should create provider with valid token`() {
        val token = "test-bedrock-api-key-123"
        val provider = StaticBearerTokenProvider(token)
        assertNotNull(provider)
    }

    @Test
    fun `should resolve token successfully`() = runTest {
        val expectedToken = "test-bedrock-api-key-456"
        val provider = StaticBearerTokenProvider(expectedToken)
        val bearerToken = provider.resolve(emptyAttributes())
        assertEquals(expectedToken, bearerToken.token)
    }

    @Test
    fun `resolved token should have no expiration`() = runTest {
        val provider = StaticBearerTokenProvider("test-token")
        val bearerToken = provider.resolve(emptyAttributes())
        assertNull(bearerToken.expiration, "Bedrock API keys should not have expiration")
    }

    @Test
    fun `resolved token should have empty attributes`() = runTest {
        val provider = StaticBearerTokenProvider("test-token")
        val bearerToken = provider.resolve(emptyAttributes())
        assertTrue(bearerToken.attributes.isEmpty, "Attributes should be empty")
    }

    @Test
    fun `should fail when token is blank`() = runTest {
        val provider = StaticBearerTokenProvider("")
        val exception = assertFailsWith<IllegalStateException> {
            provider.resolve(emptyAttributes())
        }
        assertEquals(exception.message?.contains("token must not be blank"), true)
    }

    @Test
    fun `should fail when token is only whitespace`() = runTest {
        val provider = StaticBearerTokenProvider("   ")
        val exception = assertFailsWith<IllegalStateException> {
            provider.resolve(emptyAttributes())
        }
        assertEquals(exception.message?.contains("token must not be blank"), true)
    }

    @Test
    fun `should handle long token strings`() = runTest {
        val longToken = "a".repeat(1000)
        val provider = StaticBearerTokenProvider(longToken)
        val bearerToken = provider.resolve(emptyAttributes())
        assertEquals(longToken, bearerToken.token)
    }

    @Test
    fun `should handle tokens with special characters`() = runTest {
        val specialToken = "token-with-special_chars.123!@#$%"
        val provider = StaticBearerTokenProvider(specialToken)
        val bearerToken = provider.resolve(emptyAttributes())
        assertEquals(specialToken, bearerToken.token)
    }

    @Test
    fun `should return same token on multiple resolve calls`() = runTest {
        val expectedToken = "consistent-token"
        val provider = StaticBearerTokenProvider(expectedToken)
        val bearerToken1 = provider.resolve(emptyAttributes())
        val bearerToken2 = provider.resolve(emptyAttributes())
        val bearerToken3 = provider.resolve(emptyAttributes())
        assertEquals(expectedToken, bearerToken1.token)
        assertEquals(expectedToken, bearerToken2.token)
        assertEquals(expectedToken, bearerToken3.token)
    }

    @Test
    fun `should resolve with different attributes parameter`() = runTest {
        val expectedToken = "test-token"
        val provider = StaticBearerTokenProvider(expectedToken)
        // Even though we pass different attributes, token should remain the same
        val bearerToken = provider.resolve(emptyAttributes())
        assertEquals(expectedToken, bearerToken.token)
    }
}
