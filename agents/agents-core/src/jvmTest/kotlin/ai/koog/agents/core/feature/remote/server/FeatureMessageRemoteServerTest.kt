package ai.koog.agents.core.feature.remote.server

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.junit.jupiter.api.Disabled
import java.net.BindException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FeatureMessageRemoteServerTest {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val defaultClientServerTimeout = 30.seconds
    }

    //region Start / Stop

    @Test
    fun `test server is started on a free port`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            server.start()
            assertTrue(server.isStarted.value)
        }
    }

    @Test
    fun `test start server that is already started`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            server.start()
            assertTrue(server.isStarted.value)

            server.start()
            assertTrue(server.isStarted.value)
        }
    }

    @Test
    @Disabled(
        """Disable test for now for investigation
        Test is passed, but exception inside server2.start() call cause the coroutine to be cancelled later and
        error result cause the further tests to fail.
        Test is not critical for general functionality of the remote server."""
    )
    fun `test server is started on an occupied port`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server1 ->
            server1.start()

            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server2 ->
                val throwable = assertFailsWith<BindException> {
                    server2.start()
                }

                val expectedErrorMessage = throwable.message
                assertNotNull(
                    expectedErrorMessage,
                    "Expected to get error message, but it is not defined."
                )

                assertTrue(
                    expectedErrorMessage.contains("Address already in use"),
                    "Expected to get BindError with error 'Address already in use', but got: $expectedErrorMessage"
                )
            }
        }
    }

    @Test
    fun `test stop server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val server = FeatureMessageRemoteServer(connectionConfig = serverConfig)
        server.start()
        assertTrue(server.isStarted.value)

        server.close()
        assertFalse(server.isStarted.value)
    }

    @Test
    fun `test stop already stopped server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val server = FeatureMessageRemoteServer(connectionConfig = serverConfig)
        assertFalse(server.isStarted.value)
        server.close()
        assertFalse(server.isStarted.value)
    }

    @Test
    fun `test server is started with wait connection flag and no connected clients`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port, waitConnection = true)
        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            val serverJob = launch(Dispatchers.IO) {
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                server.start()
                logger.info { "Server is finished successfully" }
            }

            val isServerConnected = withTimeoutOrNull(defaultClientServerTimeout) {
                server.isStarted.first { it }
            } != null

            // Cancel the server connection job to terminate logic that awaits connected clients
            serverJob.cancelAndJoin()

            assertTrue(isServerConnected, "Server is not started after a timeout: $defaultClientServerTimeout")
        }
    }

    @Test
    fun `test server is waiting for connected clients before continue`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port, waitConnection = true)
        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->

            val isClientConnected = MutableStateFlow(false)

            val serverJob = launch(Dispatchers.IO) {
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }
                server.start()
                isClientConnected.value = true
                logger.info { "Server is finished successfully" }
            }

            val clientConnectedTimeout = 5.seconds
            val isServerConnected = withTimeoutOrNull(clientConnectedTimeout) {
                isClientConnected.first { it }
            } != null

            server.close()
            serverJob.cancelAndJoin()

            assertFalse(isServerConnected, "Server got a connected state before timeout: $clientConnectedTimeout")
        }
    }

    @Test
    fun `test server is started with wait connection flag client connected`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port, waitConnection = true)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val isServerReceiveConnection = MutableStateFlow(false)

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
                    logger.info { "Client is finished successfully" }
                }

                withTimeoutOrNull(defaultClientServerTimeout) {
                    isServerReceiveConnection.first { it } && client.isConnected.value
                } != null

                serverJob.cancelAndJoin()
                clientJob.cancelAndJoin()

                assertTrue(isServerReceiveConnection.value, "Server did not receive a connection: $defaultClientServerTimeout")
            }
        }
    }

    //endregion Start / Stop

    //region SSE

    @Test
    fun `test server sends a valid message to a client`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val testServerMessage = FeatureStringMessage("test server message")

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

                logger.info { "Server send message to a client" }
                server.sendMessage(message = testServerMessage)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val expectedClientMessages = listOf(testServerMessage)
                val actualClientMessages = mutableListOf<FeatureMessage>()

                val clientReceiveMessagesJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message ->
                        actualClientMessages.add(message)
                        if (actualClientMessages.size >= expectedClientMessages.size) {
                            cancel()
                        }
                    }
                }

                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                logger.info { "Client await server messages." }
                clientReceiveMessagesJob.join()

                isClientFinished.complete(true)

                assertEquals(expectedClientMessages.size, actualClientMessages.size)
                assertContentEquals(expectedClientMessages, actualClientMessages)

                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test server sends an invalid message to a client`() = runBlocking {
        val port = findAvailablePort()

        val customSerializersModule = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(TestFeatureEventMessage::class, TestFeatureEventMessage.serializer())
            }
        }

        val serverConfig = DefaultServerConnectionConfig(port = port).apply {
            appendSerializersModule(customSerializersModule)
        }
        val clientConfig = DefaultClientConnectionConfig(host = "127.0.0.1", port = port, protocol = URLProtocol.HTTP)

        val testServerMessage = TestFeatureEventMessage("test server message")

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()
        val isServerSentMessage = CompletableDeferred<Boolean>()

        val serverJob = launch {
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
                server.start()
                isServerStarted.complete(true)
                logger.info { "Server is started on port: ${server.connectionConfig.port}" }

                logger.info { "Server send message to a client" }
                server.sendMessage(message = testServerMessage)
                isServerSentMessage.complete(true)

                isClientFinished.await()
                logger.info { "Server is finished successfully" }
            }
        }

        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val actualClientMessages = mutableListOf<FeatureMessage>()

                val serverSentMessageJob = launch {
                    isServerSentMessage.await()
                    delay(100)
                }

                val clientReceiveMessagesJob = launch {
                    client.receivedMessages.consumeAsFlow().collect { message ->
                        actualClientMessages.add(message)
                    }
                }

                logger.info { "Client connecting to remote server: ${client.connectionConfig.url}" }
                isServerStarted.await()

                logger.info { "Server is started. Connecting client..." }
                client.connect()

                logger.info { "Client await server messages." }
                serverSentMessageJob.join()
                clientReceiveMessagesJob.cancelAndJoin()

                isClientFinished.complete(true)

                assertEquals(0, actualClientMessages.size)

                logger.info { "Client is finished successfully" }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    //endregion SSE
}
