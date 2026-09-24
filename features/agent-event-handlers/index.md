# Event handlers

You can monitor and respond to specific events during the agent workflow by using event handlers for logging, testing, debugging, and extending agent behavior.

## Feature overview

The EventHandler feature lets you hook into various agent events. It serves as an event delegation mechanism that:

- Manages the lifecycle of AI agent operations.
- Provides hooks for monitoring and responding to different stages of the workflow.
- Enables error handling and recovery.
- Facilitates tool invocation tracking and result processing.

### Installation and configuration

The EventHandler feature integrates with the agent workflow through the `EventHandler` class, which provides a way to register callbacks for different agent events, and can be installed as a feature in the agent configuration. For details, see [API-reference](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.features.eventHandler.feature/-event-handler/index.html).

To install the feature and configure event handlers for the agent, do the following:

```
handleEvents {
    // Handle tool calls
    onToolCallStarting { eventContext ->
        println("Tool called: ${eventContext.toolName} with args ${eventContext.toolArgs}")
    }
    // Handle event triggered when the agent completes its execution
    onAgentCompleted { eventContext ->
        println("Agent finished with result: ${eventContext.result}")
    }

    // Other event handlers
}
```

```
AIAgent<String, String> agent = AIAgent.builder()
    .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
    .llmModel(OllamaModels.Meta.LLAMA_3_2)
    .install(EventHandler.Feature, cfg -> {
        // Handle tool calls
        cfg.onToolCallStarting(ctx -> {
            System.out.println("Tool called: " + ctx.getToolName() + " with args " + ctx.getToolArgs());
        });
        // Handle event triggered when the agent completes its execution
        cfg.onAgentCompleted(ctx -> {
            System.out.println("Agent finished with result: " + ctx.getResult());
        });
    })
    .build();
```

For more details about event handler configuration, see [API-reference](https://api.koog.ai/agents/agents-features/agents-features-event-handler/ai.koog.agents.features.eventHandler.feature/-event-handler-config/index.html).

You can also set up event handlers using the `handleEvents` extension function when creating an agent. This function also installs the event handler feature and configures event handlers for the agent. Here is an example:

```
val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
){
    handleEvents {
        // Handle tool calls
        onToolCallStarting { eventContext ->
            println("Tool called: ${eventContext.toolName} with args ${eventContext.toolArgs}")
        }
        // Handle event triggered when the agent completes its execution
        onAgentCompleted { eventContext ->
            println("Agent finished with result: ${eventContext.result}")
        }

        // Other event handlers
    }
}
```

```
AIAgent<String, String> agent = AIAgent.builder()
    .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
    .llmModel(OllamaModels.Meta.LLAMA_3_2)
    .install(EventHandler.Feature, cfg -> {
        // Handle tool calls
        cfg.onToolCallStarting(ctx -> {
            System.out.println("Tool called: " + ctx.getToolName() + " with args " + ctx.getToolArgs());
        });
        // Handle event triggered when the agent completes its execution
        cfg.onAgentCompleted(ctx -> {
            System.out.println("Agent finished with result: " + ctx.getResult());
        });
    })
    .build();
```
