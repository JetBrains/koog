# Agent Persistence

Agent Persistence is a feature that provides checkpoint functionality for AI agents in the Koog framework.
It lets you save and restore the state of an agent at specific points during execution, enabling capabilities such as:

- Resuming agent execution from a specific point
- Rolling back to previous states
- Persisting agent state across sessions

## Key concepts

### Checkpoints

A checkpoint captures the complete state of an agent at a specific point in its execution, including:

- Message history (all interactions between user, system, assistant, and tools)
- Current node being executed
- Input data for the current node
- Timestamp of creation

Checkpoints are identified by unique IDs and are associated with a specific agent.

## Installation

To use the Agent Persistence feature, add it to your agent's configuration:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.agents.core.agent.context.RollbackStrategy

val executor = simpleOllamaAIExecutor()
-->

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
    install(Persistence) {
        // Use in-memory storage for snapshots
        storage = InMemoryPersistenceStorageProvider()
        // Enable automatic persistence after each node
        enableAutomaticPersistence = true
        /* 
         Select which state will be restored on a new agent run.
     
         Available options are:
         1. Default: Restores the agent to the exact execution point (node in the strategy graph) where it stopped.
            This is especially useful for building complex, fault-tolerant agents.
         2. MessageHistoryOnly: Restores only the message history to the last saved state.
            The agent will always restart from the first node in the strategy graph, but with history from previous runs.
            This is useful for building conversational agents or chatbots.
        */
        rollbackStrategy = RollbackStrategy.MessageHistoryOnly
    }
}
```

!!! tip
    Combine `enableAutomaticPersistence = true` with `RollbackStrategy.MessageHistoryOnly` to create agents that 
    maintain conversation context across multiple sessions.    

<!--- KNIT example-agent-persistence-01.kt -->

## Configuration options

The Agent Persistence feature has three main configuration options:

- **Storage provider**: the provider used to save and retrieve checkpoints.
- **Continuous persistence**: automatic creation of checkpoints after each node is run.
- **Rollback strategy**: determines which state will be restored when rolling back to a checkpoint.

### Storage provider

Set the storage provider that will be used to save and retrieve checkpoints:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX 
} 
-->

```kotlin
install(Persistence) {
    storage = InMemoryPersistenceStorageProvider()
}
```

<!--- KNIT example-agent-persistence-02.kt -->


The framework includes the following built-in providers:

- `InMemoryPersistenceStorageProvider`: stores checkpoints in memory (lost when the application restarts).
- `FilePersistenceStorageProvider`: persists checkpoints to the file system.
- `NoPersistenceStorageProvider`: a no-op implementation that does not store checkpoints. This is the default provider.

You can also implement custom storage providers by implementing the `PersistenceStorageProvider` interface.
For more information, see [Custom storage providers](#custom-storage-providers).

### Continuous persistence

Continuous persistence means that a checkpoint is automatically created after each node is run.
To activate continuous persistence, use the code below:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX 
} 
-->

```kotlin
install(Persistence) {
    enableAutomaticPersistence = true
}
```

<!--- KNIT example-agent-persistence-03.kt -->

When activated, the agent will automatically create a checkpoint after each node is executed,
allowing for fine-grained recovery.

### Rollback strategy

The rollback strategy determines which state will be restored when the agent rolls back to a checkpoint or starts a new run.
There are two available strategies:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(Persistence) {
    // Default strategy: restores the complete agent state including execution point
    rollbackStrategy = RollbackStrategy.Default
}
```

<!--- KNIT example-agent-persistence-04.kt -->

**`RollbackStrategy.Default`**

Restores the agent to the exact execution point (node in the strategy graph) where it stopped.
This means the entire context is restored, including:

- Message history
- Current node being executed
- Any other stateful data

This strategy is especially useful for building complex, fault-tolerant agents that need to resume
from the exact point where they left off.

**`RollbackStrategy.MessageHistoryOnly`**

Restores only the message history to the last saved state. The agent will always restart from the
first node in the strategy graph, but with the conversation history from previous runs.

This strategy is useful for building conversational agents or chatbots that need to maintain
context across multiple sessions but should always start their execution flow from the beginning.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(Persistence) {
    // MessageHistoryOnly strategy: preserves conversation history but restarts execution
    rollbackStrategy = RollbackStrategy.MessageHistoryOnly
}
```

<!--- KNIT example-agent-persistence-05.kt -->

## Basic usage

### Creating a checkpoint

To learn how to create a checkpoint at a specific point in your agent's execution, see the code sample below:

<!--- INCLUDE
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.snapshot.feature.persistence
import kotlin.reflect.typeOf

const val outputData = "some-output-data"
val outputType = typeOf<String>()
-->

```kotlin
suspend fun example(context: AIAgentContext) {
    // Create a checkpoint with the current state
    val checkpoint = context.persistence().createCheckpointAfterNode(
        agentContext = context,
        nodePath = context.executionInfo.path(),
        lastOutput = outputData,
        lastOutputType = outputType,
        checkpointId = context.runId,
        version = 0L
    )

    // The checkpoint ID can be stored for later use
    val checkpointId = checkpoint?.checkpointId
}
```

<!--- KNIT example-agent-persistence-06.kt -->

### Restoring from a checkpoint

To restore the state of an agent from a specific checkpoint, follow the code sample below:

<!--- INCLUDE
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.snapshot.feature.persistence
-->

```kotlin
suspend fun example(context: AIAgentContext, checkpointId: String) {
    // Roll back to a specific checkpoint
    context.persistence().rollbackToCheckpoint(checkpointId, context)

    // Or roll back to the latest checkpoint
    context.persistence().rollbackToLatestCheckpoint(context)
}
```

<!--- KNIT example-agent-persistence-07.kt -->

#### Rolling back all side-effects produced by tools

It's quite common for some tools to produce side-effects. Specifically, when you are running your agents on the backend, 
some of the tools would likely perform some database transactions. This makes it much harder for your agent to travel back in time.

Imagine, that you have a tool `createUser` that creates a new user in your database. And your agent has populated multiple tool calls overtime:
```
tool call: createUser "Alex"

->>>> checkpoint-1 <<<<-

tool call: createUser "Daniel"
tool call: createUser "Maria"
```

And now you would like to roll back to a checkpoint. Restoring the agent's state (including message history, and strategy graph node) alone would not
be sufficient to achieve the exact state of the world before the checkpoint. You should also restore the side-effects produced by your tool calls. In our example,
this would mean removing `Maria` and `Daniel` from the database.

With Koog Persistence you can achieve that by providing a `RollbackToolRegistry` to `Persistence` feature config:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.agents.snapshot.feature.RollbackToolRegistry

fun createUser(name: String) {}

fun removeUser(name: String) {}

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX 
} 
-->

```kotlin
install(Persistence) {
    enableAutomaticPersistence = true
    rollbackToolRegistry = RollbackToolRegistry {
        // For every `createUser` tool call there will be a `removeUser` invocation in the reverse order 
        // when rolling back to the desired execution point.
        // Note: `removeUser` tool should take the same exact arguments as `createUser`. 
        // It's the developer's responsibility to make sure that `removeUser` invocation rolls back all side-effects of `createUser`:
        registerRollback(::createUser, ::removeUser)
    }
}
```

<!--- KNIT example-agent-persistence-08.kt -->

### Using extension functions

The Agent Persistence feature provides convenient extension functions for working with checkpoints:

<!--- INCLUDE
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.example.exampleAgentPersistence06.outputData
import ai.koog.agents.example.exampleAgentPersistence06.outputType
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.agents.snapshot.feature.withPersistence
-->

```kotlin
suspend fun example(context: AIAgentContext) {
    // Access the checkpoint feature
    val checkpointFeature = context.persistence()

    // Or perform an action with the checkpoint feature
    context.withPersistence { ctx ->
        // 'this' is the checkpoint feature
        createCheckpointAfterNode(
            agentContext = ctx,
            nodePath = ctx.executionInfo.path(),
            lastOutput = outputData,
            lastOutputType = outputType,
            checkpointId = ctx.runId,
            version = 0L
        )
    }
}
```
<!--- KNIT example-agent-persistence-09.kt -->

## Advanced usage

### Custom storage providers

You can implement custom storage providers by implementing the `PersistenceStorageProvider` interface:

<!--- INCLUDE
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider

/*
// KNIT: Ignore example
-->
<!--- SUFFIX
*/
-->
```kotlin
class MyCustomStorageProvider<MyFilterType> : PersistenceStorageProvider<MyFilterType> {
    override suspend fun getCheckpoints(agentId: String, filter: MyFilterType?): List<AgentCheckpointData> {
        TODO("Not yet implemented")
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        TODO("Not yet implemented")
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: MyFilterType?): AgentCheckpointData? {
        TODO("Not yet implemented")
    }
}

```

<!--- KNIT example-agent-persistence-10.kt -->

To use your custom provider in the feature configuration, set it as the storage when configuring the Agent Persistence
feature in your agent.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

class MyCustomStorageProvider<MyFilterType> : PersistenceStorageProvider<MyFilterType> {
    override suspend fun getCheckpoints(agentId: String, filter: MyFilterType?): List<AgentCheckpointData> {
        TODO("Not yet implemented")
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        TODO("Not yet implemented")
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: MyFilterType?): AgentCheckpointData? {
        TODO("Not yet implemented")
    }
}

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
) {
-->
<!--- SUFFIX 
} 
-->

```kotlin
install(Persistence) {
    storage = MyCustomStorageProvider<Any>()
}
```

<!--- KNIT example-agent-persistence-11.kt -->

### Setting execution points

For advanced control, you can directly set the execution point of an agent:

<!--- INCLUDE
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.prompt.message.Message.User
import kotlinx.serialization.json.JsonPrimitive

val customInput = JsonPrimitive("custom-input")
val customOutput = JsonPrimitive("custom-output")
val customMessageHistory = emptyList<User>()
-->

```kotlin
fun example(context: AIAgentContext) {
    // You can set the execution point before some node and provide an input for it:
    context.persistence().setExecutionPoint(
        agentContext = context,
        nodePath = context.executionInfo.path(),
        messageHistory = customMessageHistory,
        input = customInput
    )

    // Or after some node and provide an output from the node:
    context.persistence().setExecutionPointAfterNode(
        agentContext = context,
        nodePath = context.executionInfo.path(),
        messageHistory = customMessageHistory,
        output = customOutput
    )
}

```

<!--- KNIT example-agent-persistence-12.kt -->

This allows for more fine-grained control over the agent's state beyond just restoring from checkpoints.
