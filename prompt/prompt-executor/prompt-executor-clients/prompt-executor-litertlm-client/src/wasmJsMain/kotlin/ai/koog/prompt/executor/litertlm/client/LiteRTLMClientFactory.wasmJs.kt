package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * LiteRT-LM is not available on WasmJS platform.
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    throw UnsupportedOperationException(
        "LiteRT-LM is not supported on WasmJS. " +
        "LiteRT-LM only supports JVM and Android."
    )
}

/**
 * LiteRT-LM is not supported on WasmJS.
 */
public actual fun isLiteRTLMSupported(): Boolean = false
