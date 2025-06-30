package ai.koog.agents.features.remote.client

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.common.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.features.common.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.features.common.remote.server.FeatureMessageRemoteServer
import ai.koog.agents.features.common.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.features.writer.TestFeatureEventMessage
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.utils.use
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FeatureMessageRemoteClientTest {

    companion object {
        private val logger = KotlinLogging.logger {  }

        private val defaultClientServerTimeout = 500.seconds
    }

    //region Start / Stop

    @Test
    fun `test client connect to running server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                server.start()
                isServerStarted.complete(true)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                assertFalse(client.isConnected)

                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                isClientFinished.complete(true)

                assertTrue(client.isConnected)
                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test already connected client connect again`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                server.start()
                isServerStarted.complete(true)

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
                assertTrue(client.isConnected)

                client.connect()
                assertTrue(client.isConnected)

                isClientFinished.complete(true)
                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test client connect to stopped server`() = runBlocking {
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
            logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }

            logger.info { "Server is started. Connecting client..." }
            val throwable = assertFailsWith<IllegalStateException> {
                client.connect()
            }

            val actualErrorMessage = throwable.message
            assertNotNull(actualErrorMessage)

            assertTrue(actualErrorMessage.contains("Connection refused"))
            assertFalse(client.isConnected)
            logger.info { "Client is finished successfully" }
        }
    }

    @Test
    fun `test stop connected client`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                server.start()
                isServerStarted.complete(true)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            val client = FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this)

            logger.info { "Client await for a server to start" }
            isServerStarted.await()

            logger.info { "Server is started. Connecting client..." }
            client.connect()
            assertTrue(client.isConnected)

            logger.info { "Close connected client." }
            client.close()
            assertFalse(client.isConnected)

            isClientFinished.complete(true)
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test stop not connected client`() = runBlocking {
        val port = findAvailablePort()
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val client = FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this)
        assertFalse(client.isConnected)

        logger.info { "Close client." }
        client.close()
        assertFalse(client.isConnected)
    }

    //endregion Start / Stop

    //region Health Check

    @Test
    fun `test server send get response to a client`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->

                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

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

    //region Send

    @Test
    fun `test client send a valid message to a server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val testClientMessage = FeatureStringMessage("test client message")

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->

                val expectedServerReceivedMessages = listOf(testClientMessage)
                val actualServerReceivedMessages = mutableListOf<FeatureMessage>()

                val serverMessageReceiveJob = launch {
                    server.receivedMessages.consumeAsFlow().collect { message ->
                        actualServerReceivedMessages.add(message)
                        if (actualServerReceivedMessages.size >= expectedServerReceivedMessages.size) {
                            cancel()
                        }
                    }
                }

                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

                serverMessageReceiveJob.join()

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

                logger.info { "Send message to a server." }
                client.send(message = testClientMessage)

                isClientFinished.complete(true)
                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test client send an invalid message to a server`() = runBlocking {
        val port = findAvailablePort()

        val customSerializersModule = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(TestFeatureEventMessage::class, TestFeatureEventMessage.serializer())
            }
        }

        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig("127.0.0.1", port, URLProtocol.HTTP).apply {
            appendSerializersModule(customSerializersModule)
        }

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val testClientMessage = TestFeatureEventMessage("test client message")

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

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

                logger.info { "Send unexpected message to a server." }
                val throwable = assertFailsWith<IllegalStateException> {
                    client.send(message = testClientMessage)
                }

                isClientFinished.complete(true)

                val expectedErrorMessage = "Failed to send message: $testClientMessage. Response (status: 500, message: Error on receiving message: Unexpected JSON token at offset 0"
                val actualErrorMessage = throwable.message ?: ""

                assertTrue(
                    actualErrorMessage.startsWith(expectedErrorMessage),
                    "Expected error message: <$expectedErrorMessage>, but received: <$actualErrorMessage>"
                )

                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    //endregion Send
}
