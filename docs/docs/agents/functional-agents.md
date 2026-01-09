# Functional agents

Functional agents are lightweight AI agents that operate without building complex strategy graphs.
Instead, you implement the agent logic as a lambda function that handles user input,
interacts with an LLM, calls tools if necessary, and produces the final output.

??? note "Prerequisites"

    --8<-- "getting-started-snippets.md:prerequisites"

    --8<-- "getting-started-snippets.md:dependencies"

    --8<-- "getting-started-snippets.md:api-key"

    Examples on this page assume that you are running Llama 3.2 locally via Ollama.

This page describes how to implement a functional strategy to prototype some custom logic for your agent.
For production needs, refactor your functional agent into a [graph-based agent](graph-based-agents.md)
by implementing a proper strategy graph.

## Create a minimal functional agent

To create a minimal functional agent,
use the same [`AIAgent`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent/index.html) interface
as for a [basic agent](basic-agents.md)
and pass an instance of [`AIAgentFunctionalStrategy`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent-functional-strategy/index.html) to it.
The most convenient way is to use the `functionalStrategy {...}` DSL method.

For example, here is how to define a functional strategy that expects a string input and returns a string output,
makes one LLM call, then returns the content of the assistant message from the response.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
-->
```kotlin
val strategy = functionalStrategy<String, String> { input ->
    val response = requestLLM(input)
    response.asAssistantMessage().content
}

val mathAgent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    strategy = strategy
)

fun main() = runBlocking {
    val result = mathAgent.run("What is 12 × 9?")
    println(result)
}
```
<!--- KNIT example-functional-agent-01.kt -->

The agent can produce the following output:

```text
The answer to 12 × 9 is 108.
```

## Make sequential LLM calls

You can extend the previous strategy to make multiple sequential LLM calls:

<!--- INCLUDE
import ai.koog.agents.core.agent.functionalStrategy
-->
```kotlin
val strategy = functionalStrategy<String, String> { input ->
    // The first LLM call produces an initial draft based on the user input
    val draft = requestLLM("Draft: $input").asAssistantMessage().content
    // The second LLM call improves the initial draft
    val improved = requestLLM("Improve and clarify.").asAssistantMessage().content
    // The final LLM call formats the improved text and returns the result
    requestLLM("Format the result as bold.").asAssistantMessage().content
}
```
<!--- KNIT example-functional-agent-02.kt -->

The agent can produce the following output:

```text
To calculate the product of 12 and 9, we multiply these two numbers together.

12 × 9 = **108**
```

## Add tools

In many cases, a functional agent needs to complete specific tasks,
such as reading and writing data, calling APIs, or performing other deterministic operations.
In Koog, you expose such capabilities as [tools](../tools-overview.md) and let the LLM decide when to call them.

Here is what you need to do:

1. Create an [annotation-based tool](../annotation-based-tools.md).
2. Add it to a tool registry and pass the registry to the agent.
3. Make sure the agent strategy can identify tool calls in LLM responses, execute the requested tools,
   send their results back to the LLM, and repeat the process until there are no tool calls remaining.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
-->
```kotlin
@LLMDescription("Tools for performing math operations")
class MathTools : ToolSet {
    @Tool
    @LLMDescription("Multiplies two numbers and returns the result")
    fun multiply(a: Int, b: Int): Int {
        // This is not necessary, but it helps to see the tool call in the console output
        println("Multiplying $a and $b...")
        return a * b
    }
}

val toolRegistry = ToolRegistry {
    tool(MathTools()::multiply)
}

val strategy = functionalStrategy<String, String> { input ->
    // Send the user input to the LLM
    var responses = requestLLMMultiple(input)

    // Only loop while the LLM requests tools
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

val mathAgentWithTools = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    toolRegistry = toolRegistry,
    strategy = strategy
)

fun main() = runBlocking {
    val result = mathAgentWithTools.run("Multiply 3 by 4, then multiply the result by 5.")
    println(result)
}
```
<!--- KNIT example-functional-agent-03.kt -->

The agent can produce the following output:

```text
Multiplying 3 and 4...
Multiplying 12 and 5...
The result of multiplying 3 by 4 is 12. Multiplying 12 by 5 gives us a final answer of 60.
```

## Next steps

- Learn how to create [graph-based agents](graph-based-agents.md)
