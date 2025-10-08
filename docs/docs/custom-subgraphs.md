## Creating and configuring subgraphs

The following sections provide code templates and common patterns in the creation of subgraphs for agentic workflows.

### Basic subgraph creation

Custom subgraphs are typically created using the following patterns:

* Subgraph with a specified tool selection strategy:
<!--- INCLUDE
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy

typealias StrategyInput = Unit
typealias StrategyOutput = Unit

typealias Input = Unit
typealias Output = Unit

val str = 
-->
```kotlin
strategy<StrategyInput, StrategyOutput>("strategy-name") {
    val subgraphIdentifier by subgraph<Input, Output>(
        name = "subgraph-name",
        toolSelectionStrategy = ToolSelectionStrategy.ALL
    ) {
        // Define nodes and edges for this subgraph
    }
}
```
<!--- KNIT example-custom-subgraphs-01.kt -->


* Subgraph with a specified list of tools (subset of tools from a defined tool registry):
<!--- INCLUDE
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser

typealias StrategyInput = Unit
typealias StrategyOutput = Unit

typealias Input = Unit
typealias Output = Unit

val firstTool = SayToUser
val secondTool = AskUser

val str = 
-->
```kotlin
strategy<StrategyInput, StrategyOutput>("strategy-name") {
   val subgraphIdentifier by subgraph<Input, Output>(
       name = "subgraph-name", 
       tools = listOf(firstTool, secondTool)
   ) {
        // Define nodes and edges for this subgraph
    }
}
```
<!--- KNIT example-custom-subgraphs-02.kt -->

For more information about parameters and parameter values, see the `subgraph` [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/-a-i-agent-subgraph-builder-base/subgraph.html). For more
information about tools, see [Tools](tools-overview.md).

The following code sample shows an actual implementation of a custom subgraph:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser


val firstTool = SayToUser
val secondTool = AskUser

val str = 
-->
```kotlin
strategy<String, String>("my-strategy") {
   val mySubgraph by subgraph<String, String>(
      tools = listOf(firstTool, secondTool)
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
<!--- KNIT example-custom-subgraphs-03.kt -->

### Configuring tools in a subgraph

Tools can be configured for a subgraph in several ways:

* Directly in the subgraph definition:
<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.tool.AskUser

val str = strategy<String, String>("my-strategy") {
-->
<!--- SUFFIX
}
-->
```kotlin
val mySubgraph by subgraph<String, String>(
   tools = listOf(AskUser)
 ) {
    // Subgraph definition
 }
```
<!--- KNIT example-custom-subgraphs-04.kt -->

* From a tool registry:
<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry

val toolRegistry = ToolRegistry.EMPTY
val str = strategy<String, String>("my-strategy") {
-->
<!--- SUFFIX
}
-->
```kotlin
val mySubgraph by subgraph<String, String>(
    tools = listOf(toolRegistry.getTool("AskUser"))
) {
    // Subgraph definition
}
```
<!--- KNIT example-custom-subgraphs-05.kt -->

* Dynamically during execution:
<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy

val str = strategy<String, String>("my-strategy") {
    val node by node<Unit, Unit>("node_name") {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Make a set of tools
this.llm.writeSession {
    tools = tools.filter { it.name in listOf("first_tool_name", "second_tool_name") }
}
```
<!--- KNIT example-custom-subgraphs-06.kt -->

## Advanced subgraph techniques

### Multi-part strategies

Complex workflows can be broken down into multiple subgraphs, each handling a specific part of the process:
<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser

typealias A = Unit
typealias B = Unit
typealias C = Unit

val firstTool = AskUser
val secondTool = SayToUser

val str =
-->
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
      tools = listOf(firstTool, secondTool)
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
<!--- KNIT example-custom-subgraphs-07.kt -->

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
<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class WebSearchTool: SimpleTool<WebSearchTool.Args>() {
    @Serializable
    class Args(val query: String)

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val description = "Search on the web"

    override suspend fun doExecute(args: Args): String {
        return "Searching for ${args.query} on the web..."
    }
}

class DoAction: SimpleTool<DoAction.Args>() {
    @Serializable
    class Args(val action: String)

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val description = "Do something"

    override suspend fun doExecute(args: Args): String {
        return "Doing action..."
    }
}

class DoAnotherAction: SimpleTool<DoAnotherAction.Args>() {
    @Serializable
    class Args(val action: String)

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val description = "Do something other"

    override suspend fun doExecute(args: Args): String {
        return "Doing another action..."
    }
}
-->
```kotlin
// Define the agent strategy
val strategy = strategy<String, String>("assistant") {
    // A subgraph that includes a tool call

    val researchSubgraph by subgraph<String, String>(
        "research_subgraph",
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
        "plan_subgraph",
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
        "execute_subgraph",
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
<!--- KNIT example-custom-subgraphs-08.kt -->
