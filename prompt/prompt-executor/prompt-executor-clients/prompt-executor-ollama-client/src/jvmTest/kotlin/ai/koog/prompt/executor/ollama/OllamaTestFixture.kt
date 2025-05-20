package ai.koog.prompt.executor.ollama

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.OllamaModels
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy

class OllamaTestFixture : AutoCloseable {
    private val PORT = 11434

    private val container: GenericContainer<*> by lazy { startContainer() }

    private val baseUrl: String by lazy {
        val host = container.host
        val port = container.getMappedPort(PORT)
        "http://$host:$port"
    }

    val executor: SingleLLMPromptExecutor by lazy {
        waitForOllamaServer(baseUrl)
        SingleLLMPromptExecutor(OllamaClient(baseUrl))
    }

    val model = OllamaModels.Meta.LLAMA_3_2

    override fun close() {
        container.stop()
    }

    private fun startContainer(): GenericContainer<*> {
        return GenericContainer(System.getenv("OLLAMA_IMAGE_URL")).apply {
            withExposedPorts(PORT)
            withImagePullPolicy(PullPolicy.alwaysPull())
        }.apply { start() }
    }

    private fun waitForOllamaServer(baseUrl: String) {
        val httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 1000
            }
        }

        val maxAttempts = 100

        runBlocking {
            for (attempt in 1..maxAttempts) {
                try {
                    val response = httpClient.get(baseUrl)
                    if (response.status.isSuccess()) {
                        httpClient.close()
                        return@runBlocking
                    }
                } catch (e: Exception) {
                    if (attempt == maxAttempts) {
                        httpClient.close()
                        throw IllegalStateException(
                            "Ollama server didn't respond after $maxAttempts attemps", e
                        )
                    }
                }
                delay(1000)
            }
        }
    }
}