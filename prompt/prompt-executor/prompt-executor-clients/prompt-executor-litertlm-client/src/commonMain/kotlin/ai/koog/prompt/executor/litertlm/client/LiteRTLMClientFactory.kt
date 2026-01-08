package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient
import kotlinx.datetime.Clock

/**
 * Configuration for creating a LiteRT-LM client.
 *
 * @param modelPath The absolute path to the LiteRT-LM model file (.litertlm format).
 * @param backend The compute backend to use for inference (CPU or GPU).
 * @param cacheDir Optional cache directory path to improve model load times.
 * @param defaultTopK Default top-K sampling parameter. Limits token selection to top N tokens.
 * @param defaultTopP Default top-P (nucleus) sampling parameter. Cumulative probability threshold.
 * @param defaultTemperature Default temperature for response randomness (lower = more deterministic).
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public data class LiteRTLMClientConfig(
    val modelPath: String,
    val backend: LiteRTLMBackend = LiteRTLMBackend.CPU,
    val cacheDir: String? = null,
    val defaultTopK: Int = 40,
    val defaultTopP: Float = 0.95f,
    val defaultTemperature: Float = 0.8f,
    val clock: Clock = Clock.System,
)

/**
 * Compute backend options for LiteRT-LM inference.
 */
public enum class LiteRTLMBackend {
    /** CPU-based inference */
    CPU,
    /** GPU-accelerated inference */
    GPU
}

/**
 * Creates a LiteRT-LM client for on-device LLM inference.
 *
 * LiteRT-LM is Google's on-device inference engine that enables running LLMs locally.
 * This is only supported on JVM and Android platforms.
 *
 * @param config Configuration for the LiteRT-LM client.
 * @return An [LLMClient] implementation for LiteRT-LM.
 * @throws UnsupportedOperationException on platforms where LiteRT-LM is not available (iOS, JS, WasmJS).
 */
public expect fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient

/**
 * Checks if LiteRT-LM is supported on the current platform.
 *
 * @return true if LiteRT-LM is available, false otherwise.
 */
public expect fun isLiteRTLMSupported(): Boolean
