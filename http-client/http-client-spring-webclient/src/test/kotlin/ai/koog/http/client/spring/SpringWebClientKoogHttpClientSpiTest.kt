package ai.koog.http.client.spring

import ai.koog.http.client.KoogHttpClient
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpringWebClientKoogHttpClientSpiTest {

    @Test
    fun testServiceLoaderDiscoversSpringFactory() {
        val providers = ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
        val springFactory = providers.singleOrNull { it is SpringWebClientKoogHttpClient.Factory }
        assertNotNull(
            springFactory,
            "Expected SpringWebClientKoogHttpClient.Factory to be discoverable via ServiceLoader"
        )
    }

    @Test
    fun testFactoryHasNoArgConstructorForReflectiveInstantiation() {
        val instance = SpringWebClientKoogHttpClient.Factory::class.java.getDeclaredConstructor().newInstance()
        assertTrue(
            instance is KoogHttpClient.Factory,
            "Expected reflective no-arg instantiation to yield a KoogHttpClient.Factory, got ${instance::class}"
        )
    }
}
