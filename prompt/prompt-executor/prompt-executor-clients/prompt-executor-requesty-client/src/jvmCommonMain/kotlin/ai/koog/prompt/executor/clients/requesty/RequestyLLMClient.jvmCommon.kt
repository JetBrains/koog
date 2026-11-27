@file:JvmName("RequestyClientFactory")

package ai.koog.prompt.executor.clients.requesty

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [RequestyLLMClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [HttpClientFactoryResolver].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("requestyClient")
@JvmOverloads
public fun RequestyLLMClient(
    apiKey: String,
    settings: RequestyClientSettings = RequestyClientSettings(),
    clock: KoogClock = KoogClock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator(),
): RequestyLLMClient = RequestyLLMClient(
    apiKey = apiKey,
    settings = settings,
    httpClientFactory = HttpClientFactoryResolver.resolve(),
    clock = clock,
    toolsConverter = toolsConverter,
)
