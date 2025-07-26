package ai.koog.agents.example.features.pubsub

import ai.koog.agents.features.pubsub.providers.gcp.GCPPubSubProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Simple example testing GCP Pub/Sub provider connectivity and basic operations.
 * 
 * This example works with both:
 * 1. Local GCP Pub/Sub emulator (for development)
 * 2. Real GCP Pub/Sub service (for production)
 * 
 * For local development with emulator:
 * 1. Start emulator: docker-compose -f docker-compose-gcp.yaml up -d
 * 2. Set environment: export PUBSUB_EMULATOR_HOST=localhost:8085
 * 3. Run this example
 * 
 * For production use:
 * 1. Set up GCP credentials and project
 * 2. Unset PUBSUB_EMULATOR_HOST environment variable
 * 3. Run this example
 */
fun main() = runBlocking {
    
    println("=== GCP Pub/Sub Provider Test ===")
    
    // Check if running against emulator
    val emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST")
    if (emulatorHost != null) {
        println("🚀 Using GCP Pub/Sub Emulator at: $emulatorHost")
    } else {
        println("☁️ Using real GCP Pub/Sub service")
        println("⚠️  Make sure you have valid GCP credentials configured")
    }
    
    // Create GCP PubSub provider
    val provider = GCPPubSubProvider(
        projectId = "koog-pubsub-dev", // Use development project ID
        subscriptionPrefix = "koog-test-",
        autoCreateTopics = true,
        autoCreateSubscriptions = true
    )
    
    try {
        println("🔗 Connecting to GCP Pub/Sub...")
        
        // Test connection
        val isConnected = provider.isConnected()
        println("📡 Connection status: ${if (isConnected) "✅ Connected" else "❌ Disconnected"}")
        
        if (!isConnected) {
            println("💡 Tip: For local development, make sure the emulator is running:")
            println("   docker-compose -f docker-compose-gcp.yaml up -d")
            println("   export PUBSUB_EMULATOR_HOST=localhost:8085")
            return@runBlocking
        }
        
        // Test topics
        val testTopics = listOf("test-notifications", "test-events", "test-commands")
        
        println("\n--- Testing basic publish/subscribe operations ---")
        
        // Set up subscribers for each topic
        testTopics.forEach { topic ->
            launch {
                println("👂 Subscribing to topic: $topic")
                provider.subscribe(topic).collect { message ->
                    println("📨 Received on $topic: ${message.content}")
                    println("   Message ID: ${message.messageId}")
                    println("   Attributes: ${message.attributes}")
                    
                    // Acknowledge the message
                    message.acknowledge()
                    println("✅ Message acknowledged")
                }
            }
        }
        
        // Allow subscriptions to initialize
        delay(2000)
        
        // Publish test messages
        println("\n--- Publishing test messages ---")
        
        val messages = listOf(
            "test-notifications" to Pair("Welcome to GCP Pub/Sub!", mapOf("type" to "welcome", "priority" to "low")),
            "test-events" to Pair("User logged in", mapOf("userId" to "12345", "timestamp" to System.currentTimeMillis().toString())),
            "test-commands" to Pair("Process data batch", mapOf("batchId" to "batch-001", "priority" to "high"))
        )
        
        messages.forEach { (topic, messageData) ->
            val (content, attributes) = messageData
            println("📤 Publishing to $topic: $content")
            
            val messageId = provider.publish(topic, content, attributes)
            println("   Published with ID: $messageId")
        }
        
        // Wait for message processing
        println("\n--- Waiting for message processing ---")
        delay(3000)
        
        // Test provider health
        println("\n--- Provider Health Information ---")
        val healthInfo = provider.getHealthInfo()
        healthInfo.forEach { (key, value) ->
            println("$key: $value")
        }
        
        // Test bulk messaging
        println("\n--- Testing bulk messaging ---")
        repeat(5) { i ->
            val messageId = provider.publish(
                "test-notifications",
                "Bulk message #${i + 1}",
                mapOf(
                    "sequence" to (i + 1).toString(),
                    "batch" to "bulk-test",
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
            println("📤 Bulk message ${i + 1} published with ID: $messageId")
        }
        
        // Final wait for processing
        delay(2000)
        
        println("\n✅ GCP Pub/Sub test completed successfully!")
        
    } catch (e: Exception) {
        println("❌ Error during GCP Pub/Sub operations: ${e.message}")
        e.printStackTrace()
        
        if (emulatorHost != null) {
            println("\n💡 Troubleshooting tips for emulator:")
            println("   1. Verify emulator is running: docker ps | grep pubsub")
            println("   2. Check emulator logs: docker-compose -f docker-compose-gcp.yaml logs")
            println("   3. Verify environment variable: echo \$PUBSUB_EMULATOR_HOST")
        } else {
            println("\n💡 Troubleshooting tips for GCP:")
            println("   1. Verify GCP credentials: gcloud auth application-default login")
            println("   2. Check project permissions for Pub/Sub API")
            println("   3. Verify project ID is correct")
        }
    } finally {
        // Cleanup
        println("\n🧹 Cleaning up resources...")
        provider.close()
    }
}