# Building AI Agents with AWS Bedrock and Koog Framework

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/BedrockAgent.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/BedrockAgent.ipynb
){ .md-button }

Welcome to this comprehensive guide on creating intelligent AI agents using the Koog framework with AWS Bedrock integration. In this notebook, we'll walk through building a functional agent that can control a simple switch device through natural language commands.

## What You'll Learn

- How to define custom tools for AI agents using Kotlin annotations
- Setting up AWS Bedrock integration for LLM-powered agents
- Creating tool registries and connecting them to agents
- Building interactive agents that can understand and execute commands

## Prerequisites

- AWS Bedrock access with appropriate permissions
- AWS credentials configured (access key and secret key)
- Basic understanding of Kotlin coroutines

Let's dive into building our first Bedrock-powered AI agent!


```kotlin
%useLatestDescriptors
// %use koog
```


```kotlin
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

// Simple state-holding device that our agent will control
class Switch {
    private var state: Boolean = false

    fun switch(on: Boolean) {
        state = on
    }

    fun isOn(): Boolean {
        return state
    }
}

/**
 * ToolSet implementation that exposes switch operations to the AI agent.
 *
 * Key concepts:
 * - @Tool annotation marks methods as callable by the agent
 * - @LLMDescription provides natural language descriptions for the LLM
 * - ToolSet interface allows grouping related tools together
 */
class SwitchTools(val switch: Switch) : ToolSet {

    @Tool
    @LLMDescription("Switches the state of the switch to on or off")
    fun switchState(state: Boolean): String {
        switch.switch(state)
        return "Switch turned ${if (state) "on" else "off"} successfully"
    }

    @Tool
    @LLMDescription("Returns the current state of the switch (on or off)")
    fun getCurrentState(): String {
        return "Switch is currently ${if (switch.isOn()) "on" else "off"}"
    }
}
```


```kotlin
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools

// Create our switch instance
val switch = Switch()

// Build the tool registry with our switch tools
val toolRegistry = ToolRegistry {
    // Convert our ToolSet to individual tools and register them
    tools(SwitchTools(switch).asTools())
}

println("✅ Tool registry created with ${toolRegistry.tools.size} tools:")
toolRegistry.tools.forEach { tool ->
    println("  - ${tool.name}")
}
```

    ✅ Tool registry created with 2 tools:
      - getCurrentState
      - switchState



```kotlin
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockRegions

val region = BedrockRegions.US_WEST_2.regionCode
val maxRetries = 3

// Configure Bedrock client settings
val bedrockSettings = BedrockClientSettings(
    region = region, // Choose your preferred AWS region
    maxRetries = maxRetries // Number of retry attempts for failed requests
)

println("🌐 Bedrock configured for region: $region")
println("🔄 Max retries set to: $maxRetries")
```

    🌐 Bedrock configured for region: us-west-2
    🔄 Max retries set to: 3



```kotlin
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor

// Create the Bedrock LLM executor with credentials from environment
val executor = simpleBedrockExecutor(
    awsAccessKeyId = System.getenv("AWS_BEDROCK_ACCESS_KEY")
        ?: throw IllegalStateException("AWS_BEDROCK_ACCESS_KEY environment variable not set"),
    awsSecretAccessKey = System.getenv("AWS_BEDROCK_SECRET_ACCESS_KEY")
        ?: throw IllegalStateException("AWS_BEDROCK_SECRET_ACCESS_KEY environment variable not set"),
    settings = bedrockSettings
)

println("🔐 Bedrock executor initialized successfully")
println("💡 Pro tip: Set AWS_BEDROCK_ACCESS_KEY and AWS_BEDROCK_SECRET_ACCESS_KEY environment variables")
```

    🔐 Bedrock executor initialized successfully
    💡 Pro tip: Set AWS_BEDROCK_ACCESS_KEY and AWS_BEDROCK_SECRET_ACCESS_KEY environment variables



```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.bedrock.BedrockModels

val agent = AIAgent(
    executor = executor,
    llmModel = BedrockModels.AnthropicClaude35SonnetV2, // State-of-the-art reasoning model
    systemPrompt = """
        You are a helpful assistant that controls a switch device.

        You can:
        - Turn the switch on or off when requested
        - Check the current state of the switch
        - Explain what you're doing

        Always be clear about the switch's current state and confirm actions taken.
    """.trimIndent(),
    temperature = 0.1, // Low temperature for consistent, focused responses
    toolRegistry = toolRegistry
)

println("🤖 AI Agent created successfully!")
println("📋 System prompt configured")
println("🛠️  Tools available: ${toolRegistry.tools.size}")
println("🎯 Model: ${BedrockModels.AnthropicClaude35SonnetV2}")
println("🌡️  Temperature: 0.1 (focused responses)")
```

    🤖 AI Agent created successfully!
    📋 System prompt configured
    🛠️  Tools available: 2
    🎯 Model: LLModel(provider=Bedrock, id=us.anthropic.claude-3-5-sonnet-20241022-v2:0, capabilities=[Temperature, Tools, ToolChoice, Image, Document, Completion], contextLength=200000, maxOutputTokens=8192)
    🌡️  Temperature: 0.1 (focused responses)



```kotlin
import kotlinx.coroutines.runBlocking

println("🎉 Bedrock Agent with Switch Tools - Ready to Go!")
println("💬 You can ask me to:")
println("   • Turn the switch on/off")
println("   • Check the current switch state")
println("   • Ask questions about the switch")
println()
println("💡 Example: 'Please turn on the switch' or 'What's the current state?'")
println("📝 Type your request:")

val input = readln()
println("\n🤖 Processing your request...")

runBlocking {
    val response = agent.run(input)
    println("\n✨ Agent response:")
    println(response)
}
```

    🎉 Bedrock Agent with Switch Tools - Ready to Go!
    💬 You can ask me to:
       • Turn the switch on/off
       • Check the current switch state
       • Ask questions about the switch
    
    💡 Example: 'Please turn on the switch' or 'What's the current state?'
    📝 Type your request:



    The execution was interrupted


## What Just Happened? 🎯

When you run the agent, here's the magic that occurs behind the scenes:

1. **Natural Language Processing**: Your input is sent to Claude 3.5 Sonnet via Bedrock
2. **Intent Recognition**: The model understands what you want to do with the switch
3. **Tool Selection**: Based on your request, the agent decides which tools to call
4. **Action Execution**: The appropriate tool methods are invoked on your switch object
5. **Response Generation**: The agent formulates a natural language response about what happened

This demonstrates the core power of the Koog framework - seamless integration between natural language understanding and programmatic actions.

## Next Steps & Extensions

Ready to take this further? Here are some ideas to explore:

### 🔧 Enhanced Tools
```kotlin
@Tool
@LLMDescription("Sets a timer to automatically turn off the switch after specified seconds")
fun setAutoOffTimer(seconds: Int): String

@Tool
@LLMDescription("Gets the switch usage statistics and history")
fun getUsageStats(): String
```

### 🌐 Multiple Devices
```kotlin
class HomeAutomationTools : ToolSet {
    @Tool fun controlLight(room: String, on: Boolean): String
    @Tool fun setThermostat(temperature: Double): String
    @Tool fun lockDoor(doorName: String): String
}
```

### 🧠 Memory & Context
```kotlin
val agent = AIAgent(
    executor = executor,
    // ... other config
    features = listOf(
        MemoryFeature(), // Remember past interactions
        LoggingFeature()  // Track all actions
    )
)
```

### 🔄 Advanced Workflows
```kotlin
// Multi-step workflows with conditional logic
@Tool
@LLMDescription("Executes evening routine: dims lights, locks doors, sets thermostat")
fun eveningRoutine(): String
```

## Key Takeaways

✅ **Tools are functions**: Any Kotlin function can become an agent capability
✅ **Annotations drive behavior**: @Tool and @LLMDescription make functions discoverable
✅ **ToolSets organize capabilities**: Group related tools together logically
✅ **Registries are toolboxes**: ToolRegistry contains all available agent capabilities
✅ **Agents orchestrate everything**: AIAgent brings LLM intelligence + tools together

The Koog framework makes it incredibly straightforward to build sophisticated AI agents that can understand natural language and take real-world actions. Start simple, then expand your agent's capabilities by adding more tools and features as needed.

**Happy agent building!** 🚀

## Testing the Agent

Time to see our agent in action! The agent can now understand natural language requests and use the tools we've provided to control the switch.

**Try these commands:**
- "Turn on the switch"
- "What's the current state?"
- "Switch it off please"
- "Is the switch on or off?"
