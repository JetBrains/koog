package ai.koog.prompt.executor.ollama

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.OllamaModels
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy

object OllamaTestFixture {
    const val PORT = 11434
    private val ollamaContainer =
        GenericContainer("registry.jetbrains.team/p/grazi/grazie-infra-public/koog-ollama:1.9").apply {
            withExposedPorts(PORT)
            withImagePullPolicy(PullPolicy.alwaysPull())
        }

    lateinit var baseUrl: String
    lateinit var client: OllamaClient
    lateinit var executor: SingleLLMPromptExecutor
    val model = OllamaModels.Meta.LLAMA_3_2

    @JvmStatic
    @BeforeAll
    fun setUp() {
        ollamaContainer.start()
        val host = ollamaContainer.host
        val port = ollamaContainer.getMappedPort(PORT)
        baseUrl = "http://$host:$port"
        waitForOllamaServer()

        client = OllamaClient(baseUrl)
        executor = SingleLLMPromptExecutor(client)
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
        ollamaContainer.stop()
    }

    private fun waitForOllamaServer() {
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