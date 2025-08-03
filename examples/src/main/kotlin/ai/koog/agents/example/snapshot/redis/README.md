# Redis Persistence Example

Demonstrates using Redis as a persistence backend for agent checkpoints.

## Quick Start

1. **Start Redis**
   ```bash
   docker-compose up -d
   ```

2. **Run the examples**
   ```bash
   # Basic Redis provider example
   ./gradlew :examples:runExampleRedisPersistentAgent
   
   # Pooled Redis provider example (for high-concurrency scenarios)
   ./gradlew :examples:runExamplePooledRedisPersistentAgent
   ```

## What It Does

### Basic Redis Example (`RedisPersistentAgentExample.kt`)
- Uses single-connection Redis provider for simple scenarios
- Saves checkpoints at each node execution
- Demonstrates the "teleport" feature - jumping back to a previous checkpoint
- Shows how a new agent instance can restore from the latest checkpoint
- Ideal for development and single-agent applications

### Pooled Redis Example (`PooledRedisPersistentAgentExample.kt`)
- Uses connection-pooled Redis provider for high-concurrency scenarios
- Demonstrates multiple agents running concurrently
- Shows pool configuration and monitoring
- Displays pool statistics and utilization warnings
- Ideal for production environments with multiple agents

## Redis Data Structure

```
agent:example:persistent-agent-example:checkpoint:{id}  → Individual checkpoint data (JSON)
agent:example:persistent-agent-example:meta             → Sorted set of checkpoint IDs (by timestamp)
```

## Providers

### Basic Provider

For single-agent scenarios or development:

```kotlin
JVMRedisPersistencyStorageProvider(
    persistenceId = "my-agent",
    redisUri = RedisURI.create("redis://localhost:6379"),
    keyPrefix = "agent:checkpoint",
    ttlSeconds = 3600  // Optional TTL for checkpoints
)
```

### Pooled Provider

For high-concurrency production environments:

```kotlin
val poolConfig = PooledJVMRedisPersistencyStorageProvider.PoolConfig(
    minIdle = 2,        // Minimum idle connections
    maxIdle = 8,        // Maximum idle connections  
    maxTotal = 20,      // Maximum total connections
    testOnBorrow = true, // Validate on borrow
    testOnReturn = true  // Validate on return
)

val provider = PooledJVMRedisPersistencyStorageProvider(
    persistenceId = "my-agent",
    redisUri = RedisURI.create("redis://localhost:6379"),
    keyPrefix = "agent:checkpoint",
    ttlSeconds = 3600,
    poolConfig = poolConfig
)
```

#### Pool Monitoring

```kotlin
val stats = provider.getPoolStats()
println("Active connections: ${stats.numActive}")
println("Pool utilization: ${stats.utilizationPercent}%")
```

#### When to Use Pooled Provider

- High-concurrency applications with many concurrent checkpoint operations
- Production environments with multiple agent instances  
- Applications requiring better resource utilization and connection management

Always close providers when done: `provider.close()`

## Environment Variables

- `REDIS_URI` - Override default Redis connection (default: `redis://localhost:6379`)

## Cleanup

```bash
docker-compose down -v  # Stop and remove data
```