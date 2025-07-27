package ai.koog.agents.example.features.pubsub

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.providers.*
import ai.koog.agents.features.pubsub.providers.redis.RedisPubSubProvider
import ai.koog.agents.features.pubsub.providers.gcp.GCPPubSubProvider
import ai.koog.agents.features.pubsub.providers.local.LocalFilePubSubProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Comprehensive example demonstrating multi-provider PubSub coordination.
 * 
 * This example shows how different message types can be routed to optimal providers:
 * - Local agent coordination → InMemory (fast, same-process)
 * - Cross-environment tasks → Redis/GCP (distributed)
 * - Development/testing → LocalFile (cross-process on same machine)
 * 
 * Real-world scenarios:
 * 1. **Minecraft + Ktor coordination**: Planning agent in Ktor, execution agents in Minecraft
 * 2. **Microservices**: Different services with specialized agent roles
 * 3. **Mixed topologies**: Some agents local, others distributed
 * 4. **Performance tiers**: Critical vs non-critical message routing
 */
public object MultiplexedPubSubExample {
    
    @JvmStatic
    public fun main(args: Array<String>) {
        runBlocking {
            demonstrateBasicMultiplexing()
            demonstrateMinecraftKtorCoordination()
            demonstrateMicroservicesCoordination()
        }
    }
    
    /**
     * Basic multi-provider routing example.
     */
    private suspend fun demonstrateBasicMultiplexing() {
        println("=== Basic Multi-Provider Routing ===")
        
        // Create a multiplexed provider with different routing rules
        val multiplexedProvider = MultiplexedPubSubProvider {
            // Fast local coordination
            route("agent.local.*") to InMemoryPubSubProvider()
            route("alerts.emergency") to InMemoryPubSubProvider()
            
            // Cross-process development
            route("agent.cross.*") to LocalFilePubSubProvider()
            
            // Distributed coordination (Redis for demo, could be GCP in production)
            route("tasks.*") to LocalFilePubSubProvider() // Using LocalFile for demo
            route("results.*") to LocalFilePubSubProvider()
            
            // Default fallback
            defaultProvider = InMemoryPubSubProvider()
        }
        
        // Test publishing to different providers
        val messageIds = listOf(
            multiplexedProvider.publish("agent.local.status", "Local agent ready"),
            multiplexedProvider.publish("alerts.emergency", "Creeper spotted!"),
            multiplexedProvider.publish("tasks.build", "Build castle"),
            multiplexedProvider.publish("results.completed", "Castle built"),
            multiplexedProvider.publish("unknown.topic", "Falls back to default")
        )
        
        println("Published ${messageIds.size} messages to different providers")
        
        // Check health across all providers
        val healthInfo = multiplexedProvider.getHealthInfo()
        println("Health: ${healthInfo["connected"]} (${healthInfo["connectedProviders"]}/${healthInfo["totalProviders"]} providers)")
        
        multiplexedProvider.close()
    }
    
    /**
     * Demonstrates Minecraft + Ktor coordination scenario.
     * 
     * This mirrors our PR description use case:
     * - Ktor planning agent coordinates via distributed PubSub
     * - Minecraft agents coordinate locally via InMemory for fast communication
     * - Cross-environment messages use Redis/GCP for reliability
     */
    private suspend fun demonstrateMinecraftKtorCoordination() {
        println("\n=== Minecraft + Ktor Coordination ===")
        
        // Simulate Minecraft server environment (multiple local agents)
        val minecraftProvider = MultiplexedPubSubProvider {
            // Fast local agent-to-agent communication
            route("agent.builder.*") to InMemoryPubSubProvider()
            route("agent.defender.*") to InMemoryPubSubProvider()
            route("agent.gatherer.*") to InMemoryPubSubProvider()
            route("alerts.*") to InMemoryPubSubProvider()
            
            // Cross-environment coordination with Ktor planner
            route("planner.*") to LocalFilePubSubProvider() // Would be Redis/GCP in production
            route("tasks.*") to LocalFilePubSubProvider()
            route("status.*") to LocalFilePubSubProvider()
            
            defaultProvider = InMemoryPubSubProvider()
        }
        
        // Simulate Ktor planning environment
        val ktorProvider = MultiplexedPubSubProvider {
            // All messages go to distributed provider for cross-environment coordination
            defaultProvider = LocalFilePubSubProvider() // Would be Redis/GCP in production
        }
        
        // Simulate agent coordination scenario
        launch {
            // Planning agent publishes tasks
            ktorProvider.publish("tasks.build", "Let's build a castle together!")
            ktorProvider.publish("tasks.defend", "Keep everyone safe please")
            ktorProvider.publish("tasks.gather", "We need stone and wood")
        }
        
        launch {
            // Minecraft agents coordinate locally
            minecraftProvider.publish("alerts.emergency", "Help! Creeper at castle!")
            minecraftProvider.publish("agent.builder.response", "On my way! Building can wait")
            minecraftProvider.publish("agent.gatherer.resources", "Dropping off stone at castle!")
        }
        
        delay(100) // Let messages propagate
        
        println("Minecraft agents: Fast local coordination via InMemory")
        println("Cross-environment: Reliable distributed coordination via Redis/GCP")
        
        minecraftProvider.close()
        ktorProvider.close()
    }
    
    /**
     * Demonstrates microservices coordination with different provider strategies.
     */
    private suspend fun demonstrateMicroservicesCoordination() {
        println("\n=== Microservices Coordination ===")
        
        // Service A: User-facing service with mixed requirements
        val userServiceProvider = MultiplexedPubSubProvider {
            // High-frequency user events → fast local processing
            route("user.events.*") to InMemoryPubSubProvider()
            route("user.sessions.*") to InMemoryPubSubProvider()
            
            // Cross-service coordination → distributed
            route("orders.*") to LocalFilePubSubProvider()
            route("notifications.*") to LocalFilePubSubProvider()
            
            defaultProvider = LocalFilePubSubProvider()
        }
        
        // Service B: Order processing service
        val orderServiceProvider = MultiplexedPubSubProvider {
            // Local order processing → fast
            route("orders.local.*") to InMemoryPubSubProvider()
            
            // Cross-service events → distributed
            route("orders.created") to LocalFilePubSubProvider()
            route("orders.completed") to LocalFilePubSubProvider()
            route("inventory.*") to LocalFilePubSubProvider()
            
            defaultProvider = LocalFilePubSubProvider()
        }
        
        // Service C: Notification service (all distributed for reliability)
        val notificationServiceProvider = MultiplexedPubSubProvider {
            defaultProvider = LocalFilePubSubProvider() // All messages are cross-service
        }
        
        // Demonstrate different coordination patterns
        launch {
            // User service: Mix of local and distributed
            userServiceProvider.publish("user.events.login", "User logged in") // Local
            userServiceProvider.publish("orders.created", "New order #1234") // Distributed
        }
        
        launch {
            // Order service: Local processing + distributed events
            orderServiceProvider.publish("orders.local.validate", "Validating order") // Local
            orderServiceProvider.publish("inventory.check", "Check item availability") // Distributed
        }
        
        launch {
            // Notification service: All distributed
            notificationServiceProvider.publish("notifications.email", "Order confirmation") // Distributed
            notificationServiceProvider.publish("notifications.sms", "Delivery update") // Distributed
        }
        
        delay(100)
        
        println("User Service: Mixed local/distributed based on message type")
        println("Order Service: Local processing with distributed coordination")
        println("Notification Service: All distributed for cross-service reliability")
        
        userServiceProvider.close()
        orderServiceProvider.close()
        notificationServiceProvider.close()
    }
}

/**
 * Example agent configuration using MultiplexedPubSubProvider.
 * 
 * This shows how to configure an agent with the multi-provider setup.
 */
public object AgentWithMultiplexedPubSubExample {
    
    public fun createMinecraftBuilderAgent(): AIAgent {
        // This would be configured with actual promptExecutor and strategy
        return AIAgent(
            // promptExecutor = ...,
            // strategy = ...,
        ) {
            install(PubSub) {
                // Multi-provider configuration for Minecraft builder agent
                provider = MultiplexedPubSubProvider {
                    // Fast local coordination with other Minecraft agents
                    route("agent.builder.*") to InMemoryPubSubProvider()
                    route("agent.defender.*") to InMemoryPubSubProvider()
                    route("agent.gatherer.*") to InMemoryPubSubProvider()
                    route("alerts.*") to InMemoryPubSubProvider()
                    
                    // Cross-environment coordination with Ktor planner
                    route("planner.*") to RedisPubSubProvider(redisUri = "redis://localhost:6379")
                    route("tasks.*") to RedisPubSubProvider(redisUri = "redis://localhost:6379")
                    route("status.*") to RedisPubSubProvider(redisUri = "redis://localhost:6379")
                    
                    // Default to local for unknown topics
                    defaultProvider = InMemoryPubSubProvider()
                }
                
                // Subscribe to relevant topics
                autoSubscribeTopics = listOf(
                    "tasks.build",           // Tasks from planner (via Redis)
                    "alerts.emergency",      // Emergency alerts from other agents (via InMemory)
                    "agent.builder.resources" // Resource updates (via InMemory)
                )
                
                // Publish agent events for monitoring
                publishAgentEvents = true
                agentEventTopic = "status.builder"  // Goes to Redis for cross-environment visibility
                
                publishToolEvents = true
                toolEventTopic = "agent.builder.tools"  // Local tool events via InMemory
            }
        }
    }
    
    public fun createKtorPlannerAgent(): AIAgent {
        return AIAgent(
            // promptExecutor = ...,
            // strategy = ...,
        ) {
            install(PubSub) {
                // Planner agent primarily uses distributed coordination
                provider = MultiplexedPubSubProvider {
                    // Cross-environment coordination with Minecraft agents
                    route("tasks.*") to RedisPubSubProvider(redisUri = "redis://localhost:6379")
                    route("status.*") to RedisPubSubProvider(redisUri = "redis://localhost:6379")
                    
                    // High-priority alerts might also go to GCP for reliability
                    route("alerts.critical") to GCPPubSubProvider(
                        projectId = "my-project",
                        credentialsPath = "/path/to/service-account.json"
                    )
                    
                    // Default to Redis for other coordination
                    defaultProvider = RedisPubSubProvider(redisUri = "redis://localhost:6379")
                }
                
                autoSubscribeTopics = listOf(
                    "status.builder",        // Status from builder agent
                    "status.defender",       // Status from defender agent  
                    "status.gatherer",       // Status from gatherer agent
                    "alerts.critical"        // Critical alerts
                )
                
                publishAgentEvents = true
                agentEventTopic = "planner.lifecycle"
            }
        }
    }
}