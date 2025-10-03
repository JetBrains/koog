# Module agents-features-a2a-server

Agent feature for enabling A2A server capabilities within Koog agents.

## Overview

This module provides an agent feature that enables Koog agents to act as A2A servers, allowing them to receive and process requests from A2A clients. When installed, agents can access the A2A request context and event processor, enabling them to handle incoming messages, manage tasks, interact with storage, and send events back to clients.

## Key Components

- **`A2AAgentServer`**: Feature providing access to A2A request context and event processor
- **Agent Nodes**: Pre-built nodes for messaging, task events, and storage operations

## Related Modules

- `agents-features-a2a-client`: Client-side A2A agent feature
- `agents-features-a2a-core`: Core A2A utilities and converters
