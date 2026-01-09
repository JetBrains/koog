package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LiteRTLMModels
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Integration tests for LiteRTLMClient.
 *
 * These tests require:
 * 1. The LiteRT-LM library to be available on the classpath
 * 2. A valid model file (e.g., gemma-3n-e4b.litertlm)
 *
 * ## Running these tests
 *
 * 1. Download a compatible model from https://github.com/google-ai-edge/LiteRT-LM
 * 2. Set the `LITERTLM_MODEL_PATH` environment variable to the model file location
 * 3. Remove the `@Disabled` annotation from the test class
 *
 * Example:
 * ```bash
 * LITERTLM_MODEL_PATH=/path/to/gemma-3n-e4b.litertlm \
 *   ./gradlew :prompt:prompt-executor:prompt-executor-clients:prompt-executor-litertlm-client:jvmTest \
 *   --tests "*.LiteRTLMClientIntegrationTest"
 * ```
 */
class LiteRTLMClientIntegrationTest {

    private val modelPath: String by lazy {
        System.getenv("LITERTLM_MODEL_PATH")
            ?: throw IllegalStateException(
                "LITERTLM_MODEL_PATH environment variable not set. " +
                    "Please set it to the path of your .litertlm model file."
            )
    }

    private var client: LiteRTLMClient? = null

    @BeforeEach
    fun setup() {
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }

        // Silence native logging for cleaner test output
        LiteRTLMClient.setLogSeverity(LiteRTLMLogSeverity.ERROR)
    }

    @AfterEach
    fun teardown() {
        client?.close()
        client = null
    }

    @Test
    fun `can initialize client with factory method`() = runTest {
        val config = LiteRTLMClientConfig(
            modelPath = modelPath,
            backend = LiteRTLMBackend.CPU
        )

        client = LiteRTLMClient.create(config)

        // Client should be ready to use immediately after create()
        assert(!client!!.isClosed()) { "Client should not be closed after creation" }
    }

    @Test
    fun `can execute simple prompt`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        val testPrompt = prompt("simple-prompt") {
            system("You are a helpful assistant. Keep responses brief.")
            user("What is 2 + 2?")
        }

        val responses = client!!.execute(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)

        assert(responses.isNotEmpty()) { "Expected at least one response" }
        println("Response: ${responses.first().content}")
    }

    @Test
    fun `can stream responses`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        val testPrompt = prompt("streaming-prompt") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val frames = client!!.executeStreaming(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
            .toList()

        assert(frames.isNotEmpty()) { "Expected streaming frames" }
        println("Received ${frames.size} streaming frames")
    }

    @Test
    fun `can use managed conversation for multi-turn chat`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        client!!.conversation(systemPrompt = "You are a helpful assistant.").use { conv ->
            // First turn
            val greeting = conv.send("Hello! My name is Alice.")
            println("Assistant: ${greeting.content}")

            // Second turn - should remember context
            val followUp = conv.send("What is my name?")
            println("Assistant: ${followUp.content}")

            // The response should mention "Alice" since we're in a conversation
            assert(followUp.content.contains("Alice", ignoreCase = true)) {
                "Expected response to remember the name 'Alice' from conversation context"
            }
        }
    }

    @Test
    fun `can stream responses in managed conversation`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        client!!.conversation(systemPrompt = "You are a helpful assistant.").use { conv ->
            val chunks = mutableListOf<String>()

            conv.sendStreaming("Tell me a very short joke.")
                .collect { chunk ->
                    chunks.add(chunk)
                    print(chunk) // Stream to console
                }
            println() // newline after streaming

            assert(chunks.isNotEmpty()) { "Expected streaming chunks" }
        }
    }

    @Test
    fun `can use GPU backend`() = runTest {
        val config = LiteRTLMClientConfig(
            modelPath = modelPath,
            backend = LiteRTLMBackend.GPU
        )

        client = LiteRTLMClient.create(config)

        val testPrompt = prompt("gpu-backend-prompt") {
            user("Hello!")
        }

        val responses = client!!.execute(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
        assert(responses.isNotEmpty())
    }

    @Test
    fun `can configure custom sampler settings`() = runTest {
        val config = LiteRTLMClientConfig(
            engineConfig = LiteRTLMEngineConfig(
                modelPath = modelPath,
                backend = LiteRTLMBackend.CPU,
            ),
            samplerConfig = LiteRTLMSamplerConfig(
                topK = 20, // More focused sampling
                topP = 0.8, // Narrower nucleus
                temperature = 0.5, // More deterministic
                seed = 42, // Reproducible results
            )
        )

        client = LiteRTLMClient.create(config)

        val testPrompt = prompt("sampler-settings-prompt") {
            user("What color is the sky?")
        }

        val responses = client!!.execute(testPrompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
        assert(responses.isNotEmpty())
        println("Response with custom sampler: ${responses.first().content}")
    }

    @Test
    fun `client can be closed and reports closed state`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        assert(!client!!.isClosed()) { "Client should not be closed initially" }

        client!!.close()

        assert(client!!.isClosed()) { "Client should be closed after close()" }
    }

    @Test
    fun `conversation can be cancelled`() = runTest {
        val config = LiteRTLMClientConfig(modelPath = modelPath)
        client = LiteRTLMClient.create(config)

        client!!.conversation().use { conv ->
            // Start a potentially long generation
            val job = launch {
                try {
                    conv.sendStreaming("Write a 1000 word essay about the history of computing.")
                        .collect { /* consume */ }
                } catch (e: Exception) {
                    // Expected if cancelled
                }
            }

            // Give it a moment to start
            kotlinx.coroutines.delay(100)

            // Cancel the inference
            conv.cancel()

            // Wait for the job to complete (should be quick after cancel)
            job.join()
        }
    }
}
