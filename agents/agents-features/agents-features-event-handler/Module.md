# Module agents-features-event-handler

Provides `EventHandler` feature that allows to listen and react to events in the agent execution.

### Overview

The agents-features-event-handler module provides a powerful event handling system for AI agents, allowing developers to hook into various events in the agent's lifecycle. This enables monitoring, logging, debugging, and extending agent behavior by reacting to specific events during execution.

Key features include:
- Monitoring agent lifecycle events (creation, start, finish, error)
- Tracking strategy execution (start, finish)
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
val myAgent = AIAgents(
    // other configuration parameters
) {
    handleEvents {
        // Configure event handlers here
        onAgentStarted = { strategyName ->
            println("Agent started with strategy: $strategyName")
        }

        onAgentFinished = { strategyName, result ->
            println("Agent finished with result: $result")
        }
    }
}
```

### Using in unit tests

For testing agents with event handling capabilities, you can use the EventHandler to verify that specific events occur during test execution:

```kotlin
// Create a test agent with event handling
val testAgent = AIAgents(
    // other test configuration
) {
    // Track events for testing
    var toolCalled = false
    var agentFinished = false

    handleEvents {
        onToolCall = { stage, tool, toolArgs ->
            toolCalled = true
            println("[DEBUG_LOG] Tool called: ${tool.name}")
        }

        onAgentFinished = { strategyName, result ->
            agentFinished = true
            println("[DEBUG_LOG] Agent finished with result: $result")
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
val agent = AIAgents(
    // other configuration parameters
) {
    handleEvents {
        // Log LLM interactions
        onBeforeLLMCall = { prompt ->
            println("Sending prompt to LLM: ${prompt.toString().take(100)}...")
        }

        onAfterLLMCall = { response ->
            println("Received response from LLM: ${response.take(100)}...")
        }

        // Monitor tool usage
        onToolCall = { stage, tool, toolArgs ->
            println("Tool called: ${tool.name} with args: $toolArgs")
        }

        onToolCallResult = { stage, tool, toolArgs, result ->
            println("Tool result: $result")
        }

        onToolCallFailure = { stage, tool, toolArgs, throwable ->
            println("Tool failed: ${throwable.message}")
        }

        // Track agent progress
        onStrategyStarted = { strategy ->
            println("Strategy started: ${strategy.name}")
        }

        onStrategyFinished = { strategyName, result ->
            println("Strategy finished: $strategyName with result: $result")
        }
    }
}
```

This example demonstrates how to monitor LLM interactions, track tool usage, and observe agent progress using the EventHandler feature.
