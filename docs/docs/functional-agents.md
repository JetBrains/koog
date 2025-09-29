# Functional agents

Functional agents are lightweight AI agents that operate without building complex strategy graphs.
Instead, they use a simple loop controlled by a lambda function.
This loop handles user input, interacts with an LLM, and produces a final output.
The agent loop defines the main logic of the agent, which is repeated as needed based on user input and LLM output.

This page guides you through the steps necessary to create a minimal functional agent and extend it with tools.

!!! tip
    If you are new to Koog and want to create the simplest agent, start with [Single-run agents](single-run-agents.md).

## Prerequisites

Before you start, make sure that you have the following:

- A working Kotlin/JVM project with Gradle.
- Java 17+ installed.
- A valid API key from the LLM provider used to implement an AI agent. For a list of all available providers, refer to [Overview](index.md).
- (Optional) Ollama installed and running locally if you use this provider.

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Add dependencies

The `AIAgent` class is the main class for creating agents in Koog.
Include the following dependency in your build configuration to use the class functionality:

```
dependencies {
    implementation("ai.koog:koog-agents:VERSION")
}
```
For all available installation methods, see [Installation](index.md#installation).

## Create a minimal functional agent

To create a minimal functional agent, do the following:

1) Choose the input and output types that the agent handles and create a corresponding `AIAgent<Input, Output>` instance.
   In this guide, we use `AIAgent<String, String>`, which means the agent receives and returns `String`.
2) Provide the required parameters, including a system prompt, prompt executor, and LLM.
3) Define the agent loop with a lambda function.

Here is an example of a minimal functional agent that sends user text to a specified LLM and returns a single assistant message.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Create an AIAgent instance and provide a system prompt, prompt executor, and LLM
val mathAgent = AIAgent<String, String>(
    systemPrompt = "You are a precise math assistant.",
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2
    ) { input -> // Define the agent loop
        // Send the user input to the LLM
        val responses = requestLLMMultiple(input)
        // Extract and return the assistant message content from the response
        responses.single().asAssistantMessage().content
    }
// Run the agent with a user input and print the result
val result = mathAgent.run("What is 12 × 9?")
println(result)
```
<!--- KNIT example-functional-agent-01.kt -->

The agent can produce the following output:

```
The answer to 12 × 9 is 108.
```

## Add tools

In many cases, the functional agent needs to complete specific tasks, such as reading and writing data or calling APIs.
In Koog, you expose such capabilities as tools and let the LLM invoke them during the agent loop.

This chapter takes the minimal functional agent created above and demonstrates how to extend the agent logic using tools.

1) Create an annotation-based tool. For more details, see [Annotation-based tools](annotation-based-tools.md). 

<!--- INCLUDE
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
--> 
```kotlin
@LLMDescription("Simple multiplier")
class MathTools : ToolSet {
    @Tool
    @LLMDescription("Multiplies two numbers and returns the result")
    fun multiply(a: Int, b: Int): Int {
        val result = a * b
        return result
    }
}
```
<!--- KNIT example-functional-agent-02.kt -->

To learn more about available tools, refer to the [Tool overview](tool-overview.md).

2) Register the tool to make it available to the agent.

<!--- INCLUDE
import ai.koog.agents.example.exampleFunctionalAgent02.MathTools
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.core.tools.ToolRegistry
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val toolRegistry = ToolRegistry {
    tools(MathTools())
}
```
<!--- KNIT example-functional-agent-03.kt -->

3) Pass the tool registry to the agent to enable the LLM to request and use the available tools.
4) Extend the agent loop to identify tool calls, execute the requested tools, and return their results to the LLM for further processing.

<!--- INCLUDE
import ai.koog.agents.example.exampleFunctionalAgent02.MathTools
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.agents.core.agent.sendMultipleToolResults
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val toolRegistry = ToolRegistry {
            tools(MathTools())
        }
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val mathWithTools = AIAgent<String, String>(
    systemPrompt = "You are a precise math assistant. When multiplication is needed, use the multiplication tool.",
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    toolRegistry = toolRegistry
) { input -> // Define the agent loop extended with tool calls
    // Send the user input to the LLM
    var responses = requestLLMMultiple(input)

    // If the LLM requests tools
    while (responses.containsToolCalls()) {
        // Extract tool calls from the response
        val pendingCalls = extractToolCalls(responses)
        // Execute the tools and return the results
        val results = executeMultipleTools(pendingCalls)
        // Send the tool results back to the LLM. The LLM may call more tools or return a final output
        responses = sendMultipleToolResults(results)
    }

    // When no tool calls remain, extract and return the assistant message content from the response
    responses.single().asAssistantMessage().content
}

// Run the agent with a user input and print the result
val reply = mathWithTools.run("Please multiply 12.5 and 4, then add 10 to the result.")
println(reply)
```
<!--- KNIT example-functional-agent-04.kt -->

The agent can produce the following output:

```
Here is the step-by-step solution:

1. Multiply 12.5 and 4:
   12.5 × 4 = 50

2. Add 10 to the result:
   50 + 10 = 60
```

## What's next

- Learn how to return structured data using the [Structured output API](structured-output.md).
- Experiment with adding more [tools](tools-overview.md) to the agent.
- Improve observability with the [EventHandler](agent-events.md) feature.
- Learn how to handle long-running conversations with [History compression](history-compression.md).
