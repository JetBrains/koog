# Testing Agents in Koog

This guide explains how to effectively test AI agents in the Koog framework. It covers various testing approaches, from simple mocking of LLM responses to complex graph structure validation.

## Table of Contents
- [Introduction](#introduction)
- [Basic Testing Concepts](#basic-testing-concepts)
  - [Mocking LLM Responses](#mocking-llm-responses)
  - [Mocking Tool Calls](#mocking-tool-calls)
  - [Enabling Testing Mode](#enabling-testing-mode)
- [Advanced Testing](#advanced-testing)
  - [Testing Graph Structure](#testing-graph-structure)
  - [Testing Node Behavior](#testing-node-behavior)
  - [Testing Edge Connections](#testing-edge-connections)
- [Complete Testing Example](#complete-testing-example)
- [Testing DSL Reference](#testing-dsl-reference)

## Introduction

Testing AI agents can be challenging due to their complex behavior and dependencies on external systems like LLMs. The Koog framework provides specialized tools to make testing agents more predictable and reliable.

## Basic Testing Concepts

### Mocking LLM Responses

The most basic form of testing involves mocking LLM responses to ensure deterministic behavior. This is done using the `MockLLMBuilder` and related utilities.

```kotlin
// Create a mock LLM executor
val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
  // Mock a simple text response
  mockLLMAnswer("Hello!") onRequestContains "Hello"

  // Mock a default response
  mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
}
```

### Mocking Tool Calls

You can mock the LLM to call specific tools based on input patterns:

```kotlin
// Mock a tool call response
mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"

// Mock tool behavior - simplest form without lambda
mockTool(PositiveToneTool) alwaysReturns "The text has a positive tone."

// Using lambda when you need to perform extra actions
mockTool(NegativeToneTool) alwaysTells {
  // Perform some extra action
  println("Negative tone tool called")

  // Return the result
  "The text has a negative tone."
}

// Mock tool behavior based on specific arguments
mockTool(AnalyzeTool) returns AnalyzeTool.Result("Detailed analysis") onArguments AnalyzeTool.Args("analyze deeply")

// Mock tool behavior with conditional argument matching
mockTool(SearchTool) returns SearchTool.Result("Found results") onArgumentsMatching { args ->
  args.query.contains("important")
}
```

The examples above demonstrate different ways to mock tools, from simple to more complex:

1. `alwaysReturns` - Simplest form, directly returns a value without a lambda
2. `alwaysTells` - Uses a lambda when you need to perform additional actions
3. `returns...onArguments` - Returns specific results for exact argument matches
4. `returns...onArgumentsMatching` - Returns results based on custom argument conditions

### Enabling Testing Mode

To enable testing mode on an agent, use the `withTesting()` function within the AIAgent constructor block:

```kotlin
// Create the agent with testing enabled
AIAgent(
    promptExecutor = mockLLMApi,
    toolRegistry = toolRegistry,
    strategy = strategy,
    eventHandler = eventHandler,
    agentConfig = agentConfig,
) {
    // Enable testing mode
    withTesting()
}
```

## Advanced Testing

### Testing Graph Structure

Before diving into detailed node behavior and edge connections, it's important to verify the overall structure of your agent's graph. This includes checking that all required nodes exist and are properly connected in the expected subgraphs.

The `Testing` feature provides a comprehensive way to test your agent's graph structure. This approach is particularly valuable for complex agents with multiple subgraphs and interconnected nodes.

#### Basic Structure Testing

Start by validating the fundamental structure of your agent's graph:

```kotlin
AIAgent(
    // constructor arguments
    toolRegistry = toolRegistry,
    strategy = strategy,
    eventHandler = eventHandler,
    agentConfig = agentConfig,
    promptExecutor = mockLLMApi,
) {
    testGraph("test") {
        val firstSubgraph = assertSubgraphByName<String, String>("first")
        val secondSubgraph = assertSubgraphByName<String, String>("second")

        // Assert subgraph connections
        assertEdges {
            startNode() alwaysGoesTo firstSubgraph
            firstSubgraph alwaysGoesTo secondSubgraph
            secondSubgraph alwaysGoesTo finishNode()
        }

        // Verify the first subgraph
        verifySubgraph(firstSubgraph) {
            val start = startNode()
            val finish = finishNode()

            // Assert nodes by name
            val askLLM = assertNodeByName<String, Message.Response>("callLLM")
            val callTool = assertNodeByName<ToolCall.Signature, ToolCall.Result>("executeTool")

            // Assert node reachability
            assertReachable(start, askLLM)
            assertReachable(askLLM, callTool)
        }
    }
}
```


### Testing Node Behavior

Node behavior testing allows you to verify that nodes in your agent's graph produce the expected outputs for given inputs. This is crucial for ensuring that your agent's logic works correctly under different scenarios.

#### Basic Node Testing

Start with simple input/output validations for individual nodes:

```kotlin
assertNodes {
    // Test basic text responses
    askLLM withInput "Hello" outputs assistantMessage("Hello!")

    // Test tool call responses
    askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
}
```

The example above shows how to test that:
1. When the LLM node receives "Hello" as input, it responds with a simple text message
2. When it receives "Solve task", it responds with a tool call

#### Testing Tool Execution Nodes

You can also test nodes that execute tools:

```kotlin
assertNodes {
    // Test tool execution with specific arguments
    callTool withInput toolCallSignature(
        SolveTool,
        SolveTool.Args("solve")
    ) outputs toolResult(SolveTool, "solved")
}
```

This verifies that when the tool execution node receives a specific tool call signature, it produces the expected tool result.

#### Advanced Node Testing

For more complex scenarios, you can test nodes with structured inputs and outputs:

```kotlin
assertNodes {
    // Test with different inputs to the same node
    askLLM withInput "Simple query" outputs assistantMessage("Simple response")

    // Test with complex parameters
    askLLM withInput "Complex query with parameters" outputs toolCallMessage(
        AnalyzeTool, 
        AnalyzeTool.Args(query = "parameters", depth = 3)
    )
}
```

You can also test complex tool call scenarios with detailed result structures:

```kotlin
assertNodes {
    // Test complex tool call with structured result
    callTool withInput toolCallSignature(
        AnalyzeTool,
        AnalyzeTool.Args(query = "complex", depth = 5)
    ) outputs toolResult(AnalyzeTool, AnalyzeTool.Result(
        analysis = "Detailed analysis",
        confidence = 0.95,
        metadata = mapOf("source" to "database", "timestamp" to "2023-06-15")
    ))
}
```

These advanced tests help ensure that your nodes handle complex data structures correctly, which is essential for sophisticated agent behaviors.

### Testing Edge Connections

Edge connections testing allows you to verify that your agent's graph correctly routes outputs from one node to the appropriate next node. This ensures that your agent follows the intended workflow paths based on different outputs.

#### Basic Edge Testing

Start with simple edge connection tests:

```kotlin
assertEdges {
    // Test text message routing
    askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback

    // Test tool call routing
    askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
}
```

This example verifies that:
1. When the LLM node outputs a simple text message, the flow is directed to the `giveFeedback` node
2. When it outputs a tool call, the flow is directed to the `callTool` node

#### Testing Conditional Routing

You can test more complex routing logic based on the content of outputs:

```kotlin
assertEdges {
    // Different text responses can route to different nodes
    askLLM withOutput assistantMessage("Need more information") goesTo askForInfo
    askLLM withOutput assistantMessage("Ready to proceed") goesTo processRequest
}
```

#### Advanced Edge Testing

For sophisticated agents, you can test conditional routing based on structured data in tool results:

```kotlin
assertEdges {
    // Test routing based on tool result content
    callTool withOutput toolResult(
        AnalyzeTool, 
        AnalyzeTool.Result(analysis = "Needs more processing", confidence = 0.5)
    ) goesTo processResult
}
```

You can also test complex decision paths based on different result properties:

```kotlin
assertEdges {
    // Route to different nodes based on confidence level
    callTool withOutput toolResult(
        AnalyzeTool, 
        AnalyzeTool.Result(analysis = "Complete", confidence = 0.9)
    ) goesTo finish

    callTool withOutput toolResult(
        AnalyzeTool, 
        AnalyzeTool.Result(analysis = "Uncertain", confidence = 0.3)
    ) goesTo verifyResult
}
```

These advanced edge tests help ensure that your agent makes the correct decisions based on the content and structure of node outputs, which is essential for creating intelligent, context-aware workflows.

## Complete Testing Example

Here's a user story that demonstrates a complete testing scenario:

Imagine you're developing a tone analysis agent that analyzes the tone of text and provides feedback. The agent uses tools for detecting positive, negative, and neutral tones.

Here's how you might test this agent:

```kotlin
@Test
fun testToneAgent() = runTest {
    // Create a list to track tool calls
    var toolCalls = mutableListOf<String>()
    var result: String? = null

    // Create a tool registry
    val toolRegistry = ToolRegistry {
        // Special tool, required with this type of agent
        tool(SayToUser)

        with(ToneTools) {
            tools()
        }
    }

    // Create an event handler
    val eventHandler = EventHandler {
        onToolCallStarting { tool, args ->
            println("[DEBUG_LOG] Tool called: tool ${tool.name}, args $args")
            toolCalls.add(tool.name)
        }

        handleError {
            println("[DEBUG_LOG] An error occurred: ${it.message}\n${it.stackTraceToString()}")
            true
        }

        handleResult {
            println("[DEBUG_LOG] Result: $it")
            result = it
        }
    }

    val positiveText = "I love this product!"
    val negativeText = "Awful service, hate the app."
    val defaultText = "I don't know how to answer this question."

    val positiveResponse = "The text has a positive tone."
    val negativeResponse = "The text has a negative tone."
    val neutralResponse = "The text has a neutral tone."

    val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
        // Set up LLM responses for different input texts
        mockLLMToolCall(NeutralToneTool, ToneTool.Args(defaultText)) onRequestEquals defaultText
        mockLLMToolCall(PositiveToneTool, ToneTool.Args(positiveText)) onRequestEquals positiveText
        mockLLMToolCall(NegativeToneTool, ToneTool.Args(negativeText)) onRequestEquals negativeText

        // Mock the behaviour that LLM responds just tool responses once the tools returned smth.
        mockLLMAnswer(positiveResponse) onRequestContains positiveResponse
        mockLLMAnswer(negativeResponse) onRequestContains negativeResponse
        mockLLMAnswer(neutralResponse) onRequestContains neutralResponse

        mockLLMAnswer(defaultText).asDefaultResponse

        // Tool mocks:
        mockTool(PositiveToneTool) alwaysTells {
            toolCalls += "Positive tone tool called"
            positiveResponse
        }
        mockTool(NegativeToneTool) alwaysTells {
            toolCalls += "Negative tone tool called"
            negativeResponse
        }
        mockTool(NeutralToneTool) alwaysTells {
            toolCalls += "Neutral tone tool called"
            neutralResponse
        }
    }

    // Create strategy
    val strategy = toneStrategy("tone_analysis")

    // Create agent config
    val agentConfig = AIAgentConfig(
        prompt = prompt("test-agent") {
            system(
                """
                You are an question answering agent with access to the tone analysis tools.
                You need to answer 1 question with the best of your ability.
                Be as concise as possible in your answers.
                DO NOT ANSWER ANY QUESTIONS THAT ARE BESIDES PERFORMING TONE ANALYSIS!
                DO NOT HALLUCINATE!
            """.trimIndent()
            )
        },
        model = mockk<LLModel>(relaxed = true),
        maxAgentIterations = 10
    )

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

    // Test positive text
    agent.run(positiveText)
    assertEquals("The text has a positive tone.", result, "Positive tone result should match")
    assertEquals(1, toolCalls.size, "One tool is expected to be called")

    // Test negative text
    agent.run(negativeText)
    assertEquals("The text has a negative tone.", result, "Negative tone result should match")
    assertEquals(2, toolCalls.size, "Two tools are expected to be called")

    //Test neutral text
    agent.run(defaultText)
    assertEquals("The text has a neutral tone.", result, "Neutral tone result should match")
    assertEquals(3, toolCalls.size, "Three tools are expected to be called")
}
```

For more complex agents with multiple subgraphs, you can also test the graph structure:

```kotlin
@Test
fun testMultiSubgraphAgentStructure() = runTest {
    val strategy = strategy("test") {
        val firstSubgraph by subgraph(
            "first",
            tools = listOf(DummyTool, CreateTool, SolveTool)
        ) {
            val callLLM by nodeLLMRequest(allowToolCalls = false)
            val executeTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()
            val giveFeedback by node<String, String> { input ->
                llm.writeSession {
                    appendPrompt {
                        user("Call tools! Don't chat!")
                    }
                }
                input
            }

            edge(nodeStart forwardTo callLLM)
            edge(callLLM forwardTo executeTool onToolCall { true })
            edge(callLLM forwardTo giveFeedback onAssistantMessage { true })
            edge(giveFeedback forwardTo giveFeedback onAssistantMessage { true })
            edge(giveFeedback forwardTo executeTool onToolCall { true })
            edge(executeTool forwardTo nodeFinish transformed { it.content })
        }

        val secondSubgraph by subgraph<String, String>("second") {
            edge(nodeStart forwardTo nodeFinish)
        }

        edge(nodeStart forwardTo firstSubgraph)
        edge(firstSubgraph forwardTo secondSubgraph)
        edge(secondSubgraph forwardTo nodeFinish)
    }

    val toolRegistry = ToolRegistry {
        tool(DummyTool)
        tool(CreateTool)
        tool(SolveTool)
    }

    val mockLLMApi = getMockExecutor(toolRegistry) {
        mockLLMAnswer("Hello!") onRequestContains "Hello"
        mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
    }

    val basePrompt = prompt("test") {}

    AIAgent(
        toolRegistry = toolRegistry,
        strategy = strategy,
        eventHandler = EventHandler {},
        agentConfig = AIAgentConfig(prompt = basePrompt, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 100),
        promptExecutor = mockLLMApi,
    ) {
        testGraph("test") {
            val firstSubgraph = assertSubgraphByName<String, String>("first")
            val secondSubgraph = assertSubgraphByName<String, String>("second")

            assertEdges {
                startNode() alwaysGoesTo firstSubgraph
                firstSubgraph alwaysGoesTo secondSubgraph
                secondSubgraph alwaysGoesTo finishNode()
            }

            verifySubgraph(firstSubgraph) {
                val start = startNode()
                val finish = finishNode()

                val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                assertReachable(start, askLLM)
                assertReachable(askLLM, callTool)

                assertNodes {
                    askLLM withInput "Hello" outputs assistantMessage("Hello!")
                    askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))

                    callTool withInput toolCallMessage(
                        SolveTool,
                        SolveTool.Args("solve")
                    ) outputs toolResult(SolveTool, "solved")

                    callTool withInput toolCallMessage(
                        CreateTool,
                        CreateTool.Args("solve")
                    ) outputs toolResult(CreateTool, "created")
                }

                assertEdges {
                    askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback
                    askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
                }
            }
        }
    }
}
```

## Testing DSL Reference

### MockLLMBuilder DSL

- `mockLLMAnswer(response)` - Creates a mock LLM text response
  - `onRequestContains(pattern)` - Matches when the request contains the pattern
  - `onRequestEquals(pattern)` - Matches when the request equals the pattern
  - `onCondition(condition)` - Matches when the condition is true
  - `asDefaultResponse` - Sets as the default response

- `mockLLMToolCall(tool, args)` - Creates a mock LLM tool call response
  - `onRequestContains(pattern)` - Matches when the request contains the pattern
  - `onRequestEquals(pattern)` - Matches when the request equals the pattern

- `mockTool(tool)` - Creates a mock tool behavior
  - `alwaysReturns(response)` - Always returns the specified response
  - `alwaysDoes(action)` - Always performs the specified action
  - `alwaysTells(action)` - Always returns the string result of the action
  - `returns(result)` - Returns the result conditionally
    - `onArguments(args)` - When the arguments match exactly
    - `onArgumentsMatching(condition)` - When the arguments match the condition

### Testing feature DSL

- `testGraph(name) { }` - Tests the graph structure of an agent with the given name

- `assertSubgraphByName<I, O>(name)` - Asserts a subgraph exists with the given name and returns a reference to it
- `verifySubgraph(subgraph) { }` - Tests a specific subgraph
  - `startNode()` - Gets the start node reference
  - `finishNode()` - Gets the finish node reference
  - `assertNodeByName<I, O>(name)` - Asserts a node exists with the given name
  - `assertReachable(from, to)` - Asserts a path exists from one node to another

- `assertNodes { }` - Tests node behavior
  - `node withInput input outputs output` - Asserts node output for a given input

- `assertEdges { }` - Tests edge connections
  - `node withOutput output goesTo targetNode` - Asserts an edge exists from node to targetNode for the given output
  - `node alwaysGoesTo targetNode` - Asserts an unconditional edge exists from node to targetNode

### Utility Functions

- `toolCallMessage(tool, args)` - Creates a tool call message
- `toolResult(tool, result)` - Creates a tool result
- `withTesting(config)` - Installs the Testing feature with the given configuration

