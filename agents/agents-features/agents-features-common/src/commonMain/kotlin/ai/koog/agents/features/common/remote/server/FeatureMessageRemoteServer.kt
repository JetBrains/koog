package ai.koog.agents.features.common.remote.server

import ai.koog.agents.features.common.ExceptionExtractor.rootCause
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.serializer
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds

/**
 * A server for managing remote feature message communication via server-sent events (SSE) and HTTP endpoints.
 *
 * Supported features:
 *   - Server-sent events (SSE) for messages from a server side;
 *   - Provides health check and message handling endpoints via HTTP;
 *   - Process messages from clients via /message POST request.
 *
 * Note: Please make sure you call the [start] method before starting a communication process.
 */
public class FeatureMessageRemoteServer(
    public val connectionConfig: ServerConnectionConfig,
) : FeatureMessageServer {

    private companion object {
        private val logger = KotlinLogging.logger {  }
    }

    private var isInitialized = false

    private var server: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> by Delegates.notNull()

    private val toSendMessages: Channel<FeatureMessage> = Channel(Channel.UNLIMITED)


    override val isStarted: Boolean
        get() = isInitialized

    /**
     * A channel used to receive and process incoming feature messages sent to the server.
     *
     * This property acts as an endpoint for handling incoming messages of type [FeatureMessage].
     * Messages received on this channel are expected to be processed asynchronously, enabling
     * the server to handle events such as client requests or updates in a non-blocking manner.
     *
     * Key Characteristics:
     * - The channel is configured with an unlimited capacity, allowing it to buffer incoming messages
     *   without restriction. This ensures robustness during high message throughput scenarios.
     * - Incoming feature messages may represent various system events, categorized by their
     *   `messageType` or timestamp as per the [FeatureMessage] interface.
     *
     * Use Cases:
     * - Accepting and handling POST requests containing feature messages from clients.
     * - Routing received messages for further processing, storage, or broadcasting.
     * - Supporting server functionality by integrating received messages into its workflow for
     *   tasks such as health checks, client communication, or event-driven responses.
     *
     * Behavior:
     * - The channel remains open during the server's lifecycle and is explicitly closed
     *   when the server is stopped (e.g., via the `stopServer` method).
     * - Consumers of this channel are responsible for correctly processing the messages
     *   while adhering to the channel's concurrency guarantees.
     */
    public val receivedMessages: Channel<FeatureMessage> = Channel(Channel.UNLIMITED)


    /**
     * Serializes the current [FeatureMessage] instance into a JSON string format.
     *
     * This method leverages the serialization configuration defined in the `connectionConfig.jsonConfig`
     * property to convert the [FeatureMessage] object into its corresponding JSON representation.
     * The serialization process ensures that all the necessary properties and structure of
     * the [FeatureMessage] are captured for transmission or storage.
     *
     * @return A JSON string representing the serialized form of the [FeatureMessage] instance.
     */
    public fun FeatureMessage.toServerEventData(): String {
        val jsonConfig = connectionConfig.jsonConfig

        val serialized = jsonConfig.encodeToString(serializer = jsonConfig.serializersModule.serializer(), value = this@toServerEventData)

        return serialized
    }

    //region Start / Stop

    override suspend fun start() {
        logger.info { "Feature Message Remote Server. Starting server on port ${connectionConfig.port}" }

        if (isInitialized) {
            logger.warn { "Feature Message Remote Server. Server is already started! Skip initialization." }
            return
        }

        startServer(host = connectionConfig.host, port = connectionConfig.port)
        logger.debug { "Feature Message Remote Server. Initialized successfully on port ${connectionConfig.port}" }

        isInitialized = true
    }

    override suspend fun close() {
        logger.info { "Feature Message Remote Server. Starting closing server on port ${connectionConfig.port}" }

        if (!isInitialized) {
            logger.warn { "Feature Message Remote Server. Server is already stopped! Skip stopping." }
            return
        }

        stopServer()

        isInitialized = false
    }

    //endregion Start / Stop

    //region Message

    override suspend fun sendMessage(message: FeatureMessage) {
        toSendMessages.send(message)
    }

    //endregion Message

    //region Private Methods

    private fun startServer(host: String, port: Int) {
        try {
            val server = createServer(host = host, port = port)
            server.start(wait = false)
        }
        catch (t: CancellationException) {
            // Server start() method starts a coroutine job canceled in case of IOException.
            // The result is that we get a JobCancellationException here in case of any error on server start.
            // Get a root cause to know a real exception that case server to stop.
            val rootCause = t.rootCause
            logger.error(t) { "Feature Message Remote Server. Starting server on port $port job was cancelled. Root exception: $rootCause" }
            throw rootCause ?: t
        }
        catch (t: Throwable) {
            logger.error(t) { "Feature Message Remote Server. Error starting server on port $port: ${t.message}" }
            throw t
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createServer(host: String, port: Int): EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration> {

        logger.debug { "Feature Message Remote Server. Start creating server on port: $port" }

        val factory = CIO as ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration>
        server = embeddedServer(factory = factory, host = host, port = port) {
            install(SSE)

            routing {

                sse("/sse") {
                    toSendMessages.consumeAsFlow().collect { message: FeatureMessage ->
                        logger.debug { "Feature Message Remote Server. Process server event: $message" }

                        try {
                            val serverEventData: String = message.toServerEventData()
                            logger.debug { "Feature Message Remote Server. Send encoded message: $serverEventData" }

                            val serverEvent = ServerSentEvent(
                                event = message.messageType.value,
                                data = serverEventData,
                            )

                            logger.debug { "Feature Message Remote Server. Sending SSE server event: $serverEvent" }
                            send(serverEvent)
                        }
                        catch (t: CancellationException) {
                            logger.info { "Feature Message Remote Server. Sending SSE message (message: $message) has been canceled: ${t.message}" }
                            throw t
                        }
                        catch (t: Throwable) {
                            logger.error(t) { "Feature Message Remote Server. Error while sending SSE event: ${t.message}" }
                        }
                    }
                }

                get("/health") {
                    call.respond(HttpStatusCode.OK, "Feature Message Remote Server. Server is running.")
                }

                post("/message") {
                    try {
                        val messageString = call.receiveText()
                        val message = messageString.toFeatureMessage()
                        logger.debug { "Feature Message Remote Server. Received message: $message" }

                        receivedMessages.send(message)

                        call.respond(HttpStatusCode.OK)
                    }
                    catch (t: CancellationException) {
                        logger.debug { "Feature Message Remote Server. Received message has been canceled: ${t.message}" }
                        throw t
                    }
                    catch (t: Throwable) {
                        logger.error(t) { "Feature Message Remote Server. Error while receiving message: ${t.message}" }
                        call.respond(HttpStatusCode.InternalServerError, "Error on receiving message: ${t.message}")
                    }
                }
            }
        }

        logger.info { "Feature Message Remote Server. Server is running on port: $port" }
        return server
    }

    private suspend fun stopServer() {
        logger.info { "Feature Message Remote Server. Starting stopping server jobs for server on port: ${connectionConfig.port}" }

        toSendMessages.close()
        receivedMessages.close()

        // TODO: Wait here a bit to avoid the Ktor issue:
        //       https://youtrack.jetbrains.com/issue/KTOR-8204/CIO-Exception-Chunked-stream-has-ended-unexpectedly
        delay(200.milliseconds)

        server.stop(1000, 2000)
        logger.info { "Feature Message Remote Server. The server on port ${connectionConfig.port} has been stopped" }
    }

    private fun String.toFeatureMessage(): FeatureMessage {
        return connectionConfig.jsonConfig.decodeFromString(
            deserializer = connectionConfig.jsonConfig.serializersModule.serializer(),
            string = this
        )
    }

    //endregion Private Methods
}
