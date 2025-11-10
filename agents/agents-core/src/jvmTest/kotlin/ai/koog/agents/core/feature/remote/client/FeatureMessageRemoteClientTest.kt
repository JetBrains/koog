package ai.koog.agents.core.feature.remote.client

import ai.koog.agents.core.feature.AIAgentFeatureTestAPI.knownDefinedEvents
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.FeatureMessageRemoteServer
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.feature.writer.TestFeatureEventMessage
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.utils.io.use
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FeatureMessageRemoteClientTest {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val defaultClientServerTimeout = 30.seconds
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
                assertFalse(client.isConnected.value)

                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                isClientFinished.complete(true)

                assertTrue(client.isConnected.value)
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
                assertTrue(client.isConnected.value)

                client.connect()
                assertTrue(client.isConnected.value)

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
            assertFalse(client.isConnected.value)
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
            assertTrue(client.isConnected.value)

            logger.info { "Close connected client." }
            client.close()
            assertFalse(client.isConnected.value)

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
        assertFalse(client.isConnected.value)

        logger.info { "Close client." }
        client.close()
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `test server is started with wait connection flag client connected`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port, awaitInitialConnection = true)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val isServerReceiveConnection = MutableStateFlow(false)
                val isClientConnected = MutableStateFlow(false)

                val serverJob = launch(Dispatchers.IO) {
                    logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                    server.start()
                    isServerReceiveConnection.value = true
                    logger.info { "Server is finished successfully" }
                }

                val clientJob = launch(Dispatchers.IO) {
                    logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }

                    server.isStarted.first { it }
                    client.connect()
                    isClientConnected.value = client.isConnected.first { it }
                    logger.info { "Client is finished successfully" }
                }

                withTimeoutOrNull(defaultClientServerTimeout) {
                    isServerReceiveConnection.first { it } && isClientConnected.first { it }
                } != null

                serverJob.cancelAndJoin()
                clientJob.cancelAndJoin()

                assertTrue(
                    isServerReceiveConnection.value,
                    "Server did not receive a connection: $defaultClientServerTimeout"
                )
                assertTrue(
                    isClientConnected.value,
                    "Client is not connected after a timeout: $defaultClientServerTimeout"
                )
            }
        }
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

                val expectedErrorMessage =
                    "Failed to send message: $testClientMessage. Response (status: 500, message: Error on receiving message: Unexpected JSON token at offset 0"
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

    //region Receive

    @Test
    fun `test client receive all known events from a server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val actualClientMessages = mutableListOf<FeatureMessage>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

                // Wait for a connected client
                server.isClientConnected.first { it }

                // Send known events
                knownDefinedEvents.forEach { message ->
                    server.sendMessage(message)
                }

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                val clientReceiveMessagesJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message ->
                        actualClientMessages.add(message)
                        if (actualClientMessages.size == knownDefinedEvents.size) {
                            cancel()
                        }
                    }
                }

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                logger.info { "Wait for client to receive all events from a server..." }
                clientReceiveMessagesJob.join()

                isClientFinished.complete(true)
                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(
            isFinishedOrNull,
            "Client or server did not finish in time.\n" +
                "Expected events:\n${knownDefinedEvents.joinToString("\n") { " - $it" }}\n" +
                "Actual events:\n${actualClientMessages.joinToString("\n") { " - $it" }}\n" +
                "Missing events:\n${knownDefinedEvents.map { it::class.simpleName }.toSet()
                    .minus(actualClientMessages.map { it::class.simpleName }.toSet())
                    .joinToString("\n") { " - $it" }}\n"
        )

        assertEquals(knownDefinedEvents.size, actualClientMessages.size)
        assertContentEquals(knownDefinedEvents, actualClientMessages)
    }

    //endregion Receive
}
