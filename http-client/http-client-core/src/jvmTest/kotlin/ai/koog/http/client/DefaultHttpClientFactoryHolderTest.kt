package ai.koog.http.client

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * State-mutating tests share a singleton holder; no reset operation exists by design.
 * Each test either does not touch holder state (pure resolver tests) or sets a known
 * value of its own before asserting (last-write-wins).
 */
@Execution(ExecutionMode.SAME_THREAD)
class DefaultHttpClientFactoryHolderTest {

    @Test
    fun testResolveFactoryFromProvidersReturnsSingle() {
        val provider = StubFactory("StubFactory-A")
        val resolved = DefaultHttpClientFactoryHolder.resolveFactoryFromProviders(listOf(provider))
        assertSame(provider, resolved)
    }

    @Test
    fun testResolveFactoryFromProvidersThrowsWhenZero() {
        val ex = assertFailsWith<IllegalStateException> {
            DefaultHttpClientFactoryHolder.resolveFactoryFromProviders(emptyList())
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "DefaultHttpClientFactoryHolder.setDefault" in message,
            "Expected zero-provider error to point at setDefault(), got: $message"
        )
    }

    @Test
    fun testResolveFactoryFromProvidersThrowsWhenMultiple() {
        val first = StubFactory("StubFactory-A")
        val second = StubFactory("StubFactory-B")
        val ex = assertFailsWith<IllegalStateException> {
            DefaultHttpClientFactoryHolder.resolveFactoryFromProviders(listOf(first, second))
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "DefaultHttpClientFactoryHolder.setDefault" in message,
            "Expected multiple-provider error to suggest setDefault(), got: $message"
        )
        assertTrue(
            "StubFactory" in message,
            "Expected multiple-provider error to list discovered classes, got: $message"
        )
    }

    @Test
    fun testSetDefaultStoresFactory() {
        val stub = StubFactory("Set")
        DefaultHttpClientFactoryHolder.setDefault(stub)
        assertSame(stub, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())
    }

    @Test
    fun testSetDefaultOverwritesPreviousValue() {
        val first = StubFactory("First")
        val second = StubFactory("Second")
        DefaultHttpClientFactoryHolder.setDefault(first)
        DefaultHttpClientFactoryHolder.setDefault(second)
        assertSame(second, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())
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
