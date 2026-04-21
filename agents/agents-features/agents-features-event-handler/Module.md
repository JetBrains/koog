# Module agents-features-event-handler

Provides `EventHandler` feature that allows to listen and react to events in the agent execution.

### Overview

The agents-features-event-handler module provides a powerful event handling system for AI agents, allowing developers to hook into various events in the agent's lifecycle. This enables monitoring, logging, debugging, and extending agent behavior by reacting to specific events during execution.

Key features include:
- Monitoring agent lifecycle events (creation, start, completion, error)
- Tracking strategy execution (starting, completed)
- Observing node processing in the execution graph
- Intercepting LLM calls and responses
- Monitoring tool calls, validation errors, failures, and results

### Using in your project

To use the event handler feature in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-event-handler:$version")
}
```

Then, install the EventHandler feature when creating your agent:

```kotlin
val myAgent = AIAgent(
    // other configuration parameters
) {
    handleEvents {
        // Configure event handlers here
        onAgentStarting { eventContext ->
            println("Agent starting: ${eventContext.agent.id}")
        }

        onAgentCompleted { eventContext ->
            println("Agent finished with result: ${eventContext.result}")
        }
    }
}
```

### Using in unit tests

For testing agents with event handling capabilities, you can use the EventHandler to verify that specific events occur during test execution:

```kotlin
// Create a test agent with event handling
val testAgent = AIAgent(
    // other test configuration
) {
    // Track events for testing
    var toolCalled = false
    var agentFinished = false

    handleEvents {
        onToolCallStarting { eventContext ->
            toolCalled = true
            println("[DEBUG_LOG] Tool called: ${eventContext.toolName}")
        }

        onAgentCompleted { eventContext ->
            agentFinished = true
            println("[DEBUG_LOG] Agent finished with result: ${eventContext.result}")
        }
    }

    // Enable testing mode
    withTesting()
}

// After running the agent, assert that expected events occurred
assert(toolCalled) { "Expected tool to be called" }
assert(agentFinished) { "Expected agent to finish" }
```

### Example of usage

Here's an example of using the EventHandler to monitor and log various events during agent execution:

```kotlin
val agent = AIAgent(
    // other configuration parameters
) {
    handleEvents {
        // Log LLM interactions
        onLLMCallStarting { eventContext ->
            println("Sending prompt to LLM: ${eventContext.prompt}")
        }

        onLLMCallCompleted { eventContext ->
            println("Received ${eventContext.responses.size} response(s) from LLM")
        }

        // Monitor tool usage
        onToolCallStarting { eventContext ->
            println("Tool called: ${eventContext.toolName} with args: ${eventContext.toolArgs}")
        }

        onToolCallCompleted { eventContext ->
            println("Tool result: ${eventContext.result}")
        }

        onToolCallFailed { eventContext ->
            println("Tool failed: ${eventContext.throwable.message}")
        }

        // Track agent progress
        onStrategyStarting { eventContext ->
            println("Strategy started: ${eventContext.strategy.name}")
        }

        onStrategyCompleted { eventContext ->
            println("Strategy finished with result: ${eventContext.result}")
        }
    }
}
```

This example demonstrates how to monitor LLM interactions, track tool usage, and observe agent progress using the EventHandler feature.
