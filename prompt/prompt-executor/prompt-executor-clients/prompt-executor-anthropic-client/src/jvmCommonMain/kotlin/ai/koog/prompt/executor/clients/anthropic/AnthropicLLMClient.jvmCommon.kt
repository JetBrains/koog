@file:JvmName("AnthropicClientFactories")

package ai.koog.prompt.executor.clients.anthropic

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [AnthropicLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [DefaultHttpClientFactoryHolder].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("anthropicClient")
@JvmOverloads
public fun AnthropicLLMClient(
    apiKey: String,
    settings: AnthropicClientSettings = AnthropicClientSettings(),
    clock: KoogClock = KoogClock.System,
): AnthropicLLMClient = AnthropicLLMClient(
    apiKey = apiKey,
    settings = settings,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
    clock = clock,
)
