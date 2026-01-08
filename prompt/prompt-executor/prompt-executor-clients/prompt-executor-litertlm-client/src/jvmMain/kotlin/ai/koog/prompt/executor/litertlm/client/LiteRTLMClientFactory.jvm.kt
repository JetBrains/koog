package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * Creates a LiteRT-LM client for JVM platform.
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    return LiteRTLMClient(
        modelPath = config.modelPath,
        backend = when (config.backend) {
            LiteRTLMBackend.CPU -> LiteRTLMClient.Backend.CPU
            LiteRTLMBackend.GPU -> LiteRTLMClient.Backend.GPU
        },
        cacheDir = config.cacheDir,
        defaultTopK = config.defaultTopK,
        defaultTopP = config.defaultTopP,
        defaultTemperature = config.defaultTemperature,
        clock = config.clock,
    )
}

/**
 * LiteRT-LM is supported on JVM.
 */
public actual fun isLiteRTLMSupported(): Boolean = true
