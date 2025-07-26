# PersistencyStrategy: Flexible Persistence Provider Selection

This PR introduces the `PersistencyStrategy` pattern for flexible persistence provider selection in agent checkpoints, following the same design principles as `ToolSelectionStrategy`.

## Overview

Building on the persistence framework from PRs #480 (Redis) and #481 (SQL providers), this implementation adds a strategy pattern that enables:

- **Dynamic provider selection** based on operation context
- **Automatic failover** between providers
- **Hybrid strategies** for different persistence needs
- **Backward compatibility** with existing code

## Motivation

As demonstrated in the Redis (#480) and SQL (#481) PRs, different persistence providers excel at different use cases:
- **Redis**: Fast, ephemeral, mid-execution checkpoints
- **SQL**: Durable, queryable, session persistence
- **File/S3**: Archival, compliance, long-term storage

Previously, agents could only use one provider at a time. This PR enables intelligent routing between multiple providers.

## Key Features

### 1. Strategy Types

```kotlin
sealed interface PersistencyStrategy {
    // Single provider (backward compatible)
    data class Single(val provider: PersistencyStorageProvider)
    
    // No persistence
    data object None
    
    // Automatic failover
    data class Failover(val providers: List<PersistencyStorageProvider>)
    
    // Context-aware selection
    data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (OperationContext) -> String
    )
    
    // Pre-configured hybrid approach with intelligent defaults
    data class Hybrid(
        val ephemeralProvider: PersistencyStorageProvider,
        val durableProvider: PersistencyStorageProvider,
        val criticalProvider: PersistencyStorageProvider? = null,
        val selector: (suspend (OperationContext) -> ProviderType)? = null // Custom selection logic
    )
    
    // LLM-driven provider selection
    data class AutoSelectForTask(
        val providers: Map<String, ProviderInfo>,
        val taskDescription: String,
        val maxRetries: Int = 3
    )
}
```

### 2. Backward Compatibility

Existing code continues to work unchanged:
```kotlin
install(Persistency) {
    storage = PostgresPersistencyStorageProvider(...) // Still works
}
```

New strategic approach:
```kotlin
install(Persistency) {
    strategy = PersistencyStrategy.Hybrid(
        ephemeralProvider = redisProvider,
        durableProvider = postgresProvider
    )
}
```

### 3. Use Cases

**High Availability with Health Monitoring**
```kotlin
strategy = PersistencyStrategy.Failover(
    listOf(primaryPostgres, secondaryPostgres, s3Backup)
) // Automatic health checks ensure only working providers are used
```

**Performance Optimization**
```kotlin
strategy = PersistencyStrategy.Dynamic(
    providers = mapOf("fast" to redis, "durable" to postgres),
    selector = { context ->
        if (context.operation is SaveCheckpoint) "fast" else "durable"
    }
)
```

**Intelligent Cost Optimization**
```kotlin
strategy = PersistencyStrategy.Hybrid(
    ephemeralProvider = localCache,      // Free, mid-execution checkpoints
    durableProvider = postgres,          // Moderate cost, important checkpoints
    criticalProvider = s3Provider        // Low cost, high durability for final states
) // Simple defaults: durable for saves, ephemeral-first for reads
```

## Architecture

### Design Principles

1. **Follows ToolSelectionStrategy Pattern**: Consistent API design across Koog
2. **Composable**: Strategies can wrap other strategies
3. **Extensible**: Easy to add new strategy types
4. **Non-Breaking**: Maintains backward compatibility

### Implementation Details

- `PersistencyStrategyProvider`: Implements `PersistencyStorageProvider` interface while delegating to strategy-selected providers
- **Enhanced Failover Safety**: Comprehensive health checks for all operation types ensure provider availability before use
- **Simple Hybrid Logic**: Predictable default behavior with optional custom selector for advanced routing
- **Robust Error Handling**: AutoSelectForTask includes retry mechanism with intelligent fallback provider selection
- **Thread Safety**: Concurrent access support with proper synchronization
- Integrates seamlessly with existing `Persistency` feature
- No changes required to existing providers

## Testing

Comprehensive JVM-based test suite covering:
- All strategy types with proper mocking
- Failover scenarios with health check validation
- Dynamic selection logic with context awareness
- Hybrid strategy with simple, predictable default behavior
- Concurrent access and thread safety
- Error handling and retry mechanisms
- Provider health monitoring and automatic failover
- Backward compatibility

## Examples

Added comprehensive example demonstrating all strategy types:
```bash
./gradlew :examples:runExamplePersistencyStrategy
```

**LLM-Driven Selection with Fallback**
```kotlin
strategy = PersistencyStrategy.AutoSelectForTask(
    providers = mapOf(
        "redis" to ProviderInfo(redis, "Fast cache for temporary data", listOf("fast", "ephemeral")),
        "postgres" to ProviderInfo(postgres, "Durable storage for critical data", listOf("durable", "queryable"))
    ),
    taskDescription = "High-frequency trading agent with sub-second latency",
    maxRetries = 3 // Automatic retry with intelligent fallback on LLM failures
)
```

## Key Improvements Implemented

This PR includes production-ready enhancements:
- **Health Check Integration**: All failover strategies now perform comprehensive health checks before using providers
- **Simple Hybrid Strategy**: Clean default behavior with custom selector support for advanced use cases
- **Retry & Fallback Logic**: AutoSelectForTask includes robust error handling with configurable retries and fallback providers
- **Thread Safety**: Full concurrent access support for high-throughput scenarios
- **Comprehensive Testing**: JVM-based test suite with proper mocking and edge case coverage

## Future Enhancements

This implementation provides the foundation for:
- **Adaptive strategies**: Automatically adjust based on performance metrics
- **Multi-provider transactions**: Coordinate saves across providers
- **Cost-aware routing**: Select providers based on budget constraints
- **Real-time monitoring**: Advanced health metrics and automatic routing optimization

## Migration Guide

No migration required. Existing code continues to work. To adopt strategies:

1. Replace `storage = provider` with `strategy = PersistencyStrategy.Single(provider)`
2. Or better, evaluate if `Failover`, `Dynamic`, or `Hybrid` strategies fit your use case

## Dependencies

No new dependencies. Builds on existing persistence infrastructure.

---

## Type of Change
- [x] New feature
- [ ] Bug fix
- [ ] Documentation fix
- [ ] Tests improvement

## Checklist
- [x] The pull request has a description of the proposed change
- [x] I read the [Contributing Guidelines](https://github.com/JetBrains/koog/blob/main/CONTRIBUTING.md) before opening the pull request
- [x] The pull request uses **`develop`** as the base branch
- [x] Tests for the changes have been added (comprehensive JVM test suite)
- [x] All new and existing tests passed
- [x] Implementation includes production-ready error handling and safety features
- [x] Thread safety and concurrent access properly handled
- [x] Health check integration for robust failover scenarios

### Additional steps for pull requests adding a new feature
- [ ] An issue describing the proposed change exists *(No prior issue - feature developed as community contribution)*
- [ ] The pull request includes a link to the issue
- [ ] The change was discussed and approved in the issue
- [x] Docs have been added / updated