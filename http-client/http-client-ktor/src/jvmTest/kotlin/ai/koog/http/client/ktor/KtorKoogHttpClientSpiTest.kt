package ai.koog.http.client.ktor

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.http.client.KoogHttpClient
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun testHolderResolvesKtorFactoryFromServiceLoader() {
        val resolved = HttpClientFactoryResolver.resolve()
        assertTrue(
            resolved is KtorKoogHttpClient.Factory,
            "Expected the holder to resolve KtorKoogHttpClient.Factory via ServiceLoader"
        )
    }
}
