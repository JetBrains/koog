# Module agents-features-trace

Provides implementation of the `Tracing` feature for AI Agents

### Overview

The Tracing feature captures comprehensive data about agent execution, including:
- All LLM calls and their responses
- Prompts sent to LLMs
- Tool calls, arguments, and results
- Graph node visits and execution flow
- Agent lifecycle events (creation, start, finish, errors)
- Strategy execution events

This data is crucial for evaluation and analysis of the working agent, enabling:
- Debugging agent behavior
- Performance analysis and optimization
- Auditing and compliance
- Improving agent design and implementation

### Using in your project

To use the Tracing feature in your agent:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    strategy = strategy,
    // other parameters...
) {
    install(Tracing) {
        // Configure message processors to handle trace events
        addMessageProcessor(TraceFeatureMessageLogWriter(logger))
        
        val fileWriter = TraceFeatureMessageFileWriter(
            outputPath,
            { path: Path -> SystemFileSystem.sink(path).buffered() }
        )
        addMessageProcessor(fileWriter)

        // Optionally filter messages
        fileWriter.setMessageFilter { message -> 
            // Only trace LLM calls and tool calls
            message is BeforeLLMCallEvent || message is ToolCallEvent 
        }
    }
}
```

### Using in unit tests

For unit tests, you can use a simple log printer:

```kotlin
val agent = AIAgent(
    // parameters...
) {
    install(Tracing) {
        addMessageProcessor(object : FeatureMessageProcessor {
            override suspend fun onMessage(message: FeatureMessage) {
                println("[TEST TRACE] $message")
            }
        })
    }
}
```

### Example of usage

Here's an example of the logs produced by tracing:

```
AgentCreateEvent (strategy name: my-agent-strategy)
AgentStartedEvent (strategy name: my-agent-strategy)
StrategyStartEvent (strategy name: my-agent-strategy)
NodeExecutionStartEvent (node: definePrompt, input: user query)
NodeExecutionEndEvent (node: definePrompt, input: user query, output: processed query)
BeforeLLMCallEvent (prompt: Please analyze the following code...)
AfterLLMCallEvent (response: I've analyzed the code and found...)
ToolCallEvent (tool: readFile, tool args: {"path": "src/main.py"})
ToolCallResultEvent (tool: readFile, tool args: {"path": "src/main.py"}, result: "def main():...")
StrategyFinishedEvent (strategy name: my-agent-strategy, result: Success)
AgentFinishedEvent (strategy name: my-agent-strategy, result: Success)
```
