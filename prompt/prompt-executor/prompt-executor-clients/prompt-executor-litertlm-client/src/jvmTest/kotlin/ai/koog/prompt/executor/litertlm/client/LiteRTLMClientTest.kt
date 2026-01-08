package ai.koog.prompt.executor.litertlm.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LiteRTLMModels
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for LiteRTLMClient.
 *
 * Note: Integration tests require the LiteRT-LM library and a valid model file.
 * These tests focus on configuration and error handling.
 */
class LiteRTLMClientTest {

    @Test
    fun `llmProvider returns LiteRTLM`() {
        val client = LiteRTLMClient(modelPath = "/path/to/model.litertlm")
        client.llmProvider() shouldBe LLMProvider.LiteRTLM
    }

    @Test
    fun `execute throws exception when not initialized`() = runTest {
        val client = LiteRTLMClient(modelPath = "/path/to/model.litertlm")

        val prompt = prompt {
            user("Hello")
        }

        val exception = shouldThrow<LLMClientException> {
            client.execute(prompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
        }

        exception.message shouldContain "not initialized"
    }

    @Test
    fun `execute throws exception for wrong provider`() = runTest {
        val client = LiteRTLMClient(modelPath = "/path/to/model.litertlm")

        val prompt = prompt {
            user("Hello")
        }

        // Create a model with wrong provider
        val wrongProviderModel = LiteRTLMModels.Google.GEMMA_3N_E4B.copy(
            provider = LLMProvider.Ollama
        )

        shouldThrow<IllegalArgumentException> {
            client.execute(prompt, wrongProviderModel)
        }
    }

    @Test
    fun `Backend enum has CPU and GPU values`() {
        LiteRTLMClient.Backend.CPU shouldBe LiteRTLMClient.Backend.CPU
        LiteRTLMClient.Backend.GPU shouldBe LiteRTLMClient.Backend.GPU
    }

    @Test
    fun `client can be created with custom backend`() {
        val cpuClient = LiteRTLMClient(
            modelPath = "/path/to/model.litertlm",
            backend = LiteRTLMClient.Backend.CPU
        )
        cpuClient.llmProvider() shouldBe LLMProvider.LiteRTLM

        val gpuClient = LiteRTLMClient(
            modelPath = "/path/to/model.litertlm",
            backend = LiteRTLMClient.Backend.GPU
        )
        gpuClient.llmProvider() shouldBe LLMProvider.LiteRTLM
    }

    @Test
    fun `client can be created with cache directory`() {
        val client = LiteRTLMClient(
            modelPath = "/path/to/model.litertlm",
            cacheDir = "/tmp/cache"
        )
        client.llmProvider() shouldBe LLMProvider.LiteRTLM
    }

    @Test
    fun `moderation throws unsupported exception`() = runTest {
        val client = LiteRTLMClient(modelPath = "/path/to/model.litertlm")

        val prompt = prompt {
            user("Test content")
        }

        val exception = shouldThrow<LLMClientException> {
            client.moderate(prompt, LiteRTLMModels.Google.GEMMA_3N_E4B)
        }

        exception.message shouldContain "not supported"
    }

    @Test
    fun `close can be called safely on uninitialized client`() {
        val client = LiteRTLMClient(modelPath = "/path/to/model.litertlm")
        // Should not throw
        client.close()
    }
}
