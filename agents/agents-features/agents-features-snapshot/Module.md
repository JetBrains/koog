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

Then, install the Persistency feature when creating your agent:

```kotlin
val agent = AIAgent(
    // other configuration parameters
) {
    install(Persistency) {
        // Configure the storage provider
        storage = InMemoryPersistencyStorageProvider("agent-persistence-id")

        // Optional: enable automatic checkpoint creation after each node
        enableAutomaticPersistency = true

        // Use `RollbackStrategy.Default` if you want to checkpoint the whole state machine and continue from the same node in your strategy graph, 
        // or `RollbackStrategy.MessageHistoryOnly` if you only want to checkpoint messages
        rollbackStrategy = RollbackStrategy.Default
        
        // Optional -- if you want to also roll back all side-effects produced by some tools when rolling back in time to some checkpoint,
        // you can also provide a `RollbackToolRegistry` and register rollback-tools for each relevant agent tool in your main `ToolRegistry`
        rollbackToolRegistry = RollbackToolRegistry {
            registerRollback(::someTool, ::howToRollbackThisTool)
            registerRollback(::anotherTool, ::sideEffectsRemover)
        }
    }
}
```

### Example of usage


Here's an example of running the agent with checkpoints:
```kotlin
val agent = AIAgent(
    executor = executor,
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
) {
    install(Persistency) {
        storage = snapshotProvider
        enableAutomaticPersistency = true
    }
}

```
