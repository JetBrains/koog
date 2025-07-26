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
    
    // Agent-level routing logic (ensures data consistency)
    data class Dynamic(
        val providers: Map<String, PersistencyStorageProvider>,
        val selector: suspend (AgentContext) -> String
    )
    
    // Multiple providers with configurable read/write strategies
    data class MultiProvider(
        val providers: Map<String, PersistencyStorageProvider>,
        val writeStrategy: WriteStrategy,
        val readStrategy: ReadStrategy
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
                // Long-running data processing agents - use fast Redis for frequent checkpoints
                context.agentContext.id.contains("data-processing") -> "redis"
                
                // Critical business logic agents - use durable PostgreSQL for recovery
                context.agentContext.id.contains("business-logic") -> "postgres"
                
                // High-priority customer agents - always use durable storage
                context.agentContext.id.contains("priority") -> "postgres"
                
                else -> "redis"  // Default to fast storage
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

**Multi-Provider Checkpoint Strategies**

The `MultiProvider` strategy enables advanced patterns by coordinating multiple providers simultaneously:

**Write-Through Caching Pattern**
```kotlin
install(Persistency) {
    strategy = PersistencyStrategy.MultiProvider(
        providers = mapOf(
            "redis" to redisProvider,     // Fast cache layer
            "postgres" to postgresProvider // Durable persistence layer
        ),
        writeStrategy = PersistencyStrategy.MultiProvider.WriteStrategy.WriteToAll(
            listOf("redis", "postgres")  // Write to both providers
        ),
        readStrategy = PersistencyStrategy.MultiProvider.ReadStrategy.FastestFirst(
            fast = "redis",               // Try cache first
            fallbacks = listOf("postgres") // Fallback to durable storage
        )
    )
}
```

**Backup/Redundancy Pattern**
```kotlin
install(Persistency) {
    strategy = PersistencyStrategy.MultiProvider(
        providers = mapOf(
            "primary" to primaryDbProvider,
            "backup1" to backup1Provider,
            "backup2" to backup2Provider
        ),
        writeStrategy = PersistencyStrategy.MultiProvider.WriteStrategy.WriteWithBackup(
            primary = "primary",
            backups = listOf("backup1", "backup2") // Backup writes (best effort)
        ),
        readStrategy = PersistencyStrategy.MultiProvider.ReadStrategy.PrimaryOnly("primary")
    )
}
```

**High Availability with Failover**
```kotlin
install(Persistency) {
    strategy = PersistencyStrategy.MultiProvider(
        providers = mapOf(
            "primary" to primaryProvider,
            "secondary" to secondaryProvider,
            "tertiary" to tertiaryProvider
        ),
        writeStrategy = PersistencyStrategy.MultiProvider.WriteStrategy.WriteToAllBestEffort(
            listOf("primary", "secondary", "tertiary") // Succeed if any provider works
        ),
        readStrategy = PersistencyStrategy.MultiProvider.ReadStrategy.Prioritized(
            listOf("primary", "secondary", "tertiary") // Try in order until success
        )
    )
}
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

### **MultiProvider Strategy**
- **Use when**: You need advanced patterns like caching, backup, or high availability
- **Example**: Write-through caching (Redis + PostgreSQL), backup redundancy, failover systems
- **Best for**: Production systems requiring maximum reliability, performance optimization, or data redundancy

### **AutoSelectForTask Strategy**
- **Use when**: Optimal checkpointing strategy depends on the agent's task characteristics
- **Example**: Variable-duration agents, complex workflows, multi-tenant agent platforms
- **Best for**: Agent platforms where checkpoint requirements vary by task complexity/duration/criticality

## Benefits

**For Production Agent Deployments**:
- **Checkpoint performance**: Fast storage for frequent auto-saves, durable storage for recovery points
- **Recovery optimization**: Critical checkpoints in reliable storage, temporary ones in fast storage  
- **Cost efficiency**: Expensive durable storage only for important recovery points
- **High availability**: MultiProvider enables backup/redundancy and failover patterns
- **Write-through caching**: Combine fast and durable providers for optimal read/write performance
- **Data redundancy**: Automatic backup writes protect against provider failures
- **Operational flexibility**: Adjust checkpointing strategy without code changes (AutoSelectForTask)
- **Agent resilience**: Built-in fallback mechanisms ensure checkpoint saving never fails

**Migration Path**: Start with `Single`, move to `Dynamic` for agent-based rules, upgrade to `MultiProvider` for advanced patterns (caching, backup), or use `AutoSelectForTask` for intelligent task-aware selection.

## Custom Strategy Implementation

The `Dynamic` strategy provides the extensibility point for custom checkpoint routing logic. Here's how to create reusable, composable routing strategies:

**Reusable Routing Components**
```kotlin
// 1. Reusable load balancer
class RoundRobinLoadBalancer(private val providerNames: List<String>) {
    private val currentIndex = AtomicInteger(0)
    
    fun selectNext(): String {
        val index = currentIndex.getAndIncrement() % providerNames.size
        return providerNames[index]
    }
}

// 2. Reusable time-based router
class TimeBasedRouter(
    private val businessHours: IntRange = 9..17,
    private val fastProvider: String = "fast",
    private val durableProvider: String = "durable"
) {
    fun selectByTime(): String {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return if (hour in businessHours) fastProvider else durableProvider
    }
}

// 3. Reusable node-based router
class NodePatternRouter(
    private val patterns: Map<String, String>,
    private val defaultProvider: String
) {
    fun selectByNodeId(nodeId: String?): String {
        return patterns.entries.find { (pattern, _) -> 
            nodeId?.contains(pattern) == true 
        }?.value ?: defaultProvider
    }
}
```

**Composable Strategy Builder**
```kotlin
class PersistencyRoutingBuilder {
    private val rules = mutableListOf<(PersistencyStrategy.Dynamic.OperationContext) -> String?>()
    private var fallback: String = "default"
    
    fun loadBalance(providers: List<String>): PersistencyRoutingBuilder {
        val balancer = RoundRobinLoadBalancer(providers)
        rules.add { _ -> balancer.selectNext() }
        return this
    }
    
    fun timeBasedRouting(fastProvider: String, durableProvider: String): PersistencyRoutingBuilder {
        val router = TimeBasedRouter(fastProvider = fastProvider, durableProvider = durableProvider)
        rules.add { _ -> router.selectByTime() }
        return this
    }
    
    fun nodePatternRouting(patterns: Map<String, String>): PersistencyRoutingBuilder {
        val router = NodePatternRouter(patterns, fallback)
        rules.add { context -> router.selectByNodeId(context.checkpoint?.nodeId) }
        return this
    }
    
    fun fallbackTo(provider: String): PersistencyRoutingBuilder {
        fallback = provider
        return this
    }
    
    fun build(): (PersistencyStrategy.Dynamic.OperationContext) -> String {
        return { context ->
            rules.firstNotNullOfOrNull { rule -> rule(context) } ?: fallback
        }
    }
}
```

**Real-World Usage Examples**

**Scenario 1: Development vs Production Environment**
```kotlin
// Use fast Redis for development, durable PostgreSQL for production
install(Persistency) {
    strategy = PersistencyStrategy.Dynamic(
        providers = mapOf(
            "redis" to redisProvider,      // Fast development checkpoints
            "postgres" to postgresProvider // Durable production checkpoints
        ),
        selector = { context ->
            val isProduction = System.getenv("ENVIRONMENT") == "production"
            if (isProduction) "postgres" else "redis"
        }
    )
}
```

**Scenario 2: Multi-Tenant Agent Platform**
```kotlin
// Agent persistence based on customer tier - all operations for an agent use same provider
install(Persistency) {
    strategy = PersistencyStrategy.Dynamic(
        providers = mapOf(
            "postgres" to postgresProvider, // Enterprise: full persistence
            "redis" to redisProvider,       // Pro: session-based persistence
            "memory" to memoryProvider      // Free: no persistence
        ),
        selector = { context ->
            val customerId = context.agentContext.id.substringBefore("-")
            when (customerService.getTier(customerId)) {
                "enterprise" -> "postgres" // Durable checkpoints for enterprise
                "pro" -> "redis"           // Fast checkpoints for pro tier
                else -> "memory"           // No persistence for free tier
            }
        }
    )
}
```

**Scenario 3: Load Balancing Across Provider Instances**
```kotlin  
// Distribute agent checkpoints across multiple Redis instances for load balancing
class LoadBalancer(private val providers: List<String>) {
    private val counter = AtomicInteger(0)
    fun next(): String = providers[counter.getAndIncrement() % providers.size]
}

val balancer = LoadBalancer(listOf("redis1", "redis2", "redis3"))

install(Persistency) {
    strategy = PersistencyStrategy.Dynamic(
        providers = mapOf(
            "redis1" to redisProvider1,
            "redis2" to redisProvider2, 
            "redis3" to redisProvider3
        ),
        selector = { context ->
            // All operations for an agent go to the same Redis instance
            balancer.next()
        }
    )
}
```

These examples demonstrate practical routing logic for real production scenarios. The selector functions can be extracted into reusable utilities and shared across projects with similar checkpoint routing requirements.

## Technical Details

**Data Consistency & Safety**:
- **Agent-level routing**: Provider selection is cached per agent to ensure all operations use the same provider consistently
- **No operation-level routing**: Prevents data corruption where saves go to one provider but reads go to another
- **Thread-safe caching**: Multiple concurrent operations for the same agent safely use the cached provider selection
- **Atomic provider selection**: Each agent gets exactly one provider for its entire lifecycle to guarantee data consistency

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