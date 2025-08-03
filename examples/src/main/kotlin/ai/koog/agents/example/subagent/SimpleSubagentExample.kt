package ai.koog.agents.example.subagent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.subagent.SafetyPolicies
import ai.koog.agents.core.subagent.asSafeTool
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Streamlined safe agent execution example demonstrating multi-agent orchestration.
 * 
 * This example shows the simplified architecture:
 * 1. Creating specialized agents using standard Koog patterns
 * 2. Wrapping them with safety policies using asSafeTool()
 * 3. Registering them in ToolRegistry alongside traditional tools
 * 4. Using an orchestrator agent to coordinate specialists
 */

fun main(): Unit = runBlocking {
    println("🤖 Streamlined Safe Agent System Example")
    println("=========================================")
    
    // Check for API key
    if (ApiKeyService.openAIApiKey.isBlank()) {
        println("❌ Please configure your OpenAI API key in ApiKeyService")
        return@runBlocking
    }
    
    // 1. Create specialized agents using standard Koog patterns
    val uppercaseAgent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "Convert the input text to UPPERCASE. Return only the uppercase text, nothing else.",
        temperature = 0.0
    )
    
    val wordCountAgent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "Count the number of words in the input text. Return only the number as 'Word count: X', nothing else.",
        temperature = 0.0
    )
    
    val sentimentAgent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "Analyze the sentiment of the input text. Return only 'POSITIVE', 'NEGATIVE', or 'NEUTRAL', nothing else.",
        temperature = 0.0
    )
    
    // 2. Create ToolRegistry with safe agent tools
    val toolRegistry = ToolRegistry {
        // Standard user interaction tools
        tool(AskUser)
        tool(SayToUser)
        
        // Safe agent tools with different safety policies
        tool(uppercaseAgent.asSafeTool(
            agentName = "uppercase-converter",
            agentDescription = "Converts text to uppercase with conservative safety limits",
            inputDescriptor = ToolParameterDescriptor(
                name = "text",
                description = "Text to convert to uppercase",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe(maxDepth = 2) // Conservative policy
        ))
        
        tool(wordCountAgent.asSafeTool(
            agentName = "word-counter",
            agentDescription = "Counts words in text with conservative safety limits",
            inputDescriptor = ToolParameterDescriptor(
                name = "text",
                description = "Text to count words in",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe(maxDepth = 2) // Conservative policy
        ))
        
        tool(sentimentAgent.asSafeTool(
            agentName = "sentiment-analyzer",
            agentDescription = "Analyzes sentiment with trusted safety limits",
            inputDescriptor = ToolParameterDescriptor(
                name = "text",
                description = "Text to analyze sentiment of",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.trusted(maxDepth = 3) // More relaxed for analysis
        ))
    }
    
    // 3. Create orchestrator agent that coordinates specialists
    val orchestrator = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = """
            You are an orchestrator agent that coordinates specialized text processing agents.
            
            You have access to these specialized tools:
            - uppercase-converter: Converts text to uppercase
            - word-counter: Counts words in text  
            - sentiment-analyzer: Analyzes sentiment of text
            
            When given input text, use ALL THREE tools to process it, then provide a summary.
            
            Format your final response as:
            Original: [original text]
            Uppercase: [result from uppercase-converter]
            Word Count: [result from word-counter]  
            Sentiment: [result from sentiment-analyzer]
            
            Summary: [brief summary of the analysis]
        """.trimIndent(),
        temperature = 0.0,
        toolRegistry = toolRegistry
    )
    
    // 4. Demonstrate the system
    println("\n📝 Processing sample text through specialized agents...")
    
    val sampleTexts = listOf(
        "Hello world, this is a wonderful day!",
        "I am feeling quite frustrated with this situation.",
        "The weather today is okay, nothing special."
    )
    
    for ((index, text) in sampleTexts.withIndex()) {
        println("\n--- Sample ${index + 1} ---")
        println("Input: $text")
        println("\n🔄 Processing through orchestrator agent...")
        
        try {
            val result = orchestrator.run(text)
            println("\n✅ Result:")
            println(result)
        } catch (e: Exception) {
            println("\n❌ Error during processing: ${e.message}")
        }
        
        if (index < sampleTexts.size - 1) {
            println("\n" + "=".repeat(50))
        }
    }
    
    println("\n🎉 Example completed!")
    println("\nThis demonstrates:")
    println("• Safe agent wrapping with asSafeTool()")
    println("• Different safety policies (safe vs trusted)")
    println("• Seamless integration with ToolRegistry") 
    println("• Multi-agent orchestration with safety guarantees")
}