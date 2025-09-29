# Agent Cards

This page provides details about Agent Cards in the A2A protocol implementation of the Koog agentic framework.

## Overview

Agent Cards are metadata documents that describe an agent's capabilities, identity, and interface. They serve as a machine-readable way for clients to discover what an agent can do and how to interact with it properly.

In the A2A protocol, Agent Cards are essential for the following functions:

- Discovering agent capabilities
- Determining supported features (streaming, push notifications, state transition history)
- Verifying authentication requirements
- Providing human-readable information about the agent

## Structure

An Agent Card is represented by the `AgentCard` class in Koog.

### AgentCapabilities

The `AgentCapabilities` class defines what features the agent supports. Here are the details about the available capabilities:

- **streaming**: indicates whether the agent supports streaming responses
- **pushNotifications**: indicates whether the agent supports push notifications for task updates
- **stateTransitionHistory**: indicates whether the agent provides a history of state transitions for a task. For more information about states, see [Task states](a2a-messages-tasks-artifacts.md#task-states).

### AgentAuthentication

The `AgentAuthentication` class defines the authentication requirements for the agent. Includes the following properties:

- **type**: the authentication type. For example, "bearer", "oauth2", "api-key"
- **instructions**: optional human-readable instructions for authentication
- **metadata**: optional machine-readable metadata for authentication

## Basic and extended Agent Cards

The A2A protocol supports two types of Agent Cards:

1. **Basic Agent Card**: available to all clients without authentication
2. **Extended Agent Card**: available only to authenticated clients

The basic Agent Card typically contains minimal information, while the extended Agent Card may include additional capabilities or metadata that are only available to authenticated clients.

## Retrieving Agent Cards

### Client-side

On the client side, `A2AClient` uses `AgentCardResolver` to retrieve the Agent Card:

```kotlin
// Connect to the agent and retrieve its Agent Card
client.connect()

// Get the cached Agent Card
val agentCard = client.cachedAgentCard()

// Check if the agent supports streaming
if (agentCard.capabilities.streaming == true) {
    // Use streaming API
}

// Check if the agent supports push notifications
if (agentCard.capabilities.pushNotifications == true) {
    // Configure push notifications
}
```
<!--- KNIT example-a2a-agent-cards-01.kt -->

For authenticated extended Agent Cards:

```kotlin
// Check if the agent supports authenticated extended cards
if (agentCard.supportsAuthenticatedExtendedCard == true) {
    // Get the authenticated extended Agent Card
    val request = Request<Nothing?>(data = null)
    val response = client.getAuthenticatedExtendedAgentCard(request)
    val extendedCard = response.data
}
```
<!--- KNIT example-a2a-agent-cards-02.kt -->

### Server-side

On the server side, `A2AServer` is initialized with an Agent Card:

```kotlin
val agentCard = AgentCard(
    name = "My Agent",
    description = "A helpful agent",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = false
    )
)

val server = A2AServer(
    agentExecutor = myAgentExecutor,
    agentCard = agentCard
)
```
<!--- KNIT example-a2a-agent-cards-03.kt -->

For authenticated extended Agent Cards:

```kotlin
val basicAgentCard = AgentCard(
    name = "My Agent",
    description = "A helpful agent",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = false,
        stateTransitionHistory = false
    ),
    supportsAuthenticatedExtendedCard = true
)

val extendedAgentCard = AgentCard(
    name = "My Agent (Extended)",
    description = "A helpful agent with extended capabilities",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = true,
        stateTransitionHistory = false,
    )
)

val server = A2AServer(
    agentExecutor = myAgentExecutor,
    agentCard = basicAgentCard,
    agentCardExtended = extendedAgentCard
)
```
<!--- KNIT example-a2a-agent-cards-04.kt -->

## Custom Agent Card resolvers

You can implement your own `AgentCardResolver` to retrieve Agent Cards in a custom way:

```kotlin
class MyCustomAgentCardResolver : AgentCardResolver {
    override suspend fun resolve(): AgentCard {
        // Custom implementation to retrieve the Agent Card
        return AgentCard(
            name = "Custom Agent",
            description = "A custom agent",
            version = "1.0.0",
            capabilities = AgentCapabilities(
                streaming = true,
                pushNotifications = true,
                stateTransitionHistory = true
            )
        )
    }
}
```
<!--- KNIT example-a2a-agent-cards-05.kt -->

Koog provides several built-in implementations:

### UrlAgentCardResolver

Retrieves the Agent Card from a URL:

```kotlin
val resolver = UrlAgentCardResolver(url = "https://example.com/a2a/.well-known/agent-card.json")
```
<!--- KNIT example-a2a-agent-cards-06.kt -->

### StaticAgentCardResolver

Uses a predefined Agent Card:

```kotlin
val agentCard = AgentCard(
    name = "Static Agent",
    description = "A static agent",
    version = "1.0.0",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = false,
        stateTransitionHistory = false,
    )
)

val resolver = StaticAgentCardResolver(agentCard = agentCard)
```
<!--- KNIT example-a2a-agent-cards-07.kt -->

## Best practices

When creating Agent Cards, consider the following best practices:

1. **Be descriptive**: provide clear and concise information about your agent's purpose and capabilities.
2. **Be accurate**: only advertise capabilities that your agent actually supports.
3. **Be consistent**: ensure that the Agent Card accurately reflects the behavior of your agent.
4. **Be secure**: use authenticated extended Agent Cards for sensitive capabilities.
5. **Be forward-compatible**: include optional fields that might be used in future versions of the A2A protocol.

Example of a well-designed Agent Card:

```kotlin
val agentCard = AgentCard(
    name = "Customer Support Agent",
    protocolVersion = "0.3.0",
    description = "An agent that can answer customer support questions and create support tickets",
    version = "2.1.0",
    preferredTransport = TransportProtocol.JSONRPC,
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = true
    ),
    authentication = AgentAuthentication(
        type = "bearer",
        instructions = "Provide a valid JWT token in the Authorization header"
    ),
    supportsAuthenticatedExtendedCard = true,
    metadata = buildJsonObject {
        put("vendor", "Example Corp")
        put("supportEmail", "support@example.com")
        put("documentationUrl", "https://docs.example.com/agent")
    }
)
```
<!--- KNIT example-a2a-agent-cards-08.kt -->
