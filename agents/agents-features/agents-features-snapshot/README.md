# AgentCheckpoint

AgentCheckpoint is a feature that provides checkpoint functionality for AI agents in the Koog Agents framework. It allows saving and restoring the state of an agent at specific points during execution, enabling powerful capabilities such as:

- Resuming agent execution from a specific point
- Rolling back to previous states
- Persisting agent state across sessions

## Core Concepts

### Checkpoints

A checkpoint captures the complete state of an agent at a specific point in its execution, including:

- Message history (all interactions between user, system, assistant, and tools)
- Current node being executed
- Input data for the current node
- Timestamp of creation

Checkpoints are identified by unique IDs and are associated with a specific agent.

### Storage Providers

AgentCheckpoint uses storage providers to save and retrieve checkpoints. The framework includes several built-in providers:

- `InMemoryAgentCheckpointStorageProvider`: Stores checkpoints in memory (lost when the application restarts)
- `FileAgentCheckpointStorageProvider`: Persists checkpoints to the file system
- `NoAgentCheckpointStorageProvider`: A no-op implementation (default) that doesn't store checkpoints

You can also implement custom storage providers by implementing the `AgentCheckpointStorageProvider` interface.

### Continuous Persistence

The feature can be configured to automatically create checkpoints after each node execution, ensuring that the agent's state is continuously persisted and can be recovered at any point.

## Installation

To use the AgentCheckpoint feature, you need to add it to your agent's configuration:

```kotlin
val agent = aiAgent {
    // ... other configuration
    
    features {
        install(AgentCheckpoint) {
            // Configure the feature
            snapshotProvider(InMemoryAgentCheckpointStorageProvider())
            continuouslyPersistent() // Optional: create checkpoints after each node
        }
    }
}
```

## Configuration Options

The AgentCheckpoint feature has two main configuration options:

### Storage Provider

Set the storage provider that will be used to save and retrieve checkpoints:

```kotlin
install(AgentCheckpoint) {
    snapshotProvider(InMemoryAgentCheckpointStorageProvider())
}
```

Available built-in providers:
- `InMemoryAgentCheckpointStorageProvider`: In-memory storage (non-persistent)
- `FileAgentCheckpointStorageProvider`: File-based storage
- `NoAgentCheckpointStorageProvider`: No-op provider (default)

### Continuous Persistence

Enable automatic checkpoint creation after each node execution:

```kotlin
install(AgentCheckpoint) {
    continuouslyPersistent()
}
```

When enabled, the agent will automatically create a checkpoint after each node is executed, allowing for fine-grained recovery.

## Basic Usage

### Creating a Checkpoint

Create a checkpoint at a specific point in your agent's execution:

```kotlin
suspend fun example(context: AIAgentContextBase) {
    // Create a checkpoint with the current state
    val checkpoint = context.checkpoint().createCheckpoint(
        agentId = context.id,
        agentContext = context,
        nodeId = "current-node-id",
        lastInput = inputData
    )
    
    // The checkpoint ID can be stored for later use
    val checkpointId = checkpoint.checkpointId
}
```

### Restoring from a Checkpoint

Restore an agent's state from a specific checkpoint:

```kotlin
suspend fun example(context: AIAgentContextBase, checkpointId: String) {
    // Roll back to a specific checkpoint
    context.checkpoint().rollbackToCheckpoint(checkpointId, context)
    
    // Or roll back to the latest checkpoint
    context.checkpoint().rollbackToLatestCheckpoint(context)
}
```

### Using Extension Functions

The feature provides convenient extension functions for working with checkpoints:

```kotlin
suspend fun example(context: AIAgentContextBase) {
    // Access the checkpoint feature
    val checkpointFeature = context.checkpoint()
    
    // Or perform an action with the checkpoint feature
    context.withCheckpoints(context) { ctx ->
        // 'this' is the checkpoint feature
        createCheckpoint(ctx.id, ctx, "node-id", inputData)
    }
}
```

## Advanced Usage

### Custom Storage Providers

You can implement custom storage providers by implementing the `AgentCheckpointStorageProvider` interface:

```kotlin
class MyCustomStorageProvider : AgentCheckpointStorageProvider {
    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        // Implementation
    }
    
    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        // Implementation
    }
    
    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        // Implementation
    }
}
```

Then use your custom provider in the feature configuration:

```kotlin
install(AgentCheckpoint) {
    snapshotProvider(MyCustomStorageProvider())
}
```

### Setting Execution Points

For advanced control, you can directly set the execution point of an agent:

```kotlin
fun example(context: AIAgentContextBase) {
    context.checkpoint().setExecutionPoint(
        agentContext = context,
        nodeId = "target-node-id",
        messageHistory = customMessageHistory,
        input = customInput
    )
}
```

This allows for more fine-grained control over the agent's state beyond just restoring from checkpoints.

## Requirements

The AgentCheckpoint feature requires that all nodes in your agent's strategy have unique names. This is enforced when the feature is installed:

```kotlin
require(ctx.strategy.metadata.uniqueNames) { 
    "Checkpoint feature requires unique node names in the strategy metadata" 
}
```

Make sure to set `uniqueNames = true` in your strategy metadata when using this feature.
