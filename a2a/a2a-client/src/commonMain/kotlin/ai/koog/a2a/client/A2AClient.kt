package ai.koog.a2a.client

import ai.koog.a2a.transport.ClientTransport

/**
 * Implementation of the A2A Client, acting as a wrapper for the provided transport mechanism.
 *
 * The class delegates its functionality to an instance of [ClientTransport], implementing
 * the A2A protocol methods for client-side communication. Through this delegation, the client
 * can send requests and manage responses for operations defined in the A2A protocol specification.
 *
 * The [ClientTransport] interface is responsible for handling server responses, including
 * managing errors and converting them to the appropriate exception types.
 *
 * @param transport The underlying client transport that facilitates communication with the server.
 * This transport must handle all A2A protocol-specific logic and error handling.
 */
public class A2AClient(
    private val transport: ClientTransport,
) : ClientTransport by transport
