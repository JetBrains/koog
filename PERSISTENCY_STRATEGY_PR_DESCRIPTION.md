# PersistencyStrategy: Flexible Persistence Provider Selection

This PR introduces the `PersistencyStrategy` pattern for flexible persistence provider selection in agent checkpoints, enabling intelligent routing between multiple providers based on context and requirements.

## Overview

Building on the persistence framework from PRs #480 (Redis) and #481 (SQL providers), this implementation adds a strategy pattern that enables:

- **Dynamic provider selection** based on operation context
- **Hybrid strategies** for different persistence needs  
- **LLM-powered intelligent routing** with fallback mechanisms
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
    
    // Context-aware selection
    data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (OperationContext) -> String
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
    strategy = PersistencyStrategy.Dynamic(
        providers = mapOf(
            "ephemeral" to redisProvider,
            "durable" to postgresProvider
        ),
        selector = { context ->
            when {
                context.checkpoint?.nodeId?.contains("temp") == true -> "ephemeral"
                else -> "durable"
            }
        }
    )
}
```

### 3. Use Cases

**Performance Optimization**
```kotlin
strategy = PersistencyStrategy.Dynamic(
    providers = mapOf("fast" to redis, "durable" to postgres),
    selector = { context ->
        if (context.operation is SaveCheckpoint) "fast" else "durable"
    }
)
```

**Three-Tier Storage Routing**
```kotlin
strategy = PersistencyStrategy.Dynamic(
    providers = mapOf(
        "ephemeral" to localCache,
        "durable" to postgres,
        "critical" to s3Provider
    ),
    selector = { context ->
        when {
            context.checkpoint?.nodeId?.startsWith("critical") == true -> "critical"
            context.operation is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint -> "durable"
            else -> "ephemeral"
        }
    }
)
```

**LLM-Powered Intelligent Routing**
```kotlin
strategy = PersistencyStrategy.AutoSelectForTask(
    providers = mapOf(
        "redis" to ProviderInfo(redis, "Fast temporary storage", listOf("fast", "ephemeral")),
        "postgres" to ProviderInfo(postgres, "Durable milestone storage", listOf("durable", "reliable")),
        "s3" to ProviderInfo(s3Provider, "Long-term archive storage", listOf("archive", "cost-effective"))
    ),
    taskDescription = "Multi-step data processing pipeline with decision points",
    maxRetries = 3
) // LLM analyzes context and selects the most appropriate provider
```

## Architecture

### Design Principles

1. **Follows ToolSelectionStrategy Pattern**: Consistent API design across Koog
2. **Composable**: Strategies can wrap other strategies
3. **Extensible**: Easy to add new strategy types
4. **Non-Breaking**: Maintains backward compatibility

### Implementation Details

- `PersistencyStrategyProvider`: Implements `PersistencyStorageProvider` interface while delegating to strategy-selected providers
- **Explicit Hybrid Logic**: Requires clear, predictable selector functions - no hidden defaults or magic behavior
- **Robust Error Handling**: AutoSelectForTask includes retry mechanism with intelligent fallback provider selection
- **Thread Safety**: Concurrent access support with proper synchronization
- **Simplified Architecture**: Removed complex failover logic in favor of infrastructure-level HA solutions
- Integrates seamlessly with existing `Persistency` feature
- No changes required to existing providers

## Testing

Comprehensive JVM-based test suite covering:
- All strategy types with proper mocking
- Dynamic selection logic with context awareness and flexible provider configuration
- AutoSelectForTask strategy with LLM-powered intelligent routing and retry logic
- Concurrent access and thread safety
- Error handling and retry mechanisms
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

## Key Features Implemented

This PR includes production-ready enhancements:
- **Flexible Dynamic Strategy**: Supports any number of providers with clear selector functions for predictable, maintainable behavior
- **LLM-Powered Selection**: AutoSelectForTask provides intelligent routing with context analysis and robust retry mechanisms
- **Retry & Fallback Logic**: Includes comprehensive error handling with configurable retries and fallback providers
- **Thread Safety**: Full concurrent access support for high-throughput scenarios
- **Comprehensive Testing**: JVM-based test suite with proper mocking and edge case coverage
- **Clean Architecture**: Focused, maintainable codebase with clear separation of concerns

## Future Enhancements

This implementation provides the foundation for:
- **Adaptive strategies**: Automatically adjust based on performance metrics
- **Multi-provider transactions**: Coordinate saves across providers
- **Cost-aware routing**: Select providers based on budget constraints
- **Infrastructure integration**: Better support for service discovery and load balancing

## Migration Guide

No migration required. Existing code continues to work. To adopt strategies:

1. Replace `storage = provider` with `strategy = PersistencyStrategy.Single(provider)`
2. Or better, evaluate if `Dynamic` or `AutoSelectForTask` strategies fit your use case
3. Dynamic strategy provides maximum flexibility with named providers and custom selectors

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