package ai.koog.integration.tests

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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy

class OllamaTestFixture {
    private val PORT = 11434

    private lateinit var ollamaContainer: GenericContainer<*>

    lateinit var client: OllamaClient
    lateinit var executor: SingleLLMPromptExecutor
    val model = OllamaModels.Meta.LLAMA_3_2
    val visionModel = OllamaModels.Granite.GRANITE_3_2_VISION
    val moderationModel = OllamaModels.Meta.LLAMA_GUARD_3

    fun setUp() {
        ollamaContainer = GenericContainer(System.getenv("OLLAMA_IMAGE_URL")).apply {
            withExposedPorts(PORT)
            withImagePullPolicy(PullPolicy.alwaysPull())
        }
        ollamaContainer.start()

        val host = ollamaContainer.host
        val port = ollamaContainer.getMappedPort(PORT)
        val baseUrl = "http://$host:$port"
        waitForOllamaServer(baseUrl)

        client = OllamaClient(baseUrl)

        // Always pull the models to ensure they're available
        runBlocking {
            client.getModelOrNull(model.id, pullIfMissing = true)
            client.getModelOrNull(visionModel.id, pullIfMissing = true)
            client.getModelOrNull(moderationModel.id, pullIfMissing = true)
        }

        executor = SingleLLMPromptExecutor(client)
    }

    fun tearDown() {
        ollamaContainer.stop()
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
                            "Ollama server didn't respond after $maxAttempts attemps",
                            e
                        )
                    }
                }
                delay(1000)
            }
        }
    }
}
