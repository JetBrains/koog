package ai.koog.prompt.executor.litertlm.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for LiteRTLMClient configuration and validation.
 *
 * Note: Integration tests require the LiteRT-LM library and a valid model file.
 * See [LiteRTLMClientIntegrationTest] for tests that use an actual model.
 */
class LiteRTLMClientTest {

    @Test
    fun `LiteRTLMBackend enum has CPU, GPU, and NPU values`() {
        LiteRTLMBackend.CPU shouldBe LiteRTLMBackend.CPU
        LiteRTLMBackend.GPU shouldBe LiteRTLMBackend.GPU
        LiteRTLMBackend.NPU shouldBe LiteRTLMBackend.NPU
    }

    @Test
    fun `LiteRTLMEngineConfig validates maxNumTokens`() {
        // Null is valid (use default)
        LiteRTLMEngineConfig(modelPath = "/path/to/model", maxNumTokens = null)

        // Positive values are valid
        LiteRTLMEngineConfig(modelPath = "/path/to/model", maxNumTokens = 1024)
        LiteRTLMEngineConfig(modelPath = "/path/to/model", maxNumTokens = 1)

        // Zero and negative should throw
        shouldThrow<IllegalArgumentException> {
            LiteRTLMEngineConfig(modelPath = "/path/to/model", maxNumTokens = 0)
        }
        shouldThrow<IllegalArgumentException> {
            LiteRTLMEngineConfig(modelPath = "/path/to/model", maxNumTokens = -1)
        }
    }

    @Test
    fun `LiteRTLMSamplerConfig validates parameters`() {
        // Valid configuration
        LiteRTLMSamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)

        // topK must be positive
        shouldThrow<IllegalArgumentException> {
            LiteRTLMSamplerConfig(topK = 0)
        }
        shouldThrow<IllegalArgumentException> {
            LiteRTLMSamplerConfig(topK = -1)
        }

        // topP must be between 0 and 1
        shouldThrow<IllegalArgumentException> {
            LiteRTLMSamplerConfig(topP = -0.1)
        }
        shouldThrow<IllegalArgumentException> {
            LiteRTLMSamplerConfig(topP = 1.1)
        }

        // temperature must be non-negative
        shouldThrow<IllegalArgumentException> {
            LiteRTLMSamplerConfig(temperature = -0.1)
        }

        // Edge cases that should be valid
        LiteRTLMSamplerConfig(topP = 0.0)
        LiteRTLMSamplerConfig(topP = 1.0)
        LiteRTLMSamplerConfig(temperature = 0.0)
    }

    @Test
    fun `LiteRTLMClientConfig convenience constructor works`() {
        val config = LiteRTLMClientConfig(
            modelPath = "/path/to/model.litertlm",
            backend = LiteRTLMBackend.GPU,
            cacheDir = "/tmp/cache"
        )

        config.engineConfig.modelPath shouldBe "/path/to/model.litertlm"
        config.engineConfig.backend shouldBe LiteRTLMBackend.GPU
        config.engineConfig.cacheDir shouldBe "/tmp/cache"
        config.samplerConfig shouldBe null
    }

    @Test
    fun `LiteRTLMClientConfig full constructor works`() {
        val engineConfig = LiteRTLMEngineConfig(
            modelPath = "/path/to/model.litertlm",
            backend = LiteRTLMBackend.NPU,
            visionBackend = LiteRTLMBackend.GPU,
            audioBackend = LiteRTLMBackend.CPU,
            maxNumTokens = 8192,
            cacheDir = "/tmp/cache"
        )

        val samplerConfig = LiteRTLMSamplerConfig(
            topK = 50,
            topP = 0.9,
            temperature = 1.0,
            seed = 42
        )

        val config = LiteRTLMClientConfig(
            engineConfig = engineConfig,
            samplerConfig = samplerConfig
        )

        config.engineConfig.backend shouldBe LiteRTLMBackend.NPU
        config.engineConfig.visionBackend shouldBe LiteRTLMBackend.GPU
        config.engineConfig.audioBackend shouldBe LiteRTLMBackend.CPU
        config.engineConfig.maxNumTokens shouldBe 8192
        config.samplerConfig?.topK shouldBe 50
        config.samplerConfig?.seed shouldBe 42
    }

    @Test
    fun `LiteRTLMLogSeverity has all expected levels`() {
        LiteRTLMLogSeverity.VERBOSE
        LiteRTLMLogSeverity.DEBUG
        LiteRTLMLogSeverity.INFO
        LiteRTLMLogSeverity.WARNING
        LiteRTLMLogSeverity.ERROR
        LiteRTLMLogSeverity.FATAL
        LiteRTLMLogSeverity.SILENT
    }

    @Test
    fun `isLiteRTLMSupported returns true on JVM`() {
        isLiteRTLMSupported() shouldBe true
    }

    @Test
    fun `LiteRTLMSamplerConfig default values match official API`() {
        LiteRTLMSamplerConfig.DEFAULT_TOP_K shouldBe 40
        LiteRTLMSamplerConfig.DEFAULT_TOP_P shouldBe 0.95
        LiteRTLMSamplerConfig.DEFAULT_TEMPERATURE shouldBe 0.8
    }
}
