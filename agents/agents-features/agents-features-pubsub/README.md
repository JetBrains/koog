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

## Planning Agent Integration

The PubSub feature is designed to work seamlessly with planning agents:

```kotlin
import ai.koog.agents.planners.SimplePlannerWithCritic

// Configure planning coordination
val planningConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    // Subscribe to planning-specific topics
    autoSubscribeTopics = listOf(
        "plan-coordination",     // Cross-agent plan coordination
        "plan-feedback",         // Plan evaluation feedback  
        "resource-updates"       // Resource availability
    )
    
    // Publish planning lifecycle events
    publishAgentEvents = true
    agentEventTopic = "planning-events"
    
    // Filter planning messages
    publishFilter = { message ->
        message.topic.contains("plan") || 
        message.attributes.containsKey("planning-context")
    }
}

// Create planning agent
val planningAgent = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlannerWithCritic("CoordinatedPlanner", AllToolsStrategy(), executor),
    features = listOf(PubSub.Feature to planningConfig)
)
```

### Planning Coordination Patterns

```kotlin
// Coordinate plan execution across agents
suspend fun coordinatedPlanning() {
    // Publish plan start
    provider.publish(
        "plan-coordination",
        "plan_started", 
        mapOf(
            "plan-id" to "plan-123",
            "agent-id" to "planner-1",
            "estimated-steps" to "5"
        )
    )
    
    // Listen for coordination signals
    provider.subscribe("plan-coordination").collect { message ->
        when (message.attributes["message-type"]) {
            "step-completed" -> updatePlanProgress(message)
            "resource-conflict" -> handleResourceConflict(message)
            "plan-evaluation" -> processPlanFeedback(message)
        }
    }
}
```

## Persistence Coordination

Integrate PubSub with the PersistencyStrategy system for coordinated checkpointing:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    strategy = planningStrategy,
    features = listOf(PubSub.Feature to pubsubConfig),
    persistencyStrategy = PersistencyStrategy.Dynamic { context, registry ->
        // Publish checkpoint notifications
        provider.publish(
            "checkpoint-coordination",
            "checkpoint_saved",
            mapOf(
                "type" to context.checkpointType,
                "agent-id" to context.agentId,
                "persistence-provider" to "redis"
            )
        )
        
        // Select coordination based on checkpoint type
        when (context.checkpointType) {
            "plan_step_progress" -> CoordinationStrategies.Single(redisId)
            "plan_completed" -> CoordinationStrategies.WriteToAll(listOf(redisId, postgresId))
            else -> CoordinationStrategies.Single(redisId)
        }
    }
)
```

## Multi-Tenant Support

Configure tenant-aware messaging:

```kotlin
class TenantPubSubConfig(private val tenantId: String) {
    fun createConfig(): PubSubFeatureConfig {
        return PubSubFeatureConfig().apply {
            // Tenant-specific topics
            autoSubscribeTopics = listOf(
                "tenant-$tenantId-commands",
                "tenant-$tenantId-notifications"
            )
            
            // Tenant isolation
            publishFilter = { message ->
                message.attributes["tenant-id"] == tenantId
            }
            
            receiveFilter = { message ->
                message.attributes["tenant-id"] == tenantId ||
                message.topic.startsWith("global-")
            }
        }
    }
}
```

## Error Handling

The PubSub feature includes comprehensive error handling:

```kotlin
// Provider-level exceptions
try {
    provider.publish("topic", "message")
} catch (e: PubSubException) {
    println("Operation ${e.operation} failed on topic ${e.topic}: ${e.message}")
    // Handle specific error types
    when (e.operation) {
        "publish" -> handlePublishError(e)
        "subscribe" -> handleSubscribeError(e)
    }
}

// Connection monitoring
val healthInfo = provider.getHealthInfo()
if (healthInfo["connected"] != true) {
    // Handle disconnection
    reconnectProvider()
}
```

## Performance Considerations

### Message Throughput

- **Redis**: Handles 100K+ messages/second with proper configuration
- **GCP PubSub**: Scales automatically, handles millions of messages/day
- **Batching**: Use batch publishing for high-volume scenarios

### Memory Management

- **maxConcurrentMessages**: Limits concurrent message processing to prevent memory issues
- **autoAcknowledge**: Automatically acknowledges messages for faster processing
- **Flow backpressure**: Built-in Flow mechanisms handle backpressure automatically

### Connection Pooling

Redis provider supports connection pooling:

```kotlin
val redisProvider = RedisPubSubProvider(
    host = "localhost",
    port = 6379,
    connectionPoolSize = 10,  // Multiple connections for high concurrency
    connectionTimeout = 5000
)
```

## Monitoring and Observability

### Health Monitoring

```kotlin
// Check provider health
val health = provider.getHealthInfo()
println("Provider: ${health["provider"]}")
println("Connected: ${health["connected"]}")
println("Active subscriptions: ${health["subscriptions"]}")

// Redis-specific metrics
println("Connection pool size: ${health["pool-size"]}")
println("Active connections: ${health["active-connections"]}")
```

### Event Metrics

Enable event publishing to monitor agent behavior:

```kotlin
val config = PubSubFeatureConfig().apply {
    publishAgentEvents = true
    publishToolEvents = true
    publishLLMEvents = true
}

// Subscribe to metrics topics
provider.subscribe("agent-events").collect { event ->
    when (event.content) {
        "agent_started" -> metrics.incrementAgentStarts()
        "tool_executed" -> metrics.recordToolExecution(event.attributes)
        "llm_request" -> metrics.recordLLMUsage(event.attributes)
    }
}
```

## Testing

### Unit Testing with No-Op Provider

```kotlin
@Test
fun testAgentWithPubSub() = runTest {
    val config = PubSubFeatureConfig().apply {
        provider = NoPubSubProvider()  // No real messaging in tests
        autoSubscribeTopics = listOf("test-topic")
    }
    
    val agent = AIAgent(
        promptExecutor = mockExecutor,
        strategy = SimpleStrategy(),
        features = listOf(PubSub.Feature to config)
    )
    
    // Test agent behavior without actual messaging
    agent.start()
    // assertions...
}
```

### Integration Testing

```kotlin
@Test  
fun testRedisIntegration() = runTest {
    val redisProvider = RedisPubSubProvider("localhost", 6379)
    
    // Test publish/subscribe flow
    val receivedMessages = mutableListOf<ReceivedMessage>()
    
    launch {
        redisProvider.subscribe("test-topic").collect { message ->
            receivedMessages.add(message)
        }
    }
    
    delay(100) // Allow subscription to establish
    
    redisProvider.publish("test-topic", "test message")
    
    delay(100) // Allow message delivery
    
    assertEquals(1, receivedMessages.size)
    assertEquals("test message", receivedMessages[0].content)
}
```

## Best Practices

### Topic Naming

- Use hierarchical naming: `agent-events/lifecycle`, `planning/coordination`
- Include environment: `prod-agent-events`, `dev-notifications`  
- Version topics: `api-v1-commands`, `api-v2-commands`

### Message Design

- Keep messages small and focused
- Use attributes for routing metadata
- Include correlation IDs for request tracking
- Add timestamps for ordering and debugging

### Resource Management

- Always close providers in finally blocks or use `use` blocks
- Limit concurrent subscriptions to prevent resource exhaustion
- Monitor connection health and implement reconnection logic
- Use circuit breakers for provider failures

### Security

- Validate message content before processing
- Use authentication credentials for production providers
- Implement rate limiting to prevent abuse
- Filter sensitive information from published events

## Examples

See [EXAMPLES.md](EXAMPLES.md) for comprehensive usage examples including:
- Basic Redis and GCP PubSub setup
- Planning agent coordination patterns
- Multi-tenant configurations
- High-throughput optimizations
- Custom provider implementations

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