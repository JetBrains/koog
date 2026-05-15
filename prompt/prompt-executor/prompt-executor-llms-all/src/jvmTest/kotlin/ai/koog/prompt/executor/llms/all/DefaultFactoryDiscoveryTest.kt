package ai.koog.prompt.executor.llms.all

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.http.client.ktor.KtorKoogHttpClient
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that a consumer depending only on `:prompt-executor-llms-all` resolves
 * [KtorKoogHttpClient.Factory] as the default `KoogHttpClient.Factory` at runtime.
 *
 * The `:http-client-ktor` module is wired as `runtimeOnly` in this module's `jvmCommonMain`
 * specifically so downstream consumers get a working SPI provider without a compile-time
 * Ktor dependency. The provider-side discovery is already covered by
 * `KtorKoogHttpClientSpiTest` in `:http-client-ktor:jvmTest`; this test pins the consumer-side
 * wiring so a regression in `runtimeOnly` packaging fails here instead of in user code.
 */
class DefaultFactoryDiscoveryTest {

    @Test
    fun testDefaultFactoryIsKtorBacked() {
        val resolved = HttpClientFactoryResolver.resolve()
        assertTrue(
            resolved is KtorKoogHttpClient.Factory,
            "Expected the default KoogHttpClient.Factory resolved from :prompt-executor-llms-all " +
                "to be KtorKoogHttpClient.Factory (got ${resolved.javaClass.name})"
        )
    }
}
