package ai.koog.a2a.client

import ai.koog.a2a.transport.ClientTransport

/**
 * A2A client responsible for sending requests to A2A server.
 */
public class A2AClient(
    private val transport: ClientTransport,
)
