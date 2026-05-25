package ai.koog.http.client.spring

import ai.koog.http.client.KoogHttpClient
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpringKoogHttpClientSpiTest {

    @Test
    fun testServiceLoaderDiscoversSpringFactory() {
        val providers = ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
        val springFactory = providers.singleOrNull { it is SpringKoogHttpClient.Factory }
        assertNotNull(
            springFactory,
            "Expected SpringKoogHttpClient.Factory to be discoverable via ServiceLoader"
        )
    }

    @Test
    fun testFactoryHasNoArgConstructorForReflectiveInstantiation() {
        val instance = SpringKoogHttpClient.Factory::class.java.getDeclaredConstructor().newInstance()
        assertTrue(
            instance is KoogHttpClient.Factory,
            "Expected reflective no-arg instantiation to yield a KoogHttpClient.Factory, got ${instance::class}"
        )
    }
}
