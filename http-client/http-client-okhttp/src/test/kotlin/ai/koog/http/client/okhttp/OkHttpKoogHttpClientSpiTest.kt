package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClient
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OkHttpKoogHttpClientSpiTest {

    @Test
    fun testServiceLoaderDiscoversOkHttpFactory() {
        val providers = ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
        val okHttpFactory = providers.singleOrNull { it is OkHttpKoogHttpClient.Factory }
        assertNotNull(
            okHttpFactory,
            "Expected OkHttpKoogHttpClient.Factory to be discoverable via ServiceLoader"
        )
    }

    @Test
    fun testFactoryHasNoArgConstructorForReflectiveInstantiation() {
        val instance = OkHttpKoogHttpClient.Factory::class.java.getDeclaredConstructor().newInstance()
        assertTrue(
            instance is KoogHttpClient.Factory,
            "Expected reflective no-arg instantiation to yield a KoogHttpClient.Factory, got ${instance::class}"
        )
    }
}
