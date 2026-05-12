@file:OptIn(ExperimentalAtomicApi::class)

package ai.koog.http.client

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Holds the explicitly set default [KoogHttpClient.Factory] for the process, with runtime
 * classpath discovery as a fallback when nothing has been set.
 *
 * Intended usage: call [setDefault] once during application bootstrap. Subsequent calls
 * overwrite the previous value (last write wins). There is no operation that returns the
 * holder to an unset state once a factory has been set.
 *
 * Writes are atomic so that concurrent bootstrap paths cannot leave the holder in a
 * partially-initialized state.
 */
@Experimental
public object DefaultHttpClientFactoryHolder {

    private val explicit = AtomicReference<KoogHttpClient.Factory?>(null)

    /**
     * Atomically sets the default [KoogHttpClient.Factory]. Last write wins; typical apps call
     * this once at startup, but calling it again is not an error.
     *
     * There is no operation to return the holder to an unset state once [setDefault] has been
     * called. Avoid calling it if you want classpath discovery to remain the source of truth.
     */
    public fun setDefault(factory: KoogHttpClient.Factory) {
        explicit.store(factory)
    }

    /**
     * Returns the default [KoogHttpClient.Factory] — the explicitly set one, or, if none was
     * set, the single provider discovered on the runtime classpath.
     *
     * Throws [IllegalStateException] when zero or more than one classpath provider is found
     * and nothing has been set explicitly.
     */
    public fun getDefaultHttpClientFactory(): KoogHttpClient.Factory =
        explicit.load() ?: resolveFactoryFromProviders(loadKoogHttpClientFactories())

    internal fun resolveFactoryFromProviders(
        providers: List<KoogHttpClient.Factory>
    ): KoogHttpClient.Factory =
        when (providers.size) {
            0 -> throw IllegalStateException(
                "No KoogHttpClient.Factory provider found. " +
                        "Call DefaultHttpClientFactoryHolder.setDefault(...) at application startup, " +
                        "or add a module that publishes a Factory provider to the runtime classpath."
            )
            1 -> providers.single()
            else -> throw IllegalStateException(
                "Multiple KoogHttpClient.Factory providers found on the classpath: " +
                        providers.joinToString { it::class.simpleName ?: "<anonymous>" } +
                        ". Call DefaultHttpClientFactoryHolder.setDefault(...) at startup to select one explicitly."
            )
        }
}

/**
 * Discoverability bridge from [KoogHttpClient]'s companion to [DefaultHttpClientFactoryHolder].
 *
 * Lets consumers reach the holder through the contract type they already know:
 * `KoogHttpClient.defaultFactoryHolder.install(myFactory)`.
 */
public val KoogHttpClient.Companion.defaultFactoryHolder: DefaultHttpClientFactoryHolder
    @Experimental
    get() = DefaultHttpClientFactoryHolder

/**
 * Loads [KoogHttpClient.Factory] providers available on the runtime classpath.
 *
 * Each target chooses an appropriate discovery mechanism. Targets without a runtime
 * discovery mechanism return an empty list, in which case consumers must register a
 * factory explicitly via [DefaultHttpClientFactoryHolder.setDefault].
 */
internal expect fun loadKoogHttpClientFactories(): List<KoogHttpClient.Factory>
