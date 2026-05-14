@file:JvmName("DeepSeekClientFactories")

package ai.koog.prompt.executor.clients.deepseek

import ai.koog.http.client.DefaultHttpClientFactoryHolder
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [DeepSeekLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [DefaultHttpClientFactoryHolder].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("deepSeekClient")
@JvmOverloads
public fun DeepSeekLLMClient(
    apiKey: String,
    settings: DeepSeekClientSettings = DeepSeekClientSettings(),
    clock: KoogClock = KoogClock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
): DeepSeekLLMClient = DeepSeekLLMClient(
    apiKey = apiKey,
    settings = settings,
    httpClientFactory = DefaultHttpClientFactoryHolder.getDefaultHttpClientFactory(),
    clock = clock,
    toolsConverter = toolsConverter,
)
