package ai.koog.agents.core.feature.remote.client

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.utils.Closeable
import kotlinx.coroutines.flow.StateFlow

/**
 * An interface representing a client to interact with a feature messaging system.
 *
 * The client is responsible for:
 *   - Connecting to a remote feature messaging service;
 *   - Sending feature-related messages;
 *   - Performing periodic health checks to ensure the connection's integrity.
 *
 * A client can be used in an IDE to receive remote events from the running agents to present
 * collected agent state traces and show them inside this IDE.
 */
public interface FeatureMessageClient : Closeable {

    /**
     * Indicates whether the client is currently connected to the remote feature messaging service.
     *
     * This property reflects the connection state of the client. It is `true` if the client has
     * successfully established a communication link with the remote service and `false` otherwise.
     */
    public val isConnected: StateFlow<Boolean>

    /**
     * Establishes a connection to the remote feature messaging service.
     *
     * This method initiates communication with the remote service based on the client's configuration
     * and prepares it for sending and receiving feature-related messages. Upon successful completion,
     * the client transitions to a connected state and `isConnected` will return `true`.
     *
     * If an attempt to connect fails (e.g., due to networking issues or authentication errors),
     * the method will throw an appropriate exception or error indicating the failure reason.
     */
    public suspend fun connect()

    /**
     * Sends a feature-related message to the remote feature messaging service.
     *
     * The method facilitates communication by delivering a `FeatureMessage` instance
     * containing the relevant data and metadata, such as the timestamp and type.
     * It should only be invoked when the client is in a connected state to ensure
     * successful delivery. Failure to send the message, due to connectivity issues
     * or other errors, will result in an exception being thrown.
     *
     * @param message The `FeatureMessage` instance that encapsulates the details
     *                of the message to be sent, including its timestamp and type.
     */
    public suspend fun send(message: FeatureMessage)

    /**
     * Performs a health check to verify the status of the remote feature messaging service.
     *
     * This method checks the availability and responsiveness of the service the client is connected to
     * by making a health check request. It is intended to ensure that the remote service is operational
     * and ready to handle requests. The health check may involve a lightweight request to a designated
     * health check endpoint.
     *
     * This operation is asynchronous and may suspend until the health check completes. If the health
     * check fails, an exception may be thrown to indicate the failure.
     *
     * It is recommended to perform a health check periodically, especially in scenarios with prolonged
     * idle connections or before critical operations, to guarantee the remote service is in a healthy state.
     */
    public suspend fun healthCheck()
}
