# PubSub Feature Examples

This directory contains examples demonstrating the PubSub feature for multiagent coordination in Koog.

## Overview

The PubSub feature enables real-time communication and coordination between distributed Koog agents through a provider-agnostic messaging system.

## Examples

### 1. Multiagent Coordination (`main()` function)

Demonstrates a practical multiagent system where:
- A **Coordinator Agent** receives complex tasks and breaks them down
- Specialized **Worker Agents** handle different types of subtasks
- Agents communicate through message passing to coordinate work
- Results are aggregated for a final comprehensive response

**Key concepts shown:**
- Agent-to-agent task delegation
- Asynchronous message handling
- Result aggregation and coordination
- Health monitoring

### 2. Basic PubSub Operations (`basicPubSubExample()`)

Shows fundamental PubSub operations:
- Publishing messages with attributes
- Subscribing to topics
- Message acknowledgment
- Provider health monitoring

## Provider Options

The examples use `InMemoryPubSubProvider` for simplicity, but production systems can use:

### Redis Provider
```kotlin
install(PubSub) {
    provider = RedisPubSubProvider(
        redisUri = RedisURI.create("redis://localhost:6379"),
        keyPrefix = "koog:",
        connectionTimeout = 5000
    )
}
```

### Google Cloud PubSub Provider
```kotlin
install(PubSub) {
    provider = GCPPubSubProvider(
        projectId = "your-gcp-project",
        subscriptionPrefix = "koog-agent-",
        autoCreateTopics = true,
        autoCreateSubscriptions = true
    )
}
```

## Running the Examples

### Prerequisites
- Valid OpenAI API key (set in `ApiKeyService`)
- Gradle build environment

### Execute Main Example
```bash
./gradlew :examples:run --args="ai.koog.agents.example.features.pubsub.PubSubkt"
```

### For Redis Provider (Optional)
1. Start Redis server:
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```

2. Update the example to use `RedisPubSubProvider`

### For GCP PubSub Provider (Optional)
1. Set up GCP credentials:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
   ```

2. Update the example to use `GCPPubSubProvider`

## Architecture Patterns

### Message Topics
- `task-results`: Worker agents report completion
- `worker-status`: Health and status updates
- `text-tasks`: Tasks for text processing workers
- `data-tasks`: Tasks for data analysis workers
- `project-complete`: Final project completion notifications

### Message Attributes
Messages include metadata for routing and processing:
```kotlin
mapOf(
    "worker" to "text-worker",
    "priority" to "high", 
    "status" to "completed",
    "deadline" to "immediate"
)
```

### Error Handling
The examples demonstrate:
- Message acknowledgment for successful processing
- Resource cleanup with `use` blocks
- Provider health monitoring
- Graceful shutdown

## Production Considerations

1. **Provider Selection**: Choose based on scale and requirements
   - InMemory: Development/testing only
   - Redis: High-performance, single data center
   - GCP PubSub: Enterprise scale, multi-region

2. **Message Durability**: GCP PubSub provides guaranteed delivery, Redis and InMemory do not

3. **Scaling**: Consider message volume, topic fan-out, and subscription patterns

4. **Monitoring**: Use health info and metrics for operational visibility

5. **Security**: Configure authentication and network security for production providers