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
- Cross-process agent coordination through file-based messaging
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

The examples use `LocalFilePubSubProvider` which enables true cross-process coordination. Other options include:

### LocalFile Provider (Default)
```kotlin
install(PubSub) {
    provider = LocalFilePubSubProvider(
        // Uses temp directory by default
        pollingIntervalMs = 100,
        cleanupIntervalMs = 60_000
    )
}
```
**Benefits:** No external dependencies, true cross-process coordination  
**Limitations:** Single machine only, filesystem performance

### InMemory Provider (Single Process Only)
```kotlin  
install(PubSub) {
    provider = InMemoryPubSubProvider() // Only works within same process
}
```
**Benefits:** Fastest for single-process testing  
**Limitations:** Cannot coordinate across processes

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
- Docker and Docker Compose (for Redis and GCP examples)

### 1. LocalFile Example (Default)
```bash
./gradlew :examples:run --args="ai.koog.agents.example.features.pubsub.PubSubkt"
```
**Benefits:** No setup required, works across processes

### 2. Redis Example (High Performance)
1. Start Redis with Docker Compose:
   ```bash
   cd examples/src/main/kotlin/ai/koog/agents/example/features/pubsub/
   docker-compose -f docker-compose-redis.yaml up -d
   ```

2. Verify Redis is running:
   ```bash
   docker exec koog-pubsub-redis redis-cli ping
   # Should return: PONG
   ```

3. Run the Redis example:
   ```bash
   ./gradlew :examples:run --args="ai.koog.agents.example.features.pubsub.RedisPubSubExamplekt"
   ```

4. Stop Redis when done:
   ```bash
   docker-compose -f docker-compose-redis.yaml down
   ```

### 3. GCP PubSub Example (Enterprise Scale)

#### Option A: Local Development with Emulator
1. Start GCP Pub/Sub emulator:
   ```bash
   cd examples/src/main/kotlin/ai/koog/agents/example/features/pubsub/
   docker-compose -f docker-compose-gcp.yaml up -d
   ```

2. Set environment variable:
   ```bash
   export PUBSUB_EMULATOR_HOST=localhost:8085
   ```

3. Run the GCP example:
   ```bash
   ./gradlew :examples:run --args="ai.koog.agents.example.features.pubsub.GCPPubSubExamplekt"
   ```

4. Stop emulator when done:
   ```bash
   docker-compose -f docker-compose-gcp.yaml down
   ```

#### Option B: Real GCP Project
1. Set up GCP credentials:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
   ```

2. Enable Pub/Sub API in your GCP project

3. Update `PROJECT_ID` in `GCPPubSubExample.kt`

4. Run the example (unset emulator variable):
   ```bash
   unset PUBSUB_EMULATOR_HOST
   ./gradlew :examples:run --args="ai.koog.agents.example.features.pubsub.GCPPubSubExamplekt"
   ```

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

## Docker Compose Configurations

### Redis Setup (`docker-compose-redis.yaml`)
- **Redis 7 Alpine**: Lightweight, high-performance
- **Persistence**: Data persisted with AOF (Append Only File)
- **Health Checks**: Automatic Redis ping verification
- **Port**: 6379 (standard Redis port)
- **Volume**: Persistent storage for message durability

### GCP Pub/Sub Emulator (`docker-compose-gcp.yaml`) 
- **Official GCP SDK**: Cloud SDK with Pub/Sub emulator
- **Project ID**: `koog-pubsub-dev` (configurable)
- **Port**: 8085 (emulator endpoint)
- **Health Checks**: HTTP endpoint verification
- **Debug Mode**: Verbose logging for development

## Production Considerations

1. **Provider Selection**: Choose based on scale and requirements
   - **InMemory**: Single-process testing only
   - **LocalFile**: Cross-process local development
   - **Redis**: High-performance, single data center
   - **GCP PubSub**: Enterprise scale, multi-region

2. **Message Durability**: 
   - GCP PubSub: Guaranteed delivery with configurable retention
   - Redis: Persistence with AOF/RDB, but not guaranteed delivery
   - LocalFile: File-based persistence, manual cleanup required
   - InMemory: No persistence

3. **Scaling**: Consider message volume, topic fan-out, and subscription patterns
   - **LocalFile**: Single machine only, filesystem limited
   - **Redis**: High throughput, clustering support
   - **GCP PubSub**: Auto-scaling, global distribution

4. **Monitoring**: Use health info and metrics for operational visibility
   - All providers expose health endpoints
   - Redis and GCP provide extensive metrics
   - Docker Compose includes health checks

5. **Security**: Configure authentication and network security for production providers
   - **Redis**: AUTH, TLS encryption, network isolation
   - **GCP PubSub**: IAM roles, service accounts, VPC controls
   - **LocalFile**: File system permissions, temp directory security