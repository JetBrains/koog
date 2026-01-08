package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LiteRTLMModels
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Integration tests for LiteRTLMClient.
 *
 * These tests require:
 * 1. The LiteRT-LM library to be available
 * 2. A valid model file (e.g., gemma-3n-e4b.litertlm)
 *
 * To run these tests:
 * 1. Download a compatible model from https://github.com/google-ai-edge/LiteRT-LM
 * 2. Set the MODEL_PATH environment variable to the model file location
 * 3. Remove the @Disabled annotation
 *
 * Example:
 * ```bash
 * MODEL_PATH=/path/to/gemma-3n-e4b.litertlm ./gradlew :prompt:prompt-executor:prompt-executor-clients:prompt-executor-litertlm-client:jvmTest
 * ```
 */
@Disabled("Requires LiteRT-LM library and model file")
class LiteRTLMClientIntegrationTest {

    private val modelPath: String by lazy {
        System.getenv("MODEL_PATH")
            ?: throw IllegalStateException("MODEL_PATH environment variable not set")
    }

    @Test
    fun `can initialize and execute simple prompt`() = runTest {
        // Verify model file exists
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }

        val client = LiteRTLMClient(
            modelPath = modelPath,
            backend = LiteRTLMClient.Backend.CPU
        )

        try {
            // Initialize the engine (this can take several seconds)
            client.initialize()

            // Create a simple prompt
            val testPrompt = prompt {
                system("You are a helpful assistant. Keep responses brief.")
                user("What is 2 + 2?")
            }

            // Execute the prompt
            val responses = client.execute(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)

            // Verify we got a response
            assert(responses.isNotEmpty()) { "Expected at least one response" }
            println("Response: ${responses.first()}")
        } finally {
            client.close()
        }
    }

    @Test
    fun `can stream responses`() = runTest {
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }

        val client = LiteRTLMClient(
            modelPath = modelPath,
            backend = LiteRTLMClient.Backend.CPU
        )

        try {
            client.initialize()

            val testPrompt = prompt {
                system("You are a helpful assistant.")
                user("Count from 1 to 5.")
            }

            // Collect streaming response
            val frames = client.executeStreaming(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
                .toList()

            assert(frames.isNotEmpty()) { "Expected streaming frames" }
            println("Received ${frames.size} frames")
        } finally {
            client.close()
        }
    }

    @Test
    fun `can use GPU backend`() = runTest {
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }

        val client = LiteRTLMClient(
            modelPath = modelPath,
            backend = LiteRTLMClient.Backend.GPU
        )

        try {
            client.initialize()

            val testPrompt = prompt {
                user("Hello!")
            }

            val responses = client.execute(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
            assert(responses.isNotEmpty())
        } finally {
            client.close()
        }
    }
}
