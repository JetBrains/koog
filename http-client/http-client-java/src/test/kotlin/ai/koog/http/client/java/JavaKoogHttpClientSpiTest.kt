package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaKoogHttpClientSpiTest {

    @Test
    fun testServiceLoaderDiscoversJavaFactory() {
        val providers = ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
        val javaFactory = providers.singleOrNull { it is JavaKoogHttpClient.Factory }
        assertNotNull(
            javaFactory,
            "Expected JavaKoogHttpClient.Factory to be discoverable via ServiceLoader"
        )
    }

    @Test
    fun testFactoryHasNoArgConstructorForReflectiveInstantiation() {
        val instance = JavaKoogHttpClient.Factory::class.java.getDeclaredConstructor().newInstance()
        assertTrue(
            instance is KoogHttpClient.Factory,
            "Expected reflective no-arg instantiation to yield a KoogHttpClient.Factory, got ${instance::class}"
        )
    }
}
