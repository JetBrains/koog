@file:JvmName("OllamaClientFactory")

package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * JVM convenience for constructing [OllamaClient] without an explicit
 * [ai.koog.http.client.KoogHttpClient.Factory]: the default factory is resolved at call time from
 * [HttpClientFactoryResolver].
 *
 * Non-JVM targets must use the primary constructor and pass a factory explicitly.
 */
@JvmName("ollamaClient")
@JvmOverloads
public fun OllamaClient(
    baseUrl: String = OllamaClient.DEFAULT_BASE_URL,
    headers: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    clock: KoogClock = KoogClock.System,
    contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategy.Companion.None,
    toolDescriptorConverter: ToolDescriptorSchemaGenerator = OllamaToolDescriptorSchemaGenerator(),
): OllamaClient = OllamaClient(
    httpClientFactory = HttpClientFactoryResolver.resolve(),
    baseUrl = baseUrl,
    headers = headers,
    queryParameters = queryParameters,
    timeoutConfig = timeoutConfig,
    clock = clock,
    contextWindowStrategy = contextWindowStrategy,
    toolDescriptorConverter = toolDescriptorConverter,
)
