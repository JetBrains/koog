package ai.grazie.code.agents.local.features.writer

import ai.grazie.code.agents.local.features.NetUtil.findAvailablePort
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.common.message.use
import ai.grazie.code.agents.local.features.common.remote.client.FeatureMessageRemoteClient
import ai.grazie.code.agents.local.features.common.remote.client.config.DefaultClientConnectionConfig
import ai.grazie.code.agents.local.features.common.remote.server.config.DefaultServerConnectionConfig
import ai.grazie.code.agents.local.features.common.remote.server.config.ServerConnectionConfig
import ai.grazie.code.agents.local.features.common.writer.FeatureMessageRemoteWriter
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class FeatureMessageRemoteWriterTest {

    companion object {
        private val logger: MPPLogger = LoggerFactory.create(
            FeatureMessageRemoteWriterTest::class.qualifiedName ?:
            "ai.grazie.code.agents.local.features.writer.FeatureMessageRemoteWriterTest"
        )

        private val defaultClientServerTimeout = 5.seconds
    }

    class TestFeatureMessageRemoteWriter(connectionConfig: ServerConnectionConfig) : FeatureMessageRemoteWriter(connectionConfig)

    //region Initialize

    @Test
    fun `test base state for non-initialized writer`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val writer = TestFeatureMessageRemoteWriter(serverConfig)
        assertFalse(writer.isOpen)
        assertFalse(writer.isReady.isCompleted)
    }

    @Test
    fun `test get target path using non-initialized writer`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val writer = TestFeatureMessageRemoteWriter(serverConfig)
        val throwable = assertThrows<IllegalStateException> {
            writer.processMessage(FeatureStringMessage("test message"))
        }

        val expectedError = "Writer is not initialized. Please make sure you call method 'initialize()' before."
        assertEquals(expectedError, throwable.message)
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test base state for initialized writer`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        TestFeatureMessageRemoteWriter(serverConfig).use { writer ->
            writer.initialize()
            assertTrue(writer.isOpen)
            assertTrue(writer.isReady.isCompleted)
            assertTrue(writer.isReady.getCompleted())
        }
    }

    @Test
    fun `test initialize twice from same thread`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        TestFeatureMessageRemoteWriter(serverConfig).use { writer ->
            writer.initialize()
            writer.initialize()
            assertTrue(writer.isOpen)
            assertTrue(writer.isReady.isCompleted)
            assertTrue(writer.isReady.getCompleted())
        }
    }

    //endregion Initialize



    //region Health Check

    @Test
    fun `test client make health check with get request`() = runBlocking {

        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            TestFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->

                logger.info { "Server is started on port: ${writer.server.connectionConfig.port}" }
                writer.initialize()

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                logger.info { "Send healthcheck get request." }
                client.healthCheck()

                isClientFinished.complete(true)
                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    //endregion Health Check

    //region SSE

    @Test
    fun `test string sse message received from a server`() = runBlocking {

        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val testServerMessage = FeatureStringMessage("test server message")

        val serverJob = launch {
            TestFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->
                logger.info { "Server is started on port: ${writer.server.connectionConfig.port}" }
                writer.initialize()

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                writer.processMessage(testServerMessage)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                client.connect()

                val actualMessage = client.receivedMessages.consumeAsFlow().firstOrNull()
                isClientFinished.complete(true)

                assertNotNull(actualMessage) { "Client did not receive a server SSE message in time" }
                assertEquals(FeatureMessage.Type.Message, actualMessage.messageType)

                val actualStringMessage = actualMessage as? FeatureStringMessage
                assertNotNull(actualStringMessage) { "Client received a server SSE message, but it is not a string message" }
                assertEquals(testServerMessage.message, actualStringMessage.message)

                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test event sse message received from a server`() = runBlocking {

        val port = findAvailablePort()

        val customSerializersModule = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(TestFeatureEventMessage::class, TestFeatureEventMessage.serializer())
            }
        }

        val serverConfig = DefaultServerConnectionConfig(port = port).apply { appendSerializersModule(customSerializersModule) }
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = HttpProtocolVersion.HTTP_2_0.name).apply {
            appendSerializersModule(customSerializersModule)
        }

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val testServerMessage = TestFeatureEventMessage(testMessage = "test server message")

        val serverJob = launch {
            TestFeatureMessageRemoteWriter(connectionConfig = serverConfig).use { writer ->
                logger.info { "Server is started on port: ${writer.server.connectionConfig.port}" }
                writer.initialize()

                launch {
                    writer.isReady.await()
                    isServerStarted.complete(true)
                }

                writer.processMessage(message = testServerMessage)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                client.connect()

                val actualMessage = client.receivedMessages.consumeAsFlow().firstOrNull()
                isClientFinished.complete(true)

                assertNotNull(actualMessage) { "Client did not receive a server SSE message in time" }
                assertEquals(FeatureMessage.Type.Event, actualMessage.messageType)

                val actualEventMessage = actualMessage as? TestFeatureEventMessage
                assertNotNull(actualEventMessage) { "Client received a server SSE message, but it is not a string message" }
                assertEquals(testServerMessage.eventId, actualEventMessage.eventId)

                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
