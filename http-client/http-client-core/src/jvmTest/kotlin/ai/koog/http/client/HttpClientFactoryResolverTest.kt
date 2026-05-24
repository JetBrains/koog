package ai.koog.http.client

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpClientFactoryResolverTest {

    @Test
    fun testResolveFactoryFromProvidersReturnsSingle() {
        val provider = StubFactory("StubFactory-A")
        val resolved = HttpClientFactoryResolver.resolveFactoryFromProviders(listOf(provider))
        assertSame(provider, resolved)
    }

    @Test
    fun testResolveFactoryFromProvidersThrowsWhenZero() {
        val ex = assertFailsWith<IllegalStateException> {
            HttpClientFactoryResolver.resolveFactoryFromProviders(emptyList())
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "No KoogHttpClient.Factory provider found" in message,
            "Expected zero-provider error to explain the cause, got: $message"
        )
        assertTrue(
            "pass a KoogHttpClient.Factory explicitly" in message,
            "Expected zero-provider error to mention explicit factory as a remediation, got: $message"
        )
    }

    @Test
    fun testResolveFactoryFromProvidersThrowsWhenMultiple() {
        val first = StubFactory("StubFactory-A")
        val second = StubFactory("StubFactory-B")
        val ex = assertFailsWith<IllegalStateException> {
            HttpClientFactoryResolver.resolveFactoryFromProviders(listOf(first, second))
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "Exclude all but one provider module" in message,
            "Expected multiple-provider error to suggest module exclusion, got: $message"
        )
        assertTrue(
            "StubFactory" in message,
            "Expected multiple-provider error to list discovered classes, got: $message"
        )
    }
}

private class StubFactory(private val name: String) : KoogHttpClient.Factory {
    override fun create(
        clientName: String,
        baseUrl: String,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
        socketTimeoutMillis: Long,
        json: Json
    ): KoogHttpClient = error("StubFactory($name) does not create clients")

    override fun toString(): String = "StubFactory($name)"
}
