package ai.koog.integration.tests

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.OllamaModels
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName

class OllamaTestFixture {
    private val PORT = 11434

    private lateinit var ollamaContainer: GenericContainer<*>

    lateinit var client: OllamaClient
    lateinit var executor: SingleLLMPromptExecutor
    val model = OllamaModels.Meta.LLAMA_3_2
    val visionModel = OllamaModels.Granite.GRANITE_3_2_VISION
    val moderationModel = OllamaModels.Meta.LLAMA_GUARD_3

    fun setUp() {
        val imageUrl = System.getenv("OLLAMA_IMAGE_URL")
            ?: throw IllegalStateException("OLLAMA_IMAGE_URL not set")

        ollamaContainer = GenericContainer(DockerImageName.parse(imageUrl)).apply {
            withExposedPorts(PORT)
            withImagePullPolicy(PullPolicy.alwaysPull())
            withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig?.apply {
                    withMemory(4L * 1024 * 1024 * 1024) // 4GB RAM
                    withCpuCount(2L)
                }
            }
            withReuse(false)
        }

        try {
            ollamaContainer.start()

            val host = ollamaContainer.host
            val port = ollamaContainer.getMappedPort(PORT)
            val baseUrl = "http://$host:$port"
            waitForOllamaServer(baseUrl)

            client = OllamaClient(baseUrl)

            // Always pull the models to ensure they're available
            runBlocking {
                try {
                    client.getModelOrNull(model.id, pullIfMissing = true)
                    client.getModelOrNull(visionModel.id, pullIfMissing = true)
                    client.getModelOrNull(moderationModel.id, pullIfMissing = true)
                } catch (e: Exception) {
                    println("Failed to pull models: ${e.message}")
                    cleanup()
                    throw e
                }
            }

            executor = SingleLLMPromptExecutor(client)
        } catch (e: Exception) {
            cleanup()
            throw e
        }
    }

    fun tearDown() {
        cleanup()
    }

    private fun cleanup() {
        try {
            if (::ollamaContainer.isInitialized) {
                try {
                    ollamaContainer.stop()
                } catch (e: Exception) {
                    println("Error stopping container: ${e.message}")
                }

                try {
                    ollamaContainer.dockerClient?.removeContainerCmd(ollamaContainer.containerId)
                        ?.withRemoveVolumes(true)
                        ?.withForce(true)
                        ?.exec()
                } catch (e: Exception) {
                    println("Error removing container: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Error during cleanup: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun waitForOllamaServer(baseUrl: String) {
        val httpClient = HttpClient {
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
