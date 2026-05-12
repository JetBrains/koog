package ai.koog.http.client.ktor

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.defaultFactoryHolder
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * State-mutating tests share a singleton holder; no reset operation exists by design.
 * Each test that mutates state sets its own known value (last-write-wins).
 */
@Execution(ExecutionMode.SAME_THREAD)
class KtorKoogHttpClientSpiTest {

    @Test
    fun testServiceLoaderDiscoversKtorFactory() {
        val providers = ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
        val ktorFactory = providers.singleOrNull { it is KtorKoogHttpClient.Factory }
        assertNotNull(ktorFactory, "Expected KtorKoogHttpClient.Factory to be discoverable via ServiceLoader")
    }

    @Test
    fun testFactoryHasNoArgConstructorForReflectiveInstantiation() {
        val instance = KtorKoogHttpClient.Factory::class.java.getDeclaredConstructor().newInstance()
        assertNotNull(instance)
    }

    @Test
    fun testInstallAsDefaultRegistersKtorFactory() {
        KtorKoogHttpClient.installAsDefault()
        val resolved = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory()
        assertTrue(
            resolved is KtorKoogHttpClient.Factory,
            "Expected installAsDefault to register a KtorKoogHttpClient.Factory"
        )
    }

    @Test
    fun testExplicitRegistrationTakesPrecedenceOverSpi() {
        val custom = object : KoogHttpClient.Factory {
            override fun create(
                clientName: String,
                baseUrl: String,
                headers: Map<String, String>,
                queryParameters: Map<String, String>,
                requestTimeoutMillis: Long,
                connectTimeoutMillis: Long,
                socketTimeoutMillis: Long,
                json: Json
            ): KoogHttpClient = error("custom does not create clients")
        }
        DefaultHttpClientFactoryHolder.setDefault(custom)
        assertSame(custom, DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory())
    }

    @Test
    fun testDiscoverabilityBridgeFromKoogHttpClientCompanion() {
        assertSame(DefaultHttpClientFactoryHolder, KoogHttpClient.defaultFactoryHolder)
    }
}
