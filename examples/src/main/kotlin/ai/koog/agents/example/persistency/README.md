# PersistencyStrategy Example

This example demonstrates the flexible persistence provider coordination strategies available in the Koog agent framework.

## Overview

The `PersistencyStrategy` pattern provides an open-ended system for coordinating multiple persistence providers with unlimited customization. This enables:

- **Fixed Coordination**: Use predetermined coordination patterns
- **Dynamic Selection**: Context-aware coordination selection
- **Custom Logic**: Implement any coordination pattern imaginable
- **Built-in Patterns**: Ready-to-use implementations for common scenarios

## Running the Example

```bash
./gradlew :examples:persistencyStrategyExample
```

## Strategy Types

### 1. Fixed Strategy with Single Provider
The simplest approach - uses one provider for all operations:
```kotlin
val registry = ProviderRegistry()
val providerId = registry.register(provider, "main")
strategy = PersistencyStrategy.Fixed(
    CoordinationStrategies.Single(providerId)
)
```

### 2. Fixed Strategy with Backup Coordination
Provides high availability with automatic failover:
```kotlin
val primaryId = registry.register(primaryProvider, "primary")
val backupIds = backupProviders.mapIndexed { i, provider ->
    registry.register(provider, "backup-$i")
}
strategy = PersistencyStrategy.Fixed(
    CoordinationStrategies.WriteWithBackup(primaryId, backupIds)
)
```

### 3. Dynamic Strategy
Selects coordination patterns based on agent context:
```kotlin
strategy = PersistencyStrategy.Dynamic { context, registry ->
    when {
        context.agentContext.id.contains("critical") -> 
            CoordinationStrategies.WriteToAll(listOf(redisId, postgresId))
        context.agentContext.id.contains("fast") -> 
            CoordinationStrategies.Single(redisId)
        else -> 
            CoordinationStrategies.WriteWithBackup(postgresId, listOf(redisId))
    }
}
```

### 4. Custom Coordination Strategy
Implement unlimited custom coordination logic:
```kotlin
class TimeBasedCoordination(
    private val fastId: ProviderId,
    private val durableId: ProviderId
) : CoordinationStrategy {
    override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
        val currentHour = Clock.System.now().hour
        val providerId = if (currentHour in 9..17) fastId else durableId
        registry.get(providerId).saveCheckpoint(checkpoint)
    }
    // ... implement other methods
}

## Use Cases

- **High Availability**: Use WriteToAll or WriteWithBackup coordination
- **Performance Optimization**: Custom coordination routing to fast storage
- **Cost Optimization**: Time-based or load-based custom coordination
- **Compliance**: Custom coordination routing sensitive data appropriately
- **Multi-Region**: Geographic-aware custom coordination patterns

## Built-in Coordination Patterns

The framework provides ready-to-use coordination patterns:
- **Single**: Use one provider for all operations
- **WriteToAll**: Write to all providers, read from designated provider
- **WriteAllBestEffort**: Write to all providers, succeed if at least one succeeds
- **WriteWithBackup**: Write to primary + backup providers
- **Prioritized**: Try providers in order until one succeeds (use for fast-first with fallbacks)

## Custom Coordination

Implement unlimited custom coordination logic by implementing the `CoordinationStrategy` interface:
- Time-based routing (business hours vs off-hours)
- Load-aware distribution
- Content-based routing
- Geographic distribution
- Compliance-aware persistence
- Multi-tier storage strategies