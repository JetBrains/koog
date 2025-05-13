package ai.grazie.code.agents.local.features.remote.server

import ai.grazie.code.agents.local.features.NetUtil.findAvailablePort
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.common.message.use
import ai.grazie.code.agents.local.features.common.remote.client.FeatureMessageRemoteClient
import ai.grazie.code.agents.local.features.common.remote.client.config.DefaultClientConnectionConfig
import ai.grazie.code.agents.local.features.common.remote.server.FeatureMessageRemoteServer
import ai.grazie.code.agents.local.features.common.remote.server.config.DefaultServerConnectionConfig
import ai.grazie.code.agents.local.features.writer.TestFeatureEventMessage
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.io.IOException
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class FeatureMessageRemoteServerTest {

    companion object {
        private val logger: MPPLogger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.remote.server.FeatureMessageRemoteServerTest")

        private val defaultClientServerTimeout = 5.seconds
    }

    //region Start / Stop

    @Test
    fun `test server is started on a free port`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)
        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            server.start()
            assertTrue(server.isStarted)
        }
    }

    @Test
    fun `test start server that is already started`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server ->
            server.start()
            assertTrue(server.isStarted)

            server.start()
            assertTrue(server.isStarted)
        }
    }

    @Test
    fun `test server is started on an occupied port`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server1 ->
            server1.start()
            FeatureMessageRemoteServer(connectionConfig = serverConfig).use { server2 ->
                val throwable = assertFailsWith<IOException> {
                    server2.start()
                }

                val expectedErrorMessage = throwable.message
                assertNotNull(expectedErrorMessage)

                assertTrue(expectedErrorMessage.contains("Address already in use"))
            }
        }
    }

    @Test
    fun `test stop server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val server = FeatureMessageRemoteServer(connectionConfig = serverConfig)
        server.start()
        assertTrue(server.isStarted)

        server.close()
        assertFalse(server.isStarted)
    }

    @Test
    fun `test stop already stopped server`() = runBlocking {
        val port = findAvailablePort()
        val serverConfig = DefaultServerConnectionConfig(port = port)

        val server = FeatureMessageRemoteServer(connectionConfig = serverConfig)
        assertFalse(server.isStarted)
        server.close()
        assertFalse(server.isStarted)
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

        val serverConfig = DefaultServerConnectionConfig(port = port).apply { appendSerializersModule(customSerializersModule) }
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
