@file:JvmName("GoogleClientFactories")

package ai.koog.prompt.executor.clients.google

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [GoogleLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [DefaultHttpClientFactoryHolder].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("googleClient")
@JvmOverloads
public fun GoogleLLMClient(
    apiKey: String,
    settings: GoogleClientSettings = GoogleClientSettings(),
    clock: KoogClock = KoogClock.System,
): GoogleLLMClient = GoogleLLMClient(
    apiKey = apiKey,
    settings = settings,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
    clock = clock,
)
