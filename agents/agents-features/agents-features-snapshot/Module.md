# Module agents-features-snapshot

Provides checkpoint functionality for AI agents, allowing saving and restoring agent state at specific points during execution.

### Overview

The agents-features-snapshot module enables checkpoint capabilities for AI agents in the Koog framework, allowing you to:

- Resume agent execution from a specific point
- Roll back to previous states
- Persist agent state across sessions
- Implement continuous persistence of agent state

Key features include:
- Checkpoint creation and management
- Multiple storage providers (in-memory, file-based)
- Automatic checkpoint creation after each node execution (optional)
- Complete state capture including message history, current node, and input data

### Using in your project

To use the checkpoint feature in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-snapshot:$version")
}
```

Then, install the AgentCheckpoint feature when creating your agent:

```kotlin
val agent = AIAgent(
    // other configuration parameters
) {
    install(AgentCheckpoint) {
        // Configure the storage provider
        snapshotProvider(InMemoryAgentCheckpointStorageProvider())
        
        // Optional: enable automatic checkpoint creation after each node
        continuouslyPersistent()
    }
}
```

### Using in tests

For testing agents with checkpoint capabilities, you can use an in-memory storage provider:

```kotlin
// Create a test agent with checkpoint capability
val testAgent = AIAgent(
    // other test configuration
) {
    install(AgentCheckpoint) {
        // Use in-memory storage for testing
        snapshotProvider(InMemoryAgentCheckpointStorageProvider())
    }
    
    // Enable testing mode
    withTesting()
}
```

### Example of usage

Here's an example of using checkpoints to save and restore agent state:

```kotlin
// Create an agent with checkpoint capability
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(AgentCheckpoint) {
            // Use file-based storage for persistence
            snapshotProvider(FileAgentCheckpointStorageProvider(
                rootPath = Path("checkpoints")
            ))
        }
    }
)

// Run the agent and create a checkpoint
val result = agent.run("Tell me about AI agents") { context ->
    // Create a checkpoint at this point
    val checkpoint = context.persistency().createCheckpoint(
        agentContext = context,
        nodeId = context.currentNodeId ?: "unknown",
        lastInput = "Tell me about AI agents",
        lastInputType = typeOf<String>()
    )
    
    // Store the checkpoint ID for later use
    val checkpointId = checkpoint?.checkpointId
    println("Created checkpoint: $checkpointId")
}

// Later, restore from the checkpoint
agent.run("Continue from where we left off") { context ->
    // Roll back to the latest checkpoint
    val restoredCheckpoint = context.persistency().rollbackToLatestCheckpoint(context)
    
    if (restoredCheckpoint != null) {
        println("Restored from checkpoint: ${restoredCheckpoint.checkpointId}")
    } else {
        println("No checkpoint found")
    }
}
```

This example demonstrates creating an agent with checkpoint capability, creating a checkpoint during execution, and later restoring from that checkpoint to continue the conversation.