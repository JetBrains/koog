@file:JvmName("GoogleClientFactory")

package ai.koog.prompt.executor.clients.google

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [GoogleLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [HttpClientFactoryResolver].
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
    httpClientFactory = HttpClientFactoryResolver.resolve(),
    clock = clock,
)
