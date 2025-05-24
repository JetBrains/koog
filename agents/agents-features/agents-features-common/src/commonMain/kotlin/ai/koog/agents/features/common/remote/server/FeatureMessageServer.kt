package ai.koog.agents.features.common.remote.server

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.utils.Closeable

/**
 * Represents a server responsible for managing and facilitating communication of feature messages.
 * Typically used for sending and handling messages within a remote server context.
 *
 * A server is a pat of a Kotlin AI Agent.
 * The server is started inside the same process as the running agent.
 * It is used to broadcast agent execution events to connected clients (e.g., a running IDE).
 * Please see description for a client in [ai.koog.agents.features.common.remote.client.FeatureMessageRemoteClient].
 *
 * Features:
 *   - Send SSE events [FeatureMessage] during agent execution, e.g. [ai.koog.agents.core.feature.model.AgentCreateEvent];
 *   - Process incoming messages from a client;
 *   - Respond to client's health check requests to verify connection state.
 */
public interface FeatureMessageServer : Closeable {

    /**
     * Indicates whether the server has been started.
     *
     * This property returns `true` if the server is initialized and running,
     * and `false` if it has not been initialized or has been stopped.
     *
     * It is used to ensure the state of the server before performing operations
     * such as message broadcasting or responding to client requests.
     */
    public val isStarted: Boolean

    /**
     * Starts the server, initializing any necessary resources and beginning to listen for incoming client connections or events.
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
