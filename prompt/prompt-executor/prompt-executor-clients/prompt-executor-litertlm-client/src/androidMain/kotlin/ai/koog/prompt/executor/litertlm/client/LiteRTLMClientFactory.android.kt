package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient

/**
 * Creates a LiteRT-LM client for Android platform.
 *
 * Note: Android support requires the litertlm-android dependency and native libraries.
 * This is a placeholder - full Android implementation coming soon.
 */
public actual fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient {
    throw UnsupportedOperationException(
        "LiteRT-LM Android support requires litertlm-android dependency. " +
            "Add 'com.google.ai.edge.litertlm:litertlm-android' to your dependencies."
    )
}

/**
 * LiteRT-LM is supported on Android (requires additional setup).
 */
public actual fun isLiteRTLMSupported(): Boolean = true
