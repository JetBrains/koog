package ai.koog.agents.example.features.pubsub

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.providers.redis.RedisPubSubProvider
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.lettuce.core.RedisURI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating Redis PubSub for high-performance multiagent coordination.
 *
 * **Prerequisites:**
 * 1. Start Redis with Docker Compose:
 *    ```bash
 *    docker-compose -f docker-compose-redis.yaml up -d
 *    ```
 *
 * 2. Verify Redis is running:
 *    ```bash
 *    docker exec koog-pubsub-redis redis-cli ping
 *    # Should return: PONG
 *    ```
 *
 * **Features demonstrated:**
 * - High-performance Redis pub/sub with Lettuce client
 * - Cross-process agent coordination
 * - Kotlin coroutines integration
 * - Message attributes encoding/decoding
 * - Connection health monitoring
 * - Graceful error handling
 *
 * **Architecture:**
 * - Publisher Agent: Sends task assignments to Redis
 * - Worker Agents: Subscribe to Redis channels for work
 * - Redis: High-performance message broker with persistence
 *
 * **Production benefits:**
 * - Scales to thousands of agents
 * - Sub-millisecond message delivery
 * - Built-in persistence and clustering
 * - Supports pattern subscriptions
 */
fun main() = runBlocking {
    
    println("=== Redis PubSub Example ===")
    println("High-performance multiagent coordination with Redis")
    
    // Configure Redis connection
    val redisUri = RedisURI.create("redis://localhost:6379")
    val pubSubProvider = RedisPubSubProvider(
        redisUri = redisUri,
        keyPrefix = "koog-agents:",
        connectionTimeout = 5000,
        enablePatternSubscription = false
    )
    
    try {
        // Check Redis connection
        if (!pubSubProvider.isConnected()) {
            println("❌ Redis is not available. Please start it with:")
            println("   docker-compose -f docker-compose-redis.yaml up -d")
            return@runBlocking
        }
        
        println("✅ Connected to Redis")
        
        // Task Coordinator Agent
        val coordinatorAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Task Coordinator using Redis for high-performance message distribution.
                Break down complex tasks and delegate them efficiently to worker agents.
                Monitor performance and coordinate results quickly.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("task-completed", "worker-status")
                publishAgentEvents = true
            }
        }
        
        // Analytics Worker Agent
        val analyticsWorkerAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are an Analytics Worker specializing in:
                - Data analysis and statistical processing
                - Performance metrics calculation
                - Trend analysis and insights
                
                Process tasks quickly and report results via Redis.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("analytics-tasks", "priority-analytics")
                publishAgentEvents = true
            }
        }
        
        // Content Worker Agent
        val contentWorkerAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Content Worker specializing in:
                - Text generation and editing
                - Content optimization
                - Creative writing and summarization
                
                Handle content tasks efficiently via Redis messaging.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("content-tasks", "urgent-content")
                publishAgentEvents = true
            }
        }
        
        coordinatorAgent.use { coordinator ->
            analyticsWorkerAgent.use { analyticsWorker ->
                contentWorkerAgent.use { contentWorker ->
                    
                    println("\n--- Setting up Redis message handlers ---")
                    
                    // Analytics worker handler
                    launch {
                        pubSubProvider.subscribe(listOf("analytics-tasks", "priority-analytics")).collect { message ->
                            println("📊 Analytics Worker processing: ${message.content}")
                            println("   📋 Attributes: ${message.attributes}")
                            
                            val priority = message.attributes["priority"] ?: "normal"
                            val taskType = message.attributes["type"] ?: "general"
                            
                            val result = analyticsWorker.run(
                                "Process this $priority priority $taskType analytics task: ${message.content}"
                            )
                            
                            // Report completion with performance metrics
                            pubSubProvider.publish(
                                "task-completed",
                                "Analytics task completed: $result",
                                mapOf(
                                    "worker" to "analytics",
                                    "task-id" to (message.attributes["task-id"] ?: "unknown"),
                                    "completion-time" to System.currentTimeMillis().toString(),
                                    "priority" to priority
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Content worker handler
                    launch {
                        pubSubProvider.subscribe(listOf("content-tasks", "urgent-content")).collect { message ->
                            println("✍️ Content Worker processing: ${message.content}")
                            println("   📋 Attributes: ${message.attributes}")
                            
                            val urgency = message.attributes["urgency"] ?: "normal"
                            val contentType = message.attributes["type"] ?: "general"
                            
                            val result = contentWorker.run(
                                "Handle this $urgency urgency $contentType content task: ${message.content}"
                            )
                            
                            // Report completion with content metrics
                            pubSubProvider.publish(
                                "task-completed",
                                "Content task completed: $result",
                                mapOf(
                                    "worker" to "content",
                                    "task-id" to (message.attributes["task-id"] ?: "unknown"),
                                    "completion-time" to System.currentTimeMillis().toString(),
                                    "urgency" to urgency,
                                    "word-count" to result.split(" ").size.toString()
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Coordinator result handler
                    launch {
                        val completedTasks = mutableMapOf<String, String>()
                        
                        pubSubProvider.subscribe("task-completed").collect { message ->
                            println("🎯 Coordinator received completion: ${message.content}")
                            
                            val taskId = message.attributes["task-id"] ?: "unknown"
                            val worker = message.attributes["worker"] ?: "unknown"
                            
                            completedTasks[taskId] = "${worker}: ${message.content}"
                            
                            // When we have results from multiple workers, coordinate
                            if (completedTasks.size >= 2) {
                                println("\n--- Coordinator synthesizing results ---")
                                
                                val finalResult = coordinator.run(
                                    """
                                    Synthesize these completed task results into a comprehensive final report:
                                    ${completedTasks.values.joinToString("\n\n")}
                                    
                                    Focus on insights and actionable recommendations.
                                    """.trimIndent()
                                )
                                
                                println("🎉 Final coordinated result:")
                                println(finalResult)
                                
                                // Publish final completion
                                pubSubProvider.publish(
                                    "project-complete",
                                    finalResult,
                                    mapOf(
                                        "project-status" to "completed",
                                        "tasks-completed" to completedTasks.size.toString(),
                                        "completion-timestamp" to System.currentTimeMillis().toString()
                                    )
                                )
                            }
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Allow message handlers to initialize
                    delay(100)
                    
                    println("\n--- Demonstrating high-performance Redis coordination ---")
                    
                    // Simulate a complex business scenario
                    val projectTask = "Analyze Q4 performance metrics and create executive summary with recommendations"
                    
                    println("🎯 Coordinator received project: $projectTask")
                    
                    val coordination = coordinator.run(
                        "Break down this business project into specialized tasks: $projectTask"
                    )
                    
                    println("🧠 Coordinator strategy: $coordination")
                    
                    // Publish high-priority analytics task
                    val analyticsTaskId = "analytics-${System.currentTimeMillis()}"
                    pubSubProvider.publish(
                        "priority-analytics",
                        "Calculate Q4 KPIs, revenue trends, and performance comparisons vs Q3",
                        mapOf(
                            "task-id" to analyticsTaskId,
                            "priority" to "high",
                            "type" to "performance-metrics",
                            "deadline" to "immediate"
                        )
                    )
                    
                    // Publish urgent content task
                    val contentTaskId = "content-${System.currentTimeMillis()}"
                    pubSubProvider.publish(
                        "urgent-content",
                        "Write executive summary highlighting key achievements, challenges, and strategic recommendations",
                        mapOf(
                            "task-id" to contentTaskId,
                            "urgency" to "high",
                            "type" to "executive-summary",
                            "audience" to "C-level",
                            "length" to "concise"
                        )
                    )
                    
                    println("\n--- Redis processing tasks with high performance ---")
                    delay(8000) // Allow Redis to process efficiently
                    
                    // Show Redis performance metrics
                    println("\n--- Redis Provider Health Status ---")
                    val healthInfo = pubSubProvider.getHealthInfo()
                    healthInfo.forEach { (key, value) ->
                        println("$key: $value")
                    }
                }
            }
        }
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        println("Make sure Redis is running with: docker-compose -f docker-compose-redis.yaml up -d")
    } finally {
        pubSubProvider.close()
        println("\n--- Redis PubSub example completed ---")
    }
}

/**
 * Utility function to demonstrate Redis pattern subscriptions.
 */
@Suppress("unused")
fun demonstrateRedisPatterns() = runBlocking {
    println("=== Redis Pattern Subscription Demo ===")
    
    val pubSubProvider = RedisPubSubProvider(
        redisUri = RedisURI.create("redis://localhost:6379"),
        keyPrefix = "koog:",
        enablePatternSubscription = true
    )
    
    try {
        // Subscribe to all task-related topics using pattern
        launch {
            pubSubProvider.subscribe(listOf("*-tasks", "priority-*")).collect { message ->
                println("📨 Pattern matched: ${message.topic} -> ${message.content}")
                message.acknowledge()
            }
        }
        
        delay(100)
        
        // Publish to various topics that match patterns
        pubSubProvider.publish("analytics-tasks", "Pattern test 1", mapOf("pattern" to "tasks"))
        pubSubProvider.publish("priority-urgent", "Pattern test 2", mapOf("pattern" to "priority"))
        pubSubProvider.publish("content-tasks", "Pattern test 3", mapOf("pattern" to "tasks"))
        
        delay(1000)
        
    } finally {
        pubSubProvider.close()
    }
}