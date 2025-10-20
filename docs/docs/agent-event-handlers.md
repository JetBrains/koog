# Event handlers

You can monitor and respond to specific events during the agent workflow by using event handlers for logging, testing, debugging, and extending agent behavior.

## Feature overview

The EventHandler feature lets you hook into various agent events. It serves as an event delegation mechanism that:

- Manages the lifecycle of AI agent operations.
- Provides hooks for monitoring and responding to different stages of the workflow.
- Enables error handling and recovery.
- Facilitates tool invocation tracking and result processing.

<!--## Key components

The EventHandler entity consists of five main handler types:

- Initialization handler that executes at the initialization of an agent run
- Result handler that processes successful results from agent operations
- Error handler that handles exceptions and errors that occur during execution
- Tool call listener that notifies when a tool is about to be invoked
- Tool result listener that processes the results after a tool has been called-->


### Installation and configuration

The EventHandler feature integrates with the agent workflow through the `EventHandler` class,
which provides a way to register callbacks for different agent events, and can be installed as a feature in the agent configuration. For details, see [API reference](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.features.eventHandler.feature/-event-handler/index.html).

To install the feature and configure event handlers for the agent, do the following:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
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
handleEvents {
    // Handle tool calls
    onToolCallStarting { eventContext ->
        println("Tool called: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
    }
    // Handle event triggered when the agent completes its execution
    onAgentCompleted { eventContext ->
        println("Agent finished with result: ${eventContext.result}")
    }

    // Other event handlers
}
```
<!--- KNIT example-event-handlers-01.kt -->

For more details about event handler configuration, see [API reference](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.features.eventHandler.feature/-event-handler-config/index.html).

You can also set up event handlers using the `handleEvents` extension function when creating an agent.
This function also installs the event handler feature and configures event handlers for the agent. Here is an example:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
-->
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
){
    handleEvents {
        // Handle tool calls
        onToolCallStarting { eventContext ->
            println("Tool called: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
        }
        // Handle event triggered when the agent completes its execution
        onAgentCompleted { eventContext ->
            println("Agent finished with result: ${eventContext.result}")
        }

        // Other event handlers
    }
}
```
<!--- KNIT example-event-handlers-02.kt -->
