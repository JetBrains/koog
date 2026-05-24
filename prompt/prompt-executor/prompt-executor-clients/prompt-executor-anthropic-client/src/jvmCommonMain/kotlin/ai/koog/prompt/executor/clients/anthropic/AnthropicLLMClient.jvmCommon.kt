@file:JvmName("AnthropicClientFactory")

package ai.koog.prompt.executor.clients.anthropic

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [AnthropicLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [HttpClientFactoryResolver].
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
    httpClientFactory = HttpClientFactoryResolver.resolve(),
    clock = clock,
)
