package ai.koog.integration.tests

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Volume
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.Collections

/**
 * Utility class used for setting up and tearing down resources needed for testing
 * with the Ollama service, either running locally or within a Docker container.
 *
 * This fixture ensures:
 * - Connection to a locally running Ollama service, if a URL is provided via environment variable.
 * - Setup of a Docker container running Ollama, including configuring memory, CPU, and volume mounting.
 * - Retrieval and setup of necessary LLM models for testing.
 * - Graceful cleanup of Docker resources after testing.
 */
class OllamaTestFixture {
    companion object {
        private const val PORT = 11434

        private val logger = KotlinLogging.logger {}
    }

    lateinit var client: OllamaClient
        private set
    lateinit var executor: MultiLLMPromptExecutor
        private set

    val model get() = prepareModel(OllamaModels.Meta.LLAMA_3_2)
    val toolChoiceModel get() = prepareModel(OllamaModels.Alibaba.QWEN_3_5_9B)
    val embeddingsModel get() = prepareModel(OllamaModels.Embeddings.NOMIC_EMBED_TEXT)
    val visionModel get() = prepareModel(OllamaModels.Granite.GRANITE_3_2_VISION)
    val moderationModel get() = prepareModel(OllamaModels.Meta.LLAMA_GUARD_3)
    val thinkingModel get() = prepareModel(OllamaModels.DeepSeek.DEEPSEEK_R1_DISTILL_LLAMA_1_5B)
    val modelsWithHallucinations
        get() = listOf(
            model,
            prepareModel(OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B)
        )

    private lateinit var ollamaContainer: GenericContainer<*>
    private val preparedModels = Collections.synchronizedSet(mutableSetOf<String>())

    fun setup() {
        val localUrl = System.getenv("OLLAMA_LOCAL_URL")
        val imageUrl = System.getenv("OLLAMA_IMAGE_URL")
        val volumeName = System.getenv("OLLAMA_VOLUME_NAME")

        if (localUrl != null) {
            logger.info { "Local Ollama server URL provided, connecting to local Ollama" }
            setupLocal(localUrl)
        } else {
            logger.info { "Setting up Ollama in Docker container" }
            setupContainer(
                imageUrl = imageUrl ?: throw IllegalStateException("OLLAMA_IMAGE_URL not set"),
                volumeName = volumeName ?: throw IllegalStateException("OLLAMA_VOLUME_NAME not set")
            )
        }

        executor = MultiLLMPromptExecutor(client)
    }

    fun teardown() {
        if (::ollamaContainer.isInitialized) {
            cleanContainer()
        }
    }

    /**
     * Set up the client with Ollama in a Docker container.
     */
    private fun setupContainer(imageUrl: String, volumeName: String) {
        // Ensure the volume exists before starting container
        ensureVolumeExists(volumeName)

        ollamaContainer = GenericContainer(DockerImageName.parse(imageUrl)).apply {
            withExposedPorts(PORT)
            withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig?.apply {
                    withMemory(12L * 1024 * 1024 * 1024) // 12GB RAM for qwen3.5:9b
                    withCpuCount(2L)
                    // Mount the external volume
                    withBinds(
                        Bind(
                            volumeName,
                            Volume("/root/.ollama")
                        )
                    )
                }
            }
        }

        ollamaContainer.start()

        val host = ollamaContainer.host
        val port = ollamaContainer.getMappedPort(PORT)

        @Suppress("HttpUrlsUsage")
        val baseUrl = "http://$host:$port"
        waitForOllamaServer(baseUrl)

        client = OllamaClient(baseUrl)
    }

    /**
     * Set up the client with connection to locally running Ollama.
     */
    private fun setupLocal(localUrl: String) {
        client = OllamaClient(localUrl)
    }

    private fun prepareModel(model: LLModel): LLModel {
        if (preparedModels.contains(model.id)) return model

        synchronized(preparedModels) {
            if (preparedModels.contains(model.id)) return model

            try {
                runBlocking {
                    client.getModelOrNull(model.id, pullIfMissing = true)
                }
                preparedModels.add(model.id)
            } catch (e: Exception) {
                logger.error(e) { "Failed to pull model '${model.id}': ${e.message}" }
                teardown()
                throw e
            }
        }

        return model
    }

    private fun cleanContainer() {
        try {
            ollamaContainer.stop()
        } catch (e: Exception) {
            logger.info { "Error stopping ollama container: ${e.message}" }
        }
    }

    private fun ensureVolumeExists(volumeName: String) {
        val dockerClient = DockerClientFactory.instance().client()

        val existingVolumes = dockerClient.listVolumesCmd().exec().volumes
        val volumeExists = existingVolumes?.any { it.name == volumeName } ?: false

        if (!volumeExists) {
            logger.info { "Creating volume: $volumeName" }

            dockerClient.createVolumeCmd()
                .withName(volumeName)
                .exec()
        } else {
            logger.info { "Volume already exists: $volumeName" }
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
                            "Ollama server didn't respond after $maxAttempts attempts",
                            e
                        )
                    }
                }
                delay(1000)
            }
        }
    }
}
