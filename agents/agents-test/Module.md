# Module agents-test

Comprehensive testing utilities for AI agents, providing mocking capabilities and validation tools for agent behavior.

### Overview

The agents-test module provides specialized tools and utilities for testing AI agents in a deterministic and reliable way. It enables developers to mock LLM responses, tool calls, and validate complex agent behaviors including graph structure, node behavior, and edge connections.

Key features include:
- Mocking LLM responses for deterministic testing
- Mocking tool calls and their results
- Testing graph structure and node connections
- Validating agent behavior in different scenarios
- DSL for expressive and readable tests

### Using in your project

To use the agents-test module in your project, add the following dependency:

```kotlin
dependencies {
    testImplementation("ai.koog.agents:agents-test:$version")
}
```

Then, you can create mock LLM executors and enable testing mode on your agents:

```kotlin
// Create a mock LLM executor
val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
    // Mock a simple text response
    mockLLMAnswer("Hello!") onRequestContains "Hello"

    // Mock a tool call
    mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"

    // Mock tool behavior
    mockTool(AnalyzeTool) returns AnalyzeTool.Result("Analysis complete") onArguments AnalyzeTool.Args("analyze")
}
```

### Using in unit tests

To enable testing mode on an agent, use the `withTesting()` function within the agent constructor block:

```kotlin
// Create the agent with testing enabled
val agent = AIAgent(
    promptExecutor = mockLLMApi,
    toolRegistry = toolRegistry,
    strategy = strategy,
    eventHandler = eventHandler,
    agentConfig = agentConfig,
) {
    withTesting()
}
```

This enables additional testing capabilities like graph structure validation and node behavior testing.

### Example of usage

```kotlin
@Test
fun testAgentBehavior() = runTest {
    // Create mock LLM executor
    val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
        mockLLMAnswer("Hello!") onRequestContains "Hello"
        mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"

        // Mock tool behavior
        mockTool(AnalyzeTool) alwaysReturns "Analysis complete"
    }

    // Create agent with testing enabled
    val agent = AIAgent(
        promptExecutor = mockLLMApi,
        toolRegistry = toolRegistry,
        strategy = strategy,
        eventHandler = eventHandler,
        agentConfig = agentConfig,
    ) {
        withTesting()

        // Test graph structure
        testGraph {
            assertStagesOrder("first", "second")

            stage("first") {
                val start = startNode()
                val askLLM = assertNodeByName<String, Message.Response>("callLLM")

                // Test node behavior
                assertNodes {
                    askLLM withInput "Hello" outputs assistantMessage("Hello!")
                }

                // Test edge connections
                assertEdges {
                    askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback
                }
            }
        }
    }

    // Run the agent and verify results
    val result = agent.run("Hello")
    assertEquals("Hello!", result)
}
```
