# A2A protocol

This page provides an overview of the A2A (Agent-to-Agent) protocol implementation in the Koog agentic framework.

## What is the A2A protocol?

The A2A (Agent-to-Agent) protocol is a standardized communication protocol that enables AI agents to interact with each
other and with client applications.
It defines a set of methods, message formats, and behaviors that allow for consistent and interoperable agent
communication.
For more information and a detailed specification of the A2A protocol, see the
official [A2A Protocol website](https://a2a-protocol.org/latest/).

## Getting Started

**Important**: A2A dependencies are **not** included by default in the `koog-agents` meta-dependency. 
You must explicitly add the A2A modules you need to your project.

To use A2A in your project, add dependencies based on your use case:


- **For A2A client**: See [A2A Client documentation](a2a-client.md#dependencies)
- **For A2A server**: See [A2A Server documentation](a2a-server.md#dependencies)
- **For Koog integration**: See [A2A Koog Integration documentation](a2a-koog-integration.md#dependencies)

## Key A2A components

Koog provides full implementation of A2A protocol v0.3.0 for both client and server, as well as integration with the
Koog agent framework:

- [A2A Server](a2a-server.md) is an agent or agentic system that exposes an endpoint implementing the A2A protocol. It
  receives requests from clients, processes tasks, and returns results or status updates. It can also be used
  independently of Koog agents.
- [A2A Client](a2a-client.md) is a client application or agent that initiates communication with an A2A server using the
  A2A protocol. It can also be used independently of Koog agents.
- [A2A Koog Integration](a2a-koog-integration.md) is a set of classes and utilities that simplify the integration of A2A
  with Koog Agents. It contains components (A2A features and nodes) for seamless A2A agent connections and communication
  within the Koog framework.


For more examples, follow
the [examples](https://github.com/JetBrains/koog/tree/develop/examples/simple-examples/src/main/kotlin/ai/koog/agents/example/a2a)
