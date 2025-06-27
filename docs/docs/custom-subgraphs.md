## Creating and configuring subgraphs

The following sections provide code templates and common patterns in the creation of subgraphs for agentic workflows.

### Basic subgraph creation

Custom subgraphs are typically created using the following patterns:

* Subgraph with a specified tool selection strategy:
```kotlin
strategy("strategy-name") {
   val subgraphIdentifier by subgraph<Input, Output>(
       name = "subgraph-name",
       toolSelectionStrategy = ToolSelectionStrategy.ALL
   ) {
        // Define nodes and edges for this subgraph
    }
}
```

* Subgraph with a specified list of tools (subset of tools from a defined tool registry):
```kotlin
strategy("strategy-name") {
   val subgraphIdentifier by subgraph<Input, Output>(
       name = "subgraph-name", 
       tools = listOf(firstToolName, secondToolName)
   ) {
        // Define nodes and edges for this subgraph
    }
}
```

For more information about parameters and parameter values, see the `subgraph` [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/-a-i-agent-subgraph-builder-base/subgraph.html). For more
information about tools, see [Tools](tools-overview.md).

The following code sample shows an actual implementation of a custom subgraph:

```kotlin
strategy("my-strategy") {
   val mySubgraph by subgraph<String, String>(
      tools = listOf(myTool1, myTool2)
   ) {
        // Define nodes and edges for this subgraph
        val sendInput by nodeLLMRequest()
        val executeToolCall by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo sendInput)
        edge(sendInput forwardTo executeToolCall onToolCall { true })
        edge(executeToolCall forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
    }
}
```

### Configuring tools in a subgraph

Tools can be configured for a subgraph in several ways:

* Directly in the subgraph definition:
```kotlin
val mySubgraph by subgraph<String, String>(
   tools = listOf(AskUser)
 ) {
    // Subgraph definition
 }
```

* From a tool registry:
```kotlin
val mySubgraph by subgraph<String, String>(
   tools = toolRegistry.getTool("AskUser")
) {
   // Subgraph definition
}
```

[//]: # (TODO: @maria.tigina to check whether this is possible)
* Dynamically during execution:
```kotlin
// Make a set of tools
val newTools = context.llm.writeSession {
    val selectedTools = this.requestLLMStructured<SelectedTools>(/*...*/)
    tools.filter { it.name in selectedTools.structure.tools.toSet() }
}

// Pass the tools to the context
val context = context.copyWithTools(newTools)
```

## Advanced subgraph techniques

### Multi-part strategies

Complex workflows can be broken down into multiple subgraphs, each handling a specific part of the process:

```kotlin
strategy("complex-workflow") {
   val inputProcessing by subgraph<String, A>(
   ) {
      // Process the initial input
   }

   val reasoning by subgraph<A, B>(
   ) {
      // Perform reasoning based on the processed input
   }

   val toolRun by subgraph<B, C>(
      // Optional subset of tools from the tool registry
      tools = listOf(tool1, too2)
   ) {
      // Run tools based on the reasoning
   }

   val responseGeneration by subgraph<C, String>(
   ) {
      // Generate a response based on the tool results
   }

   nodeStart then inputProcessing then reasoning then toolRun then responseGeneration then nodeFinish

}
```

## Best practices

When working with subgraphs, follow these best practices:

1. **Break complex workflows into subgraphs**: each subgraph should have a clear, focused responsibility.

2. **Pass only necessary context**: only pass the information that subsequent subgraphs need to function correctly.

3. **Document subgraph dependencies**: clearly document what each subgraph expects from previous subgraphs and what it provides to subsequent subgraphs.

4. **Test subgraphs in isolation**: ensure that each subgraph works correctly with various inputs before integrating it into a strategy.

5. **Consider token usage**: be mindful of token usage, especially when passing large histories between subgraphs.

## Troubleshooting

### Tools not available

If tools are not available in a subgraph:

- Check that the tools are correctly registered in the tool registry.

### Subgraphs not running in the defined and expected order

If subgraphs are not executing in the defined order:

- Check the strategy definition to ensure that subgraphs are listed in the correct order.
- Verify that each subgraph is correctly passing its output to the next subgraph.
- Ensure that your subgraph is connected with the rest of the subgraph and is reachable from the start (and finish). Be careful with conditional edges, so they cover all possible conditions to continue in order not to get blocked in a subgraph or node.

## Examples

The following example shows how subgraphs are used to create an agent strategy in a real-world scenario.
The code sample includes three defined subgraphs, `researchSubgraph`, `planSubgraph`, and `executeSubgraph`, where each of the subgraphs has a defined and distinct purpose within the assistant flow.

```kotlin
// Define the agent strategy
val strategy = strategy("assistant") {
    // A subgraph that includes a tool call
    val researchSubgraph by subgraph<String, String>(
        "name",
        tools = listOf(WebSearchTool())
    ) {
        val nodeCallLLM by nodeLLMRequest("call_llm")
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    }
    
    val planSubgraph by subgraph(
        "research_subgraph",
        tools = listOf()
    ) {
        val nodeUpdatePrompt by node<String, Unit> { research ->
            llm.writeSession {
                rewritePrompt {
                    prompt("research_prompt") {
                        system(
                            "You are given a problem and some research on how it can be solved." +
                                    "Make step by step a plan on how to solve given task."
                        )
                        user("Research: $research")
                    }
                }
            }
        }
        val nodeCallLLM by nodeLLMRequest("call_llm")

        edge(nodeStart forwardTo nodeUpdatePrompt)
        edge(nodeUpdatePrompt forwardTo nodeCallLLM transformed { "Task: $agentInput" })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    }

    val executeSubgraph by subgraph<String, String>(
        "research_subgraph",
        tools = listOf(DoAction(), DoAnotherAction()),
    ) {
        val nodeUpdatePrompt by node<String, Unit> { plan ->
            llm.writeSession {
                rewritePrompt {
                    prompt("execute_prompt") {
                        system(
                            "You are given a task and detailed plan how to execute it." +
                                    "Perform execution by calling relevant tools."
                        )
                        user("Execute: $plan")
                        user("Plan: $plan")
                    }
                }
            }
        }
        val nodeCallLLM by nodeLLMRequest("call_llm")
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo nodeUpdatePrompt)
        edge(nodeUpdatePrompt forwardTo nodeCallLLM transformed { "Task: $agentInput" })
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    }

    nodeStart then researchSubgraph then planSubgraph then executeSubgraph then nodeFinish
}
```
