# PersistencyStrategy Example

This example demonstrates the flexible persistence provider selection strategies available in the Koog agent framework.

## Overview

The `PersistencyStrategy` pattern allows agents to use different persistence providers based on context, similar to how `ToolSelectionStrategy` works for tool selection. This enables:

- **Single Provider**: Traditional single-provider persistence
- **Failover**: Automatic failover to backup providers
- **Dynamic Selection**: Context-aware provider selection
- **Hybrid Strategies**: Pre-configured patterns for common use cases

## Running the Example

```bash
./gradlew :examples:persistencyStrategyExample
```

## Strategy Types

### 1. Single Strategy
The simplest approach - uses one provider for all operations:
```kotlin
strategy = PersistencyStrategy.Single(provider)
```

### 2. Failover Strategy
Provides high availability with automatic failover:
```kotlin
strategy = PersistencyStrategy.Failover(
    listOf(primaryProvider, backupProvider1, backupProvider2)
)
```

### 3. Dynamic Strategy
Selects providers based on operation context:
```kotlin
strategy = PersistencyStrategy.Dynamic(
    providers = mapOf("fast" to redis, "durable" to postgres),
    selector = { context -> 
        if (context.operation is SaveCheckpoint) "fast" else "durable"
    }
)
```

### 4. Hybrid Strategy
Optimized for mixed workloads (ephemeral + durable):
```kotlin
strategy = PersistencyStrategy.Hybrid(
    ephemeralProvider = redisProvider,  // For mid-execution
    durableProvider = postgresProvider,  // For session persistence
    criticalProvider = s3Provider       // For critical checkpoints
)
```

## Use Cases

- **High Availability**: Use failover strategy with multiple providers
- **Performance Optimization**: Route frequent checkpoints to fast storage
- **Cost Optimization**: Use expensive durable storage only when needed
- **Compliance**: Route sensitive data to compliant storage providers
- **Multi-Region**: Select providers based on geographic location

## Integration with Existing Providers

Works seamlessly with all existing persistence providers:
- Redis (ephemeral, distributed)
- PostgreSQL/MySQL (durable, queryable)
- File-based (simple, portable)
- In-memory (testing, development)
- Custom providers

## Future Enhancements

The example shows the foundation for:
- LLM-driven provider selection
- Automatic performance-based routing
- Cost-aware persistence strategies
- Multi-provider transactions