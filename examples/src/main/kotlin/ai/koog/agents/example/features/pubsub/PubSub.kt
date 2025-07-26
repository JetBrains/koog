package ai.koog.agents.example.features.pubsub

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.providers.local.LocalFilePubSubProvider
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating PubSub feature for multiagent coordination.
 * 
 * This example shows:
 * - Setting up agents with PubSub capabilities
 * - Publishing and subscribing to messages between agents
 * - Coordinating agent behavior through message passing
 * - Using the LocalFilePubSubProvider for true cross-process coordination
 * 
 * For production use cases, you can replace LocalFilePubSubProvider with:
 * - RedisPubSubProvider for high-performance distributed messaging
 * - GCPPubSubProvider for enterprise-scale messaging with guaranteed delivery
 */
fun main() = runBlocking {
    
    println("=== PubSub Feature Example ===")
    println("Demonstrating multiagent coordination through message passing")
    
    // Create a shared PubSub provider for all agents
    // This enables true cross-process coordination through file-based messaging
    val pubSubProvider = LocalFilePubSubProvider()
    
    try {
        // Coordinator Agent - orchestrates tasks and delegates work
        val coordinatorAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Task Coordinator. Your job is to:
                1. Break down complex tasks into smaller subtasks
                2. Delegate subtasks to worker agents via messages
                3. Monitor progress and coordinate the final result
                
                When you want to delegate work, describe the task clearly.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("task-results", "worker-status")
                publishAgentEvents = true
                publishToolEvents = true
            }
        }
        
        // Worker Agent 1 - handles text processing tasks
        val textWorkerAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),  
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Text Processing Worker. You specialize in:
                - Text analysis and summarization
                - Writing and editing
                - Language processing tasks
                
                Listen for work assignments and complete them efficiently.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("text-tasks")
                publishAgentEvents = true
            }
        }
        
        // Worker Agent 2 - handles data analysis tasks  
        val dataWorkerAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Data Analysis Worker. You specialize in:
                - Mathematical calculations
                - Data processing and analysis
                - Statistical insights
                
                Listen for work assignments and provide analytical results.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("data-tasks")
                publishAgentEvents = true
            }
        }
        
        // Use agents and demonstrate coordination
        coordinatorAgent.use { coordinator ->
            textWorkerAgent.use { textWorker ->
                dataWorkerAgent.use { dataWorker ->
                    
                    println("\n--- Setting up agent subscriptions ---")
                    
                    // Set up message handlers for worker agents
                    launch {
                        pubSubProvider.subscribe("text-tasks").collect { message ->
                            println("📝 Text Worker received task: ${message.content}")
                            
                            // Process the task
                            val result = textWorker.run("Complete this text processing task: ${message.content}")
                            
                            // Report back to coordinator
                            pubSubProvider.publish(
                                "task-results",
                                "Text task completed: $result",
                                mapOf(
                                    "worker" to "text-worker",
                                    "original-task" to message.content,
                                    "status" to "completed"
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    launch {
                        pubSubProvider.subscribe("data-tasks").collect { message ->
                            println("📊 Data Worker received task: ${message.content}")
                            
                            // Process the task
                            val result = dataWorker.run("Complete this data analysis task: ${message.content}")
                            
                            // Report back to coordinator
                            pubSubProvider.publish(
                                "task-results", 
                                "Data task completed: $result",
                                mapOf(
                                    "worker" to "data-worker",
                                    "original-task" to message.content,
                                    "status" to "completed"
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Set up coordinator to handle results
                    launch {
                        val completedTasks = mutableListOf<String>()
                        
                        pubSubProvider.subscribe("task-results").collect { message ->
                            println("🎯 Coordinator received result: ${message.content}")
                            completedTasks.add(message.content)
                            
                            // If we have results from both workers, coordinate final response
                            if (completedTasks.size >= 2) {
                                println("\n--- All subtasks completed! Coordinator finalizing ---")
                                val finalResult = coordinator.run(
                                    "Combine these completed subtask results into a final comprehensive response: ${completedTasks.joinToString("; ")}"
                                )
                                
                                println("🎉 Final coordinated result: $finalResult")
                                
                                // Publish completion notification
                                pubSubProvider.publish(
                                    "project-complete",
                                    finalResult,
                                    mapOf("project-status" to "completed")
                                )
                            }
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Allow subscriptions to initialize
                    delay(100)
                    
                    println("\n--- Starting coordinated task execution ---")
                    
                    // Coordinator delegates work to specialized agents
                    val complexTask = "Create a comprehensive analysis of remote work trends, including a summary of key findings and statistical insights"
                    
                    println("🎯 Coordinator received complex task: $complexTask")
                    
                    val coordinatorResponse = coordinator.run(
                        "Break down this complex task and delegate appropriate parts to specialized workers: $complexTask"
                    )
                    
                    println("🧠 Coordinator planning: $coordinatorResponse")
                    
                    // Publish delegated tasks
                    pubSubProvider.publish(
                        "text-tasks",
                        "Write a comprehensive summary of remote work trends and their impact on productivity",
                        mapOf("priority" to "high", "deadline" to "immediate")
                    )
                    
                    pubSubProvider.publish(
                        "data-tasks", 
                        "Analyze remote work statistics and provide key numerical insights and trends",
                        mapOf("priority" to "high", "deadline" to "immediate")
                    )
                    
                    // Wait for coordination to complete
                    println("\n--- Waiting for agents to coordinate and complete work ---")
                    delay(10000) // Give enough time for all agents to process
                    
                    // Show health status
                    println("\n--- PubSub Provider Health Status ---")
                    val healthInfo = pubSubProvider.getHealthInfo()
                    healthInfo.forEach { (key, value) ->
                        println("$key: $value")
                    }
                }
            }
        }
        
    } finally {
        // Cleanup
        pubSubProvider.close()
        println("\n--- PubSub example completed ---")
    }
}

/**
 * Simple example showing basic PubSub operations without agent coordination.
 */
@Suppress("unused")
fun basicPubSubExample() = runBlocking {
    
    println("=== Basic PubSub Operations Example ===")
    
    val provider = LocalFilePubSubProvider()
    
    try {
        // Create a simple agent with PubSub
        val agent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = "You are a helpful assistant."
        ) {
            install(PubSub) {
                provider = this@try.provider
                autoSubscribeTopics = listOf("notifications", "commands")
                publishAgentEvents = true
            }
        }
        
        agent.use { 
            // Subscribe to messages
            launch {
                provider.subscribe("notifications").collect { message ->
                    println("📢 Received notification: ${message.content}")
                    println("   Attributes: ${message.attributes}")
                    message.acknowledge()
                }
            }
            
            // Allow subscription to initialize
            delay(50)
            
            // Publish some test messages
            println("\n--- Publishing test messages ---")
            
            val messageId1 = provider.publish(
                "notifications",
                "Welcome to the PubSub system!",
                mapOf("type" to "welcome", "priority" to "low")
            )
            println("Published message with ID: $messageId1")
            
            val messageId2 = provider.publish(
                "notifications", 
                "System maintenance scheduled",
                mapOf("type" to "maintenance", "priority" to "high")
            )
            println("Published message with ID: $messageId2")
            
            // Wait for message processing
            delay(100)
            
            println("\n--- Provider Status ---")
            println("Connected: ${provider.isConnected()}")
            println("Health: ${provider.getHealthInfo()}")
        }
        
    } finally {
        provider.close()
    }
}