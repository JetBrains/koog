package ai.koog.http.client

import java.util.ServiceLoader

/**
 * Resolver for the default [KoogHttpClient.Factory] discovered on the JVM runtime
 * classpath via [ServiceLoader].
 *
 * For customization (custom HTTP engine, non-default timeouts, etc.) or to disambiguate when
 * multiple providers are on the classpath, pass a [KoogHttpClient.Factory] directly to the
 * relevant LLM client constructor or builder method instead of relying on the discovered default.
 */
public object HttpClientFactoryResolver {

    private val cached: KoogHttpClient.Factory by lazy {
        resolveFactoryFromProviders(loadKoogHttpClientFactories())
    }

    /**
     * Returns the single [KoogHttpClient.Factory] discovered on the runtime classpath via
     * [ServiceLoader]. The resolved factory is cached on first successful access; the
     * `ServiceLoader` lookup and reflective instantiation are paid once per process.
     *
     * Throws [IllegalStateException] when zero or more than one provider is found.
     */
    public fun resolve(): KoogHttpClient.Factory = cached

    internal fun resolveFactoryFromProviders(
        providers: List<KoogHttpClient.Factory>
    ): KoogHttpClient.Factory =
        when (providers.size) {
            0 -> error(
                "No KoogHttpClient.Factory provider found on the runtime classpath. " +
                    "Add a module that publishes a Factory provider (e.g. http-client-ktor) " +
                    "to the runtime classpath, or pass a KoogHttpClient.Factory explicitly."
            )

            1 -> providers.single()

            else -> error(
                "Multiple KoogHttpClient.Factory providers found on the classpath: " +
                    providers.joinToString { it.javaClass.name } +
                    ". Exclude all but one provider module from your build, or pass a " +
                    "KoogHttpClient.Factory explicitly at the call site."
            )
        }

    private fun loadKoogHttpClientFactories(): List<KoogHttpClient.Factory> =
        ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
}
