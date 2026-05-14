@file:JvmName("MistralAIClientFactories")

package ai.koog.prompt.executor.clients.mistralai

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [MistralAILLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [DefaultHttpClientFactoryHolder].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("mistralAIClient")
@JvmOverloads
public fun MistralAILLMClient(
    apiKey: String,
    settings: MistralAIClientSettings = MistralAIClientSettings(),
    clock: KoogClock = KoogClock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
): MistralAILLMClient = MistralAILLMClient(
    apiKey = apiKey,
    settings = settings,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
    clock = clock,
    toolsConverter = toolsConverter,
)
