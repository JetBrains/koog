package ai.koog.a2a.client

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Object representing a Dockerized test server for Python-based A2A (Agent-to-Agent) communication.
 * This server is instantiated using Testcontainers and is primarily utilized for integration testing
 * purposes, such as validating JSON-RPC HTTP communication in an A2A client context.
 *
 * The server is built from a Dockerfile located in the `../test-python-a2a-server` directory and runs
 * on a predefined exposed port. It provides runtime flexibility to inspect the server's dynamically
 * assigned host and port.
 *
 * This `object` ensures the server is only started once for use in test environments, with the
 * capability of shutting it down after the tests are completed.
 *
 * The server should be initialized before tests, retrieving its host and port, and
 * using them to configure a test client.
 */
object TestA2AServerContainer {

    private const val EXPOSED_PORT = 9999
    private val STARTUP_TIMEOUT = 20.seconds.toJavaDuration()

    private val image =
        ImageFromDockerfile("test-python-a2a-server:latest", false) // "false" prevents deleting intermediate images
            .withFileFromPath(".", Path("../test-python-a2a-server")) // Specify Dockerfile context path
    private val container: GenericContainer<*> =
        GenericContainer(image)
            .withTmpFs(mapOf("/tmp" to "rw,noexec,size=16m"))
            .withExposedPorts(EXPOSED_PORT)
            .withReuse(true)
            .waitingFor(Wait.forListeningPort())
            .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger(TestA2AServerContainer::class.java)))
            .withStartupTimeout(STARTUP_TIMEOUT)

    init {
        container.start()
    }

    fun shutdown() = runCatching { container.stop() }

    val host: String by lazy {
        container.host
    }

    val port: Int by lazy {
        container.getMappedPort(EXPOSED_PORT)
    }
}
