package ai.koog.agents.core.feature.remote.server

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.utils.io.Closeable
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a server responsible for managing and facilitating communication of feature messages.
 * Typically used for sending and handling messages within a remote server context.
 *
 * A server is a pat of a Kotlin AI Agent.
 * The server is started inside the same process as the running agent.
 * It is used to broadcast agent execution events to connected clients (e.g., a running IDE).
 * Please see description for a client in [ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient].
 *
 * Features:
 *   - Send SSE events [ai.koog.agents.core.feature.message.FeatureMessage] during agent execution;
 *   - Process incoming messages from a client;
 *   - Respond to client's health check requests to verify connection state.
 */
public interface FeatureMessageServer : Closeable {

    /**
     * Represents the current state of the server, indicating whether it has been started and is actively running.
     *
     * This state flow emits a boolean value:
     * - `true` if the server is running and ready to process incoming connections or events.
     * - `false` if the server is stopped or has not yet been started.
     *
     * The value of `isStarted` is updated automatically based on the server's lifecycle transitions,
     * such as when starting or stopping the server.
     *
     * This property can be used to monitor the server's state, ensuring that its operations
     * are executed only when it is in the appropriate running state.
     * It is particularly useful for preventing redundant start operations or for providing feedback to the client.
     */
    public val isStarted: StateFlow<Boolean>

    /**
     * Starts the server, initializing any necessary resources, and beginning to listen for incoming client connections or events.
     *
     * This method ensures that the server transitions into a running state, allowing it to process incoming messages,
     * send SSE events [FeatureMessage], and respond to health check requests from clients.
     *
     * It is recommended to check the server's state using [isStarted] before invoking this method to prevent redundant operations.
     *
     * @throws IllegalStateException if the server is already running or cannot be started due to invalid configuration.
     * @throws kotlinx.io.IOException if an error occurs while initializing the server's underlying infrastructure.
     */
    public suspend fun start()

    /**
     * Sends a feature message for further processing or delivery to connected clients via server-sent events (SSE).
     *
     * This method is designed to enqueue the given [FeatureMessage] into a channel, from which
     * it will be serialized and sent to all subscribed clients or receivers, if applicable. The
     * method ensures that the message conforms to the expected [FeatureMessage] interface.
     *
     * @param message The message to be sent, implementing the [FeatureMessage] interface. This message
     *                includes information such as a timestamp and type, ensuring proper context
     *                for the recipient.
     */
    public suspend fun sendMessage(message: FeatureMessage)
}
