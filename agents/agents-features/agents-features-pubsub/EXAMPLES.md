# PubSub Feature Examples

This document provides comprehensive examples of using the PubSub feature with different providers and configurations.

## Basic Usage

### Simple Redis PubSub

```kotlin
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.feature.PubSubFeatureConfig
import ai.koog.agents.features.pubsub.providers.redis.RedisPubSubProvider
import ai.koog.agents.features.pubsub.message.PubSubStringMessage

// Configure Redis provider
val redisProvider = RedisPubSubProvider(
    host = "localhost",
    port = 6379,
    password = null
)

// Configure PubSub feature
val pubsubConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    autoSubscribeTopics = listOf("agent-commands", "notifications")
    publishAgentEvents = true
    agentEventTopic = "agent-lifecycle"
}

// Create agent with PubSub feature
val agent = AIAgent(
    promptExecutor = executor,
    strategy = SimpleStrategy(),
    features = listOf(PubSub.Feature to pubsubConfig)
)

// Publish messages
redisProvider.publish("notifications", "Agent started successfully")
redisProvider.publish(
    PubSubStringMessage(
        topic = "agent-commands",
        content = "execute_task",
        attributes = mapOf("priority" to "high", "source" to "scheduler")
    )
)
```

### Google Cloud PubSub

```kotlin
import ai.koog.agents.features.pubsub.providers.gcp.GcpPubSubProvider

// Configure GCP provider
val gcpProvider = GcpPubSubProvider(
    projectId = "my-project",
    credentialsPath = "/path/to/service-account.json"
)

val pubsubConfig = PubSubFeatureConfig().apply {
    provider = gcpProvider
    autoSubscribeTopics = listOf("planning-events", "coordination-signals")
    publishAgentEvents = true
    publishToolEvents = true
    maxConcurrentMessages = 50
}

val agent = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlannerWithCritic("TaskPlanner", AllToolsStrategy(), executor),
    features = listOf(PubSub.Feature to pubsubConfig)
)
```

## Advanced Configurations

### Message Filtering

```kotlin
val config = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    // Only publish high-priority messages
    publishFilter = { message ->
        message.attributes["priority"] == "high" ||
        message.topic.startsWith("critical-")
    }
    
    // Only receive messages from trusted sources
    receiveFilter = { message ->
        val trustedSources = setOf("scheduler", "monitor", "planner")
        message.attributes["source"] in trustedSources
    }
}
```

### Multi-Topic Coordination

```kotlin
val config = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    // Subscribe to coordination topics
    autoSubscribeTopics = listOf(
        "planning-coordination",    // Planning agent coordination
        "resource-allocation",     // Resource management
        "checkpoint-signals",      // Persistence coordination
        "error-notifications"      // Error handling
    )
    
    // Publish all event types to different topics
    publishAgentEvents = true
    publishToolEvents = true
    publishLLMEvents = true
    agentEventTopic = "agent-lifecycle"
    toolEventTopic = "tool-executions"
    llmEventTopic = "llm-interactions"
}
```

## Planning Agent Integration

### Planning Coordination with PubSub

```kotlin
// Planning agent with PubSub coordination
val planningConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    // Subscribe to planning coordination topics
    autoSubscribeTopics = listOf(
        "plan-coordination",     // Cross-agent plan coordination
        "plan-feedback",         // Plan evaluation feedback
        "resource-updates"       // Resource availability updates
    )
    
    // Publish planning lifecycle events
    publishAgentEvents = true
    agentEventTopic = "planning-events"
    
    // Filter planning-specific messages
    publishFilter = { message ->
        message.topic.contains("plan") || 
        message.attributes.containsKey("planning-context")
    }
    
    receiveFilter = { message ->
        message.attributes["message-type"] in setOf(
            "plan-step", "plan-evaluation", "resource-notification"
        )
    }
}

val planningAgent = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlannerWithCritic("CoordinatedPlanner", AllToolsStrategy(), executor),
    features = listOf(PubSub.Feature to planningConfig)
)

// Coordinate plan execution across multiple agents
suspend fun coordinatePlanExecution() {
    // Publish plan start notification
    redisProvider.publish(
        "plan-coordination",
        "plan_started",
        mapOf(
            "plan-id" to "plan-123",
            "agent-id" to "planner-agent-1",
            "estimated-steps" to "5"
        )
    )
    
    // Subscribe to coordination responses
    redisProvider.subscribe("plan-coordination").collect { message ->
        when (message.attributes["message-type"]) {
            "plan-step-completed" -> {
                println("Step completed by ${message.attributes["agent-id"]}")
            }
            "resource-conflict" -> {
                println("Resource conflict detected, replanning...")
                // Trigger replanning logic
            }
            "plan-evaluation-result" -> {
                println("Plan evaluation: ${message.content}")
            }
        }
    }
}
```

### Distributed Planning with Multiple Agents

```kotlin
// Primary planner agent
val primaryPlannerConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    autoSubscribeTopics = listOf("plan-feedback", "agent-status")
    publishAgentEvents = true
    agentEventTopic = "primary-planner-events"
}

val primaryPlanner = AIAgent(
    promptExecutor = executor,
    strategy = SimplePlanner("PrimaryPlanner", AllToolsStrategy()),
    features = listOf(PubSub.Feature to primaryPlannerConfig)
)

// Critic/evaluator agent
val criticConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    autoSubscribeTopics = listOf("plan-requests", "plan-updates")
    publishAgentEvents = true  
    agentEventTopic = "critic-events"
}

val criticAgent = AIAgent(
    promptExecutor = executor,
    strategy = SimpleStrategy(), // Custom critic strategy
    features = listOf(PubSub.Feature to criticConfig)
)

// Coordinate between planner and critic
suspend fun distributedPlanningWorkflow() {
    // Primary planner publishes plan for evaluation
    redisProvider.publish(
        "plan-requests",
        "evaluate_plan",
        mapOf(
            "plan-id" to "plan-456",
            "plan-content" to "Step 1: Analyze data, Step 2: Generate report",
            "requester" to "primary-planner"
        )
    )
    
    // Critic evaluates and provides feedback
    redisProvider.subscribe("plan-requests").collect { message ->
        if (message.attributes["message-type"] == "evaluate_plan") {
            // Process plan evaluation
            val feedback = evaluatePlan(message.content)
            
            redisProvider.publish(
                "plan-feedback",
                feedback,
                mapOf(
                    "plan-id" to message.attributes["plan-id"]!!,
                    "evaluation-result" to "approved", // or "needs-revision"
                    "evaluator" to "critic-agent"
                )
            )
        }
    }
}
```

## Persistence Coordination

### Redis + PostgreSQL Coordination

```kotlin
// Configure PubSub for persistence coordination
val persistenceConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    autoSubscribeTopics = listOf(
        "checkpoint-coordination",  // Coordinate checkpoint saves
        "persistence-status",       // Monitor persistence health
        "recovery-signals"          // Handle recovery scenarios
    )
    
    publishAgentEvents = true
    agentEventTopic = "persistence-events"
}

// Agent with coordinated persistence
val agent = AIAgent(
    promptExecutor = executor,
    strategy = planningStrategy,
    features = listOf(PubSub.Feature to persistenceConfig),
    persistencyStrategy = PersistencyStrategy.Dynamic { context, registry ->
        // Coordinate persistence based on PubSub signals
        when (context.checkpointType) {
            "plan_step_progress" -> {
                // Publish checkpoint notification
                redisProvider.publish(
                    "checkpoint-coordination",
                    "checkpoint_saved",
                    mapOf(
                        "type" to "plan_step",
                        "agent-id" to context.agentId,
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                CoordinationStrategies.Single(redisId)
            }
            "plan_completed" -> {
                // Notify completion to all subscribers
                redisProvider.publish(
                    "checkpoint-coordination", 
                    "plan_completed",
                    mapOf(
                        "plan-id" to context.planId,
                        "persistence" to "redis+postgres"
                    )
                )
                CoordinationStrategies.WriteToAll(listOf(redisId, postgresId))
            }
            else -> CoordinationStrategies.Single(redisId)
        }
    }
)
```

## Multi-Tenant SaaS Configuration

### Tenant-Aware PubSub

```kotlin
class TenantAwarePubSubConfig(private val tenantId: String, private val tier: String) {
    fun createConfig(): PubSubFeatureConfig {
        return PubSubFeatureConfig().apply {
            provider = when (tier) {
                "premium" -> redisProvider  // Premium: Redis for speed
                "standard" -> redisProvider // Standard: Redis with limits
                "free" -> NoPubSubProvider() // Free: No real-time features
            }
            
            // Tenant-specific topics
            autoSubscribeTopics = listOf(
                "tenant-$tenantId-commands",
                "tenant-$tenantId-notifications",
                "global-announcements"
            )
            
            // Filter by tenant
            publishFilter = { message ->
                message.attributes["tenant-id"] == tenantId ||
                message.topic.startsWith("global-")
            }
            
            receiveFilter = { message ->
                message.attributes["tenant-id"] == tenantId ||
                message.topic.startsWith("global-") ||
                message.topic.contains("tenant-$tenantId")
            }
            
            // Tier-based limits
            maxConcurrentMessages = when (tier) {
                "premium" -> 100
                "standard" -> 20
                "free" -> 5
            }
        }
    }
}

// Usage for different tenants
val premiumTenantConfig = TenantAwarePubSubConfig("tenant-123", "premium").createConfig()
val freeTenantConfig = TenantAwarePubSubConfig("tenant-456", "free").createConfig()

val premiumAgent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    features = listOf(PubSub.Feature to premiumTenantConfig)
)
```

## Error Handling and Monitoring

### Robust Error Handling

```kotlin
val config = PubSubFeatureConfig().apply {
    provider = redisProvider
    autoSubscribeTopics = listOf("error-notifications", "health-checks")
    
    // Custom error handling
    publishFilter = { message ->
        try {
            // Validate message before publishing
            message.topic.isNotBlank() && message.content.length < 10000
        } catch (e: Exception) {
            println("Message validation failed: ${e.message}")
            false
        }
    }
}

// Monitor PubSub health
suspend fun monitorPubSubHealth() {
    val healthInfo = redisProvider.getHealthInfo()
    
    if (healthInfo["connected"] != true) {
        println("PubSub provider disconnected, attempting reconnection...")
        
        // Publish health alert
        redisProvider.publish(
            "health-checks",
            "provider_disconnected",
            mapOf(
                "provider" to "redis",
                "timestamp" to System.currentTimeMillis().toString(),
                "action" to "reconnecting"
            )
        )
    }
}
```

## Performance Optimization

### High-Throughput Configuration

```kotlin
val highThroughputConfig = PubSubFeatureConfig().apply {
    provider = redisProvider
    
    // Optimize for high message volume
    maxConcurrentMessages = 200
    autoAcknowledge = true  // Faster processing
    
    // Batch subscription topics
    autoSubscribeTopics = listOf(
        "high-volume-events",
        "batch-processing",
        "real-time-updates"
    )
    
    // Selective filtering to reduce processing load
    receiveFilter = { message ->
        // Only process priority messages during peak hours
        val isPeakHour = isBusinessHours()
        !isPeakHour || message.attributes["priority"] in setOf("high", "critical")
    }
}

// Batch message publishing
suspend fun publishBatch(messages: List<PubSubStringMessage>) {
    messages.chunked(50).forEach { batch ->
        batch.forEach { message ->
            redisProvider.publish(message)
        }
        delay(10) // Small delay between batches
    }
}
```

## Custom Provider Implementation

### Custom Local File Provider

```kotlin
class FilePubSubProvider(private val baseDir: String) : PubSubProvider {
    private val subscriptions = mutableMapOf<String, MutableSharedFlow<ReceivedMessage>>()
    
    override suspend fun publish(message: PubSubMessage): String? {
        val file = File("$baseDir/${message.topic}.messages")
        file.appendText("${message.content}\n")
        
        // Notify subscribers
        subscriptions[message.topic]?.emit(
            ReceivedMessage(
                messageId = UUID.randomUUID().toString(),
                topic = message.topic,
                content = message.content,
                attributes = message.attributes
            )
        )
        
        return UUID.randomUUID().toString()
    }
    
    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return subscriptions.getOrPut(topic) { 
            MutableSharedFlow<ReceivedMessage>() 
        }.asSharedFlow()
    }
    
    override suspend fun isConnected(): Boolean = File(baseDir).exists()
    
    override suspend fun getHealthInfo(): Map<String, Any> = mapOf(
        "provider" to "file",
        "connected" to isConnected(),
        "base-directory" to baseDir
    )
    
    override fun close() {
        subscriptions.clear()
    }
}

// Usage with custom provider
val fileConfig = PubSubFeatureConfig().apply {
    provider = FilePubSubProvider("/tmp/pubsub-messages")
    autoSubscribeTopics = listOf("local-events")
}
```

This comprehensive set of examples demonstrates the flexibility and power of the PubSub feature across various use cases, from simple message passing to complex multi-agent coordination scenarios.