# Agent events

Agent events are actions or interactions that occur as part of an agent workflow. They include:

- Agent lifecycle events
- Strategy events
- Node events
- LLM call events
- Tool call events

## Event handlers

You can monitor and respond to specific events during the agent workflow by using event handlers for logging, testing, debugging, and extending agent behavior.

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
which provides a way to register callbacks for different agent events, and can be installed as a feature in the agent configuration. For details, see [API reference](https://api.koog.
ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.local.features.eventHandler.feature/-event-handler/index.html).

To install the feature and configure event handlers for the agent, do the following:

```kotlin
{
    install(EventHandler){
        // Define event handlers here
        onToolCall = { stage, tool, toolArgs ->
            // Handle tool call event
        }

        onAgentFinished = { strategyName, result ->
            // Handle event triggered when the agent completes its execution
        }

        // Define other event handlers
    }
}
```

For more details about event handler configuration, see [API reference](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.local.features.eventHandler.feature/-event-handler-config/index.html).

You can also set up event handlers using the `handleEvents` extension function when creating an agent.
This function also installs the event handler feature and configures event handlers for the agent. Here is an example:

```kotlin
val agent = AIAgent(
    // Initialization options
){
    handleEvents {
        // Handle tool calls
        onToolCall = { stage, tool, toolArgs ->
            println("Tool called: ${tool.name} with args $toolArgs")
        }
        // Handle event triggered when the agent completes its execution
        onAgentFinished = { strategyName, result ->
            println("Agent finished with result: $result")
        }

        // Other event handlers
    }
}
```
