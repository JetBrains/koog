package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * Creates a LiteRT-LM client for JVM platform.
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    return LiteRTLMClient(config)
}

/**
 * LiteRT-LM is supported on JVM.
 */
public actual fun isLiteRTLMSupported(): Boolean = true
