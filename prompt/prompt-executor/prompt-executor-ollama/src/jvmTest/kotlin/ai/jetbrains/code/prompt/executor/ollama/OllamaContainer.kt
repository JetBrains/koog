package ai.jetbrains.code.prompt.executor.ollama

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import org.slf4j.LoggerFactory

class OllamaContainer : GenericContainer<OllamaContainer>(DockerImageName.parse("ollama/ollama:latest")) {

    companion object {
        private const val OLLAMA_PORT = 11434
        private val logger = LoggerFactory.getLogger(OllamaContainer::class.java)
    }

    init {
        withExposedPorts(OLLAMA_PORT)

        withLogConsumer(Slf4jLogConsumer(logger))

        waitingFor(
            Wait.forLogMessage(".*Listening on.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2))
        )
    }

    override fun start() {
        logger.info("Starting Ollama container...")
        super.start()
        logger.info("Ollama container started, URL: ${getOllamaUrl()}")

        try {
            logger.info("Pulling Llama model...")
            val pullResult = execInContainer("ollama", "pull", "llama3.2")
            logger.info("Model pull result: ${pullResult.stdout}")

            if (pullResult.exitCode != 0) {
                logger.error("Failed to pull model: ${pullResult.stderr}")
                logger.info("Trying alternative model name format...")
                val fallbackResult = execInContainer("ollama", "pull", "meta/llama3:latest")
                logger.info("Fallback model pull result: ${fallbackResult.stdout}")

                if (fallbackResult.exitCode != 0) {
                    logger.error("Failed to pull fallback model: ${fallbackResult.stderr}")
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during model pull: ${e.message}")
        }

        try {
            val versionResult = execInContainer("curl", "-s", "http://localhost:${OLLAMA_PORT}/api/version")
            logger.info("Ollama API version: ${versionResult.stdout}")

            if (versionResult.exitCode != 0) {
                logger.error("Failed to get API version: ${versionResult.stderr}")
            }
        } catch (e: Exception) {
            logger.error("Failed to verify Ollama API: ${e.message}")
        }
    }

    fun getOllamaUrl(): String {
        return "http://${host}:${getMappedPort(OLLAMA_PORT)}"
    }
}
