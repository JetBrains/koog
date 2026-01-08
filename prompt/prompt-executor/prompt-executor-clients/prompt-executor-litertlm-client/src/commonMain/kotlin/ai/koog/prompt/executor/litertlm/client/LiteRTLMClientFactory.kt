package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.executor.clients.LLMClient
import kotlinx.datetime.Clock

/**
 * Backend for the LiteRT-LM engine.
 *
 * This is the Kotlin Multiplatform version of the C++'s `litert::lm::Backend`.
 */
public enum class LiteRTLMBackend {
    /** CPU LiteRT backend. */
    CPU,
    /** GPU LiteRT backend. */
    GPU,
    /** NPU LiteRT backend. */
    NPU,
}

/**
 * Configuration for the LiteRT-LM engine.
 *
 * @property modelPath The file path to the LiteRT-LM model.
 * @property backend The backend to use for the engine.
 * @property visionBackend The backend to use for the vision executor. If null, vision executor will
 *   not be initialized.
 * @property audioBackend The backend to use for the audio executor. If null, audio executor will
 *   not be initialized.
 * @property maxNumTokens The maximum number of the sum of input and output tokens. It is equivalent
 *   to the size of the kv-cache. When `null`, use the default value from the model or the engine.
 * @property cacheDir The directory for placing cache files. It should be a directory where the
 *   application has write access. If not set, it uses the directory of the [modelPath].
 */
public data class LiteRTLMEngineConfig(
    val modelPath: String,
    val backend: LiteRTLMBackend = LiteRTLMBackend.CPU,
    val visionBackend: LiteRTLMBackend? = null,
    val audioBackend: LiteRTLMBackend? = null,
    val maxNumTokens: Int? = null,
    val cacheDir: String? = null,
) {
    init {
        require(maxNumTokens == null || maxNumTokens > 0) {
            "maxNumTokens must be positive or null (use the default from model or engine)."
        }
    }
}

/**
 * Configuration for the sampling process.
 *
 * @property topK The number of top logits used during sampling.
 * @property topP The cumulative probability threshold for nucleus sampling.
 * @property temperature The temperature to use for sampling.
 * @property seed The seed to use for randomization. Default to 0 (same default as engine code).
 */
public data class LiteRTLMSamplerConfig(
    val topK: Int = DEFAULT_TOP_K,
    val topP: Double = DEFAULT_TOP_P,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val seed: Int = 0,
) {
    init {
        require(topK > 0) { "topK should be positive, but got $topK." }
        require(topP >= 0 && topP <= 1) { "topP should between 0 and 1 inclusively, but got $topP." }
        require(temperature >= 0) { "temperature should be non-negative, but got $temperature." }
    }

    public companion object {
        public const val DEFAULT_TOP_K: Int = 40
        public const val DEFAULT_TOP_P: Double = 0.95
        public const val DEFAULT_TEMPERATURE: Double = 0.8
    }
}

/**
 * Configuration for creating a LiteRT-LM client.
 *
 * @property engineConfig Configuration for the LiteRT-LM engine.
 * @property samplerConfig Configuration for the sampling process. If `null`, uses the engine's
 *   default values.
 * @property clock Clock instance used for tracking response metadata timestamps.
 */
public data class LiteRTLMClientConfig(
    val engineConfig: LiteRTLMEngineConfig,
    val samplerConfig: LiteRTLMSamplerConfig? = null,
    val clock: Clock = Clock.System,
) {
    /**
     * Convenience constructor for simple configurations.
     *
     * @param modelPath The file path to the LiteRT-LM model.
     * @param backend The backend to use for the engine.
     * @param cacheDir The directory for placing cache files.
     * @param clock Clock instance used for tracking response metadata timestamps.
     */
    public constructor(
        modelPath: String,
        backend: LiteRTLMBackend = LiteRTLMBackend.CPU,
        cacheDir: String? = null,
        clock: Clock = Clock.System,
    ) : this(
        engineConfig = LiteRTLMEngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir,
        ),
        samplerConfig = null,
        clock = clock,
    )
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
@MustUseReturnValue("The returned LLMClient must be used and eventually closed")
public expect fun createLiteRTLMClient(config: LiteRTLMClientConfig): LLMClient

/**
 * Checks if LiteRT-LM is supported on the current platform.
 *
 * @return true if LiteRT-LM is available, false otherwise.
 */
public expect fun isLiteRTLMSupported(): Boolean

/**
 * DSL builder for [LiteRTLMClientConfig].
 *
 * Example:
 * ```kotlin
 * val config = liteRTLMConfig {
 *     modelPath = "/path/to/model.litertlm"
 *     backend = LiteRTLMBackend.GPU
 *     sampler {
 *         temperature = 0.7
 *         topK = 50
 *     }
 * }
 * ```
 */
@DslMarker
public annotation class LiteRTLMConfigDsl

/**
 * Builder for [LiteRTLMClientConfig].
 */
@LiteRTLMConfigDsl
public class LiteRTLMClientConfigBuilder {
    /** Path to the LiteRT-LM model file. Required. */
    public var modelPath: String? = null

    /** Backend for the main inference engine. */
    public var backend: LiteRTLMBackend = LiteRTLMBackend.CPU

    /** Backend for vision processing, or null to disable. */
    public var visionBackend: LiteRTLMBackend? = null

    /** Backend for audio processing, or null to disable. */
    public var audioBackend: LiteRTLMBackend? = null

    /** Maximum number of tokens (input + output). */
    public var maxNumTokens: Int? = null

    /** Cache directory for model artifacts. */
    public var cacheDir: String? = null

    /** Clock for response timestamps. */
    public var clock: Clock = Clock.System

    private var samplerConfig: LiteRTLMSamplerConfig? = null

    /**
     * Configure sampling parameters.
     */
    public fun sampler(block: SamplerConfigBuilder.() -> Unit) {
        samplerConfig = SamplerConfigBuilder().apply(block).build()
    }

    /**
     * Builds the [LiteRTLMClientConfig].
     * @throws IllegalStateException if modelPath is not set.
     */
    public fun build(): LiteRTLMClientConfig {
        val path = requireNotNull(modelPath) { "modelPath must be set" }
        return LiteRTLMClientConfig(
            engineConfig = LiteRTLMEngineConfig(
                modelPath = path,
                backend = backend,
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                maxNumTokens = maxNumTokens,
                cacheDir = cacheDir,
            ),
            samplerConfig = samplerConfig,
            clock = clock,
        )
    }
}

/**
 * Builder for [LiteRTLMSamplerConfig].
 */
@LiteRTLMConfigDsl
public class SamplerConfigBuilder {
    public var topK: Int = LiteRTLMSamplerConfig.DEFAULT_TOP_K
    public var topP: Double = LiteRTLMSamplerConfig.DEFAULT_TOP_P
    public var temperature: Double = LiteRTLMSamplerConfig.DEFAULT_TEMPERATURE
    public var seed: Int = 0

    public fun build(): LiteRTLMSamplerConfig = LiteRTLMSamplerConfig(
        topK = topK,
        topP = topP,
        temperature = temperature,
        seed = seed,
    )
}

/**
 * Creates a [LiteRTLMClientConfig] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val config = liteRTLMConfig {
 *     modelPath = "/path/to/model.litertlm"
 *     backend = LiteRTLMBackend.GPU
 *     sampler {
 *         temperature = 0.7
 *     }
 * }
 * ```
 */
public fun liteRTLMConfig(block: LiteRTLMClientConfigBuilder.() -> Unit): LiteRTLMClientConfig {
    return LiteRTLMClientConfigBuilder().apply(block).build()
}

// ==================== Simple Helpers (Cactus-style) ====================

/**
 * Creates a simple [LiteRTLMClientConfig] with just a model path.
 *
 * This is the simplest way to get started - just provide the model file path.
 *
 * Example:
 * ```kotlin
 * val config = liteRTLMConfig("/path/to/model.litertlm")
 * ```
 *
 * @param modelPath Path to the LiteRT-LM model file.
 * @param backend Backend for inference (default: CPU).
 * @return A configured [LiteRTLMClientConfig].
 */
public fun liteRTLMConfig(
    modelPath: String,
    backend: LiteRTLMBackend = LiteRTLMBackend.CPU,
): LiteRTLMClientConfig = LiteRTLMClientConfig(modelPath = modelPath, backend = backend)

/**
 * Creates a [LiteRTLMSamplerConfig] with common parameters.
 *
 * Example:
 * ```kotlin
 * val sampler = samplerConfig(temperature = 0.7, topK = 50)
 * ```
 */
public fun samplerConfig(
    temperature: Double = LiteRTLMSamplerConfig.DEFAULT_TEMPERATURE,
    topK: Int = LiteRTLMSamplerConfig.DEFAULT_TOP_K,
    topP: Double = LiteRTLMSamplerConfig.DEFAULT_TOP_P,
    seed: Int = 0,
): LiteRTLMSamplerConfig = LiteRTLMSamplerConfig(
    topK = topK,
    topP = topP,
    temperature = temperature,
    seed = seed,
)
