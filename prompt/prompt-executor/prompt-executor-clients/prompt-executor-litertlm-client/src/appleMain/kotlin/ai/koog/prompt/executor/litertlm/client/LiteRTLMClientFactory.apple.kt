package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * LiteRT-LM is not available on Apple platforms (iOS/macOS).
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    throw UnsupportedOperationException(
        "LiteRT-LM is not supported on Apple platforms. " +
        "LiteRT-LM only supports JVM and Android."
    )
}

/**
 * LiteRT-LM is not supported on Apple platforms.
 */
public actual fun isLiteRTLMSupported(): Boolean = false
