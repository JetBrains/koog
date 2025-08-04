package ai.koog.agents.example.cancellation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.CancellationReason
import ai.koog.agents.core.agent.RunOutcome
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.agent.*
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Simple example demonstrating the new agent cancellation functionality.
 * 
 * This example shows the key features of the direct AIAgent cancellation integration:
 * - Using runCancellable() for tri-state outcomes (Success/Failure/Cancelled)  
 * - Extension functions for common patterns (timeout, fallback values)
 * - Clean handling of different execution outcomes
 * - No additional runner infrastructure needed
 */
fun main(): Unit = runBlocking {
    println("🚀 Simple Agent Cancellation Example")
    println("=" * 50)
    
    // Create a simple agent using the existing API
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a helpful assistant. Be concise.",
        strategy = singleRunStrategy()
    )

    // Scenario 1: Normal execution with runCancellable() 
    println("\n📖 Scenario 1: Normal Execution")
    val outcome1 = agent.runCancellable("Hello, how are you?")
    
    when (outcome1) {
        is RunOutcome.Success -> println("✅ Success: ${outcome1.value}")
        is RunOutcome.Failure -> println("❌ Failure: ${outcome1.error.message}")
        is RunOutcome.Cancelled -> println("🛑 Cancelled: ${outcome1.reason} - ${outcome1.message}")
    }

    // Scenario 2: Using extension function for timeout
    println("\n📖 Scenario 2: Timeout Protection")
    val outcome2 = agent.runWithTimeout("Tell me a long story", 2.seconds)
    
    when (outcome2) {
        is RunOutcome.Success -> println("✅ Success: ${outcome2.value}")
        is RunOutcome.Failure -> println("❌ Failure: ${outcome2.error.message}")
        is RunOutcome.Cancelled -> {
            println("🛑 Cancelled: ${outcome2.reason}")
            if (outcome2.reason == CancellationReason.Timeout) {
                println("   ⏰ Agent execution timed out")
            }
        }
    }

    // Scenario 3: Using fallback values 
    println("\n📖 Scenario 3: Fallback Values")
    val result3 = agent.runOrDefault("What's the weather?", "Weather information unavailable")
    println("🌤️  Result with fallback: $result3")

    // Scenario 4: Nullable results
    println("\n📖 Scenario 4: Nullable Results")
    val result4 = agent.runOrNull("Simple question")
    if (result4 != null) {
        println("✅ Got result: $result4")
    } else {
        println("❌ No result (failed or cancelled)")
    }

    // Scenario 5: Pattern matching approach
    println("\n📖 Scenario 5: Functional Pattern Matching")
    val transformedResult = agent.runAndMap(
        input = "What is 2+2?",
        onSuccess = { "Math result: $it" },
        onFailure = { "Error occurred: ${it.message}" },
        onCancelled = { reason, msg -> "Operation cancelled ($reason): $msg" }
    )
    println("🔢 Transformed result: $transformedResult")

    println("\n💡 Key Benefits Demonstrated:")
    println("  ✅ Direct integration with AIAgent - no new runner concepts")
    println("  ✅ Tri-state outcomes distinguish success/failure/cancellation")
    println("  ✅ Extension functions provide common usage patterns")
    println("  ✅ Clean, readable code with minimal cognitive overhead")
    println("  ✅ Backward compatible - existing .run() calls unchanged")
}

/**
 * Extension function for string repetition (for formatting)
 */
private operator fun String.times(count: Int): String = repeat(count)