# PubSub Feature for Koog Agents

The PubSub feature provides first-class publish-subscribe messaging capabilities for Koog agents, enabling real-time communication, coordination, and event-driven architectures.

## Overview

This feature allows agents to:
- **Publish messages** to topics for other agents or systems to consume
- **Subscribe to topics** to receive real-time notifications and commands
- **Coordinate across multiple agents** in distributed deployments
- **Integrate with planning agents** for sophisticated workflow coordination
- **Monitor agent lifecycle events** through automated event publishing

## Architecture

The PubSub feature follows the standard `agents-features-common` pattern with:

- **Provider-agnostic interface**: `PubSubProvider` abstracts different messaging systems
- **Built-in providers**: Redis and Google Cloud PubSub implementations
- **Feature integration**: Seamless integration with agent lifecycle and events
- **Message filtering**: Configurable publish and receive filters
- **Auto-subscription**: Automatic topic subscription on agent startup

## Supported Providers

### Redis PubSub Provider
- **Use case**: High-performance, low-latency messaging for distributed systems
- **Features**: Connection pooling, pattern subscriptions, clustering support
- **Best for**: Real-time coordination, ephemeral messaging, development environments

### Google Cloud PubSub Provider  
- **Use case**: Enterprise-scale messaging with guaranteed delivery
- **Features**: Automatic topic/subscription management, message ordering, dead letter queues
- **Best for**: Production deployments, cross-region messaging, audit trails

### No-Op Provider
- **Use case**: Testing and environments where PubSub is disabled
- **Features**: Discards all messages, returns empty flows
- **Best for**: Unit testing, feature toggles, resource-constrained environments

## Quick Start

### Basic Setup

```kotlin
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.feature.PubSubFeatureConfig
import ai.koog.agents.features.pubsub.providers.redis.RedisPubSubProvider

// Configure Redis provider
val redisProvider = RedisPubSubProvider(
    host = "localhost",
    port = 6379
)

// Configure PubSub feature
val pubsubConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    autoSubscribeTopics = listOf("agent-commands", "notifications")
    publishAgentEvents = true
}

// Create agent with PubSub
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    features = listOf(PubSub.Feature to pubsubConfig)
)
```

### Publishing Messages

```kotlin
// Simple string message
redisProvider.publish("notifications", "Agent started successfully")

// Rich message with attributes
redisProvider.publish(
    PubSubStringMessage(
        topic = "agent-commands",
        content = "execute_task",
        attributes = mapOf(
            "priority" to "high",
            "source" to "scheduler",
            "task-id" to "task-123"
        )
    )
)
```

### Subscribing to Messages

```kotlin
// Subscribe to single topic
redisProvider.subscribe("agent-commands").collect { message ->
    println("Received: ${message.content} from ${message.topic}")
    
    // Process message based on attributes
    when (message.attributes["priority"]) {
        "high" -> processHighPriorityCommand(message)
        "normal" -> processNormalCommand(message)
    }
    
    // Acknowledge message processing
    message.acknowledge()
}

// Subscribe to multiple topics
redisProvider.subscribe(listOf("commands", "notifications", "alerts"))
    .collect { message ->
        routeMessage(message.topic, message)
    }
```

## Feature Configuration

### PubSubFeatureConfig Properties

```kotlin
class PubSubFeatureConfig {
    // Provider configuration
    var provider: PubSubProvider = NoPubSubProvider()
    var providerConfig: Map<String, Any> = emptyMap()
    
    // Subscription management
    var autoSubscribeTopics: List<String> = emptyList()
    var maxConcurrentMessages: Int = 10
    var autoAcknowledge: Boolean = true
    
    // Event publishing
    var publishAgentEvents: Boolean = false
    var publishToolEvents: Boolean = false
    var publishLLMEvents: Boolean = false
    var agentEventTopic: String = "agent-events"
    var toolEventTopic: String = "tool-events"
    var llmEventTopic: String = "llm-events"
    
    // Message filtering
    var publishFilter: (PubSubMessage) -> Boolean = { true }
    var receiveFilter: (PubSubMessage) -> Boolean = { true }
}
```

### Event Publishing

The feature can automatically publish agent lifecycle events:

```kotlin
val config = PubSubFeatureConfig().apply {
    publishAgentEvents = true  // Agent start/stop/error events
    publishToolEvents = true   // Tool execution events
    publishLLMEvents = true    // LLM interaction events
    
    // Custom topic names
    agentEventTopic = "production-agent-events"
    toolEventTopic = "tool-executions"
    llmEventTopic = "llm-interactions"
}
```

Events are published as structured messages with attributes:

```kotlin
// Agent event example
{
    "topic": "agent-events",
    "content": "agent_started",
    "attributes": {
        "agent-id": "agent-123",
        "strategy": "SimplePlanner", 
        "timestamp": "1642608000000",
        "event-type": "lifecycle"
    }
}

// Tool event example  
{
    "topic": "tool-events",
    "content": "tool_executed",
    "attributes": {
        "tool-name": "FileReader",
        "execution-time": "150ms",
        "result": "success",
        "agent-id": "agent-123"
    }
}
```

## Message Filtering

Configure filters to control which messages are published or received:

```kotlin
val config = PubSubFeatureConfig().apply {
    // Only publish high-priority messages
    publishFilter = { message ->
        message.attributes["priority"] in setOf("high", "critical") ||
        message.topic.startsWith("emergency-")
    }
    
    // Only receive messages from trusted sources
    receiveFilter = { message ->
        val trustedSources = setOf("scheduler", "monitor", "admin")
        message.attributes["source"] in trustedSources
    }
}
```

## Advanced Usage Examples

### Planning Agent Coordination

```kotlin
// Configure planning coordination
val planningConfig = PubSubFeatureConfig().apply {
    provider = RedisPubSubProvider("localhost", 6379)
    autoSubscribeTopics = listOf("plan-coordination", "plan-feedback", "resource-updates")
    publishAgentEvents = true
    agentEventTopic = "planning-events"
}

// Create planning agent with PubSub
val planningAgent = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlannerWithCritic("CoordinatedPlanner", AllToolsStrategy(), executor)
) {
    install(PubSub) {
        provider = RedisPubSubProvider("localhost", 6379)
        autoSubscribeTopics = listOf("plan-coordination", "resource-updates")
        publishAgentEvents = true
    }
}

// Coordinate plan execution
suspend fun coordinatedPlanning() {
    planningAgent.publish("plan-coordination", "plan_started", 
        mapOf("plan-id" to "plan-123", "agent-id" to "planner-1"))
    
    planningAgent.subscribe("plan-coordination").collect { message ->
        when (message.attributes["message-type"]) {
            "step-completed" -> updatePlanProgress(message)
            "resource-conflict" -> handleResourceConflict(message)
        }
    }
}
```

### Persistence Coordination Integration

```kotlin
// Agent with coordinated PubSub + Persistence
val agent = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlannerWithCritic("TaskPlanner", AllToolsStrategy(), executor)
) {
    install(PubSub) {
        provider = RedisPubSubProvider("localhost", 6379)
        autoSubscribeTopics = listOf("checkpoint-coordination")
        publishAgentEvents = true
    }
    
    install(Persistency) {
        strategy = PersistencyStrategy.Dynamic { context, registry ->
            // Publish checkpoint notifications via PubSub
            when (isExecutingPlanStep(context)) {
                true -> {
                    // Notify other agents of progress
                    publishCheckpointEvent("plan_step_saved", context)
                    CoordinationStrategies.Single(redisId)
                }
                false -> {
                    publishCheckpointEvent("plan_completed", context)
                    CoordinationStrategies.WriteToAll(listOf(redisId, postgresId))
                }
            }
        }
        registry.register(RedisPersistencyStorageProvider(), "redis")
        registry.register(PostgresPersistencyStorageProvider(), "postgres")
    }
}
```

### Multi-Tenant Configuration

```kotlin
// Tenant-aware messaging
val tenantConfig = PubSubFeatureConfig().apply {
    autoSubscribeTopics = listOf("tenant-$tenantId-commands", "global-announcements")
    publishFilter = { message -> message.attributes["tenant-id"] == tenantId }
    receiveFilter = { message -> 
        message.attributes["tenant-id"] == tenantId || message.topic.startsWith("global-")
    }
}
```

## Error Handling & Monitoring

```kotlin
// Error handling
try {
    provider.publish("topic", "message")
} catch (e: PubSubException) {
    logger.error("${e.operation} failed on ${e.topic}: ${e.message}")
}

// Health monitoring
val health = provider.getHealthInfo()
if (health["connected"] != true) {
    reconnectProvider()
}
```

## Testing

Use `NoPubSubProvider()` for unit tests to avoid real messaging infrastructure:

```kotlin
@Test
fun testAgentWithPubSub() = runTest {
    val agent = AIAgent(executor, strategy) {
        install(PubSub) {
            provider = NoPubSubProvider() // No-op for testing
            autoSubscribeTopics = listOf("test-topic")
        }
    }
    // Test agent behavior...
}
```

## API Reference

### Core Interfaces

- `PubSubProvider`: Main provider interface
- `PubSubMessage`: Message abstraction
- `ReceivedMessage`: Incoming message representation  
- `PubSubFeatureConfig`: Feature configuration
- `PubSubException`: Error handling

### Built-in Providers

- `RedisPubSubProvider`: Redis-based messaging
- `GcpPubSubProvider`: Google Cloud PubSub
- `NoPubSubProvider`: No-op implementation

### Message Types

- `PubSubStringMessage`: Simple string messages
- `MessagePublishedEvent`: Published message events
- `MessageReceivedEvent`: Received message events

For detailed API documentation, see the KDoc comments in the source files.