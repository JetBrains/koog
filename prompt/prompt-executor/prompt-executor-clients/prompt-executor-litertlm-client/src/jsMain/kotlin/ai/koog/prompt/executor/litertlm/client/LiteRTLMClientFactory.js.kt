package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * LiteRT-LM is not available on JavaScript platforms.
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    throw UnsupportedOperationException(
        "LiteRT-LM is not supported on JavaScript. " +
        "LiteRT-LM only supports JVM and Android."
    )
}

/**
 * LiteRT-LM is not supported on JavaScript.
 */
public actual fun isLiteRTLMSupported(): Boolean = false
