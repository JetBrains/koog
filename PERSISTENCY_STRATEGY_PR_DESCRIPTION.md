# PersistencyStrategy: Intelligent Provider Selection

This PR adds the `PersistencyStrategy` pattern to enable intelligent routing between multiple persistence providers within the agent framework.

## Problem & Solution

**Problem**: Currently, agents can only use one persistence provider at a time. This creates challenges for production applications where different persistence needs conflict:

**Real-World Scenarios:**
- **Long-running agents**: Fast Redis for frequent auto-checkpoints, PostgreSQL for recovery points
- **Development vs Production**: File storage for local dev checkpoints, cloud storage for prod recovery
- **Multi-environment deployments**: Memory storage for temp checkpoints, durable storage for critical states  
- **Agent lifecycle management**: Fast storage for active execution, archive storage for historical states

**Solution**: Strategy pattern that intelligently routes checkpoint operations to optimal providers based on context. This enables combining the Redis provider (PR #480) and SQL provider (PR #481) in hybrid architectures that leverage the strengths of multiple storage systems for different checkpoint scenarios.

## Implementation

### Strategy Types
```kotlin
sealed interface PersistencyStrategy {
    data class Single(val provider: PersistencyStorageProvider)  // Backward compatible
    data object None                                             // Testing/dev
    
    // Custom routing logic
    data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (OperationContext) -> String
    )
    
    // LLM-powered selection using @LLMDescription annotations
    data class AutoSelectForTask(
        val providers: Map<String, PersistencyStorageProvider>,
        val taskDescription: String,
        val maxRetries: Int = 3
    )
}
```

### Usage Examples

**Long-Running Agent with Mixed Checkpoint Needs**

This example shows how to combine the Redis provider (PR #480) for fast temporary checkpoints with the SQL provider (PR #481) for durable recovery points:

```kotlin
install(Persistency) {
    strategy = PersistencyStrategy.Dynamic(
        providers = mapOf(
            "redis" to redisProvider,     // Fast temporary checkpoints
            "postgres" to postgresProvider // Durable recovery points
        ),
        selector = { context ->
            when {
                // Automatic checkpoints during processing - fast but ephemeral
                context.operation is SaveCheckpoint && 
                context.checkpoint?.nodeId?.contains("processing") == true -> "redis"
                
                // Critical decision points - need durable storage for rollback
                context.checkpoint?.nodeId?.contains("decision") == true -> "postgres"
                
                // Manual recovery checkpoints - always durable
                context.checkpoint?.nodeId?.contains("checkpoint") == true -> "postgres"
                
                else -> "redis"  // Default to fast storage for frequent saves
            }
        }
    )
}
```

**Intelligent Checkpoint Strategy Selection**

For complex scenarios, the LLM can automatically choose between the Redis provider (PR #480) and SQL provider (PR #481) based on agent task characteristics:

```kotlin
// Providers with @LLMDescription annotations  
@LLMDescription("Fast in-memory storage for frequent automatic checkpoints during active execution")
class RedisProvider : PersistencyStorageProvider

@LLMDescription("Durable SQL database for critical recovery checkpoints and long-term agent state history") 
class PostgresProvider : PersistencyStorageProvider

strategy = PersistencyStrategy.AutoSelectForTask(
    providers = mapOf(
        "redis" to RedisProvider(),
        "postgres" to PostgresProvider()
    ),
    taskDescription = "Long-running data analysis agent with 2-hour execution time, requires fast recovery from failures"
)
// LLM analyzes the agent task characteristics and automatically determines:
// - Redis for frequent auto-checkpoints (every few seconds during execution)
// - Postgres for recovery points at major milestones and final state
```

## When to Use Each Strategy

### **Single Strategy** (Default)
- **Use when**: Agents have uniform checkpointing needs across all execution
- **Example**: Short-lived agents, development environments, simple workflows
- **Migration**: No change needed - existing `storage = provider` code works

### **Dynamic Strategy** 
- **Use when**: You have clear rules about when different checkpoint durability is needed
- **Example**: Fast storage for frequent auto-saves, durable storage for critical recovery points
- **Best for**: Long-running agents where you can define checkpoint importance programmatically

### **AutoSelectForTask Strategy**
- **Use when**: Optimal checkpointing strategy depends on the agent's task characteristics
- **Example**: Variable-duration agents, complex workflows, multi-tenant agent platforms
- **Best for**: Agent platforms where checkpoint requirements vary by task complexity/duration/criticality

## Benefits

**For Production Agent Deployments**:
- **Checkpoint performance**: Fast storage for frequent auto-saves, durable storage for recovery points
- **Recovery optimization**: Critical checkpoints in reliable storage, temporary ones in fast storage  
- **Cost efficiency**: Expensive durable storage only for important recovery points
- **Operational flexibility**: Adjust checkpointing strategy without code changes (AutoSelectForTask)
- **Agent resilience**: Built-in fallback mechanisms ensure checkpoint saving never fails

**Migration Path**: Start with `Single`, move to `Dynamic` for checkpoint-type rules, upgrade to `AutoSelectForTask` for task-aware selection.

## Custom Strategy Implementation

Beyond the built-in strategies, you can implement custom routing logic. Here's an example of a load-balancing strategy that rotates between providers:

```kotlin
// Custom strategy that alternates between providers for load distribution
data class RoundRobin(
    val providers: Map<String, PersistencyStorageProvider>
) : PersistencyStrategy {
    private val providerList = providers.values.toList()
    private var currentIndex = AtomicInteger(0)
    
    fun selectNextProvider(): PersistencyStorageProvider {
        val index = currentIndex.getAndIncrement() % providerList.size
        return providerList[index]
    }
}

// Usage: extend the sealed interface in your codebase
sealed interface MyPersistencyStrategy : PersistencyStrategy {
    data class RoundRobin(
        val providers: Map<String, PersistencyStorageProvider>
    ) : MyPersistencyStrategy
}

// Implementation in custom PersistencyStrategyProvider
class MyPersistencyStrategyProvider(
    strategy: PersistencyStrategy,
    context: AIAgentContextBase
) : PersistencyStrategyProvider(strategy, context) {
    
    override suspend fun selectProvider(
        operation: PersistencyStrategy.Dynamic.Operation,
        checkpoint: AgentCheckpointData?
    ): PersistencyStorageProvider {
        return when (strategy) {
            is MyPersistencyStrategy.RoundRobin -> strategy.selectNextProvider()
            else -> super.selectProvider(operation, checkpoint)
        }
    }
}

// Usage in agent configuration
install(Persistency) {
    strategy = MyPersistencyStrategy.RoundRobin(
        providers = mapOf(
            "redis1" to redisProvider1,
            "redis2" to redisProvider2,
            "postgres" to postgresProvider
        )
    )
}
```

This demonstrates how the extensible architecture allows for custom routing logic beyond the four built-in strategies. The `PersistencyStrategyProvider` class is designed to be extended for custom strategy implementations.

## Technical Details

**Intelligent Provider Selection**:
- `AutoSelectForTask` analyzes task description against provider `@LLMDescription` annotations
- Selection based on overall task requirements, not individual operations
- Uses structured data for reliable provider name validation with optional reasoning

**Architecture**:
- **Clean delegation**: `PersistencyStrategyProvider` implements the same interface while routing to selected providers
- **Thread-safe**: Supports concurrent access for server environments  
- **Comprehensive testing**: Covers all strategies with proper mocking
- **No new dependencies**: Uses existing persistence infrastructure patterns

## Operational Considerations

**Monitoring & Debugging**:
- Checkpoint provider selection decisions are logged with reasoning (AutoSelectForTask)
- Failed provider selections automatically fall back to durable checkpoint storage
- Standard agent checkpoint metrics work across all strategies

**Performance Notes**:
- `Single`: No overhead, identical to current checkpointing performance
- `Dynamic`: Minimal overhead from checkpoint routing logic
- `AutoSelectForTask`: Additional LLM call overhead during provider selection (cached per agent session)

**Production Checklist**:
- ✅ Test all checkpoint providers independently before combining in strategies
- ✅ Configure appropriate fallback providers (prioritize durable storage for AutoSelectForTask)
- ✅ Monitor checkpoint provider selection accuracy and adjust task descriptions
- ✅ Set up alerting for checkpoint save failures across all providers

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
- [x] Tests for the changes have been added
- [x] All new and existing tests passed

### Additional steps for pull requests adding a new feature
- [ ] An issue describing the proposed change exists *(No prior issue - feature developed as community contribution)*
- [ ] The pull request includes a link to the issue
- [ ] The change was discussed and approved in the issue
- [x] Docs have been added / updated
