# A2A protocol

This page provides an overview of the A2A (Agent-to-Agent) protocol implementation in the Koog agentic framework.

## What is the A2A protocol?

The A2A (Agent-to-Agent) protocol is a standardized communication protocol that enables AI agents to interact with each other and with client applications.
It defines a set of methods, message formats, and behaviors that allow for consistent and interoperable agent communication.
For more information and a detailed specification of the A2A protocol, see the official [A2A Protocol website](https://a2a-protocol.org/latest/).

## Key components

The Koog implementation of the A2A protocol provides both client and server support as well as integration with the Koog agent framework:
- [A2A Server](a2a-server.md) is an agent or an agentic system that exposes an endpoint implementing the A2A protocol. It receives requests from clients, processes tasks, and returns results or status updates.
- [A2A Client](a2a-client.md) is a client application (can be also an agent) that initiates communication with A2A server using the A2A protocol.
- [A2A Koog Integration](a2a-koog.md) is a set of classes and utilities that simplify the integration of A2A with Koog Agents. It contains components (A2A feature and nodes) for seamless A2A agent connection to Koog agent and communication.

