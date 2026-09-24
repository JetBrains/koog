## Creating and configuring subgraphs

The following sections provide code templates and common patterns in the creation of subgraphs for agentic workflows.

### Basic subgraph creation

Custom subgraphs are typically created using the following patterns:

- Subgraph with a specified tool selection strategy:

```
strategy<StrategyInput, StrategyOutput>("strategy-name") {
    val subgraphIdentifier by subgraph<Input, Output>(
        name = "subgraph-name",
        toolSelectionStrategy = ToolSelectionStrategy.ALL
    ) {
        // Define nodes and edges for this subgraph
    }

    nodeStart then subgraphIdentifier then nodeFinish
}
```

```
var strategyBuilder = AIAgentGraphStrategy.builder("strategy-name")
    .withInput(String.class)
    .withOutput(String.class);

var subgraphIdentifier = AIAgentSubgraph.builder("subgraph-name")
    .withToolSelectionStrategy(ToolSelectionStrategy.ALL.INSTANCE)
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Define nodes and edges for this subgraph
    })
    .build();

var strategy = strategyBuilder
    .edge(strategyBuilder.nodeStart, subgraphIdentifier)
    .edge(subgraphIdentifier, strategyBuilder.nodeFinish)
    .build();
```

- Subgraph with a specified list of tools (subset of tools from a defined tool registry):

```
strategy<StrategyInput, StrategyOutput>("strategy-name") {
   val subgraphIdentifier by subgraph<Input, Output>(
       name = "subgraph-name",
       tools = listOf(firstTool, secondTool)
   ) {
        // Define nodes and edges for this subgraph
    }
}
```

```
var strategyBuilder = AIAgentGraphStrategy.builder("strategy-name")
    .withInput(String.class)
    .withOutput(String.class);

var subgraphIdentifier = AIAgentSubgraph.builder("subgraph-name")
    .limitedTools(List.of(firstTool, secondTool))
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Define nodes and edges for this subgraph
    })
    .build();

var strategy = strategyBuilder
    .edge(strategyBuilder.nodeStart, subgraphIdentifier)
    .edge(subgraphIdentifier, strategyBuilder.nodeFinish)
    .build();
```

For more information about parameters and parameter values, see the `subgraph` [API-reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/-a-i-agent-subgraph-builder-base/subgraph.html). For more information about tools, see [Tools](../tools/).

The following code sample shows an actual implementation of a custom subgraph:

```
strategy<String, String>("my-strategy") {
   val mySubgraph by subgraph<String, String>(
      tools = listOf(firstTool, secondTool)
   ) {
        // Define nodes and edges for this subgraph
        val sendInput by nodeLLMRequest()
        val executeToolCall by nodeExecuteTools()
        val sendToolResult by nodeLLMSendToolResults()

        edge(nodeStart forwardTo sendInput)
        edge(sendInput forwardTo executeToolCall onToolCalls { true })
        edge(executeToolCall forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish onTextMessage { true })
    }
}
```

```
var strategyBuilder = AIAgentGraphStrategy.builder("my-strategy")
        .withInput(String.class)
        .withOutput(String.class);

var sendInput = AIAgentNode.llmRequest(null);
var executeToolCall = AIAgentNode.executeTools(null);
var sendToolResult = AIAgentNode.llmSendToolResults(null);

var mySubgraph = AIAgentSubgraph.builder()
    .limitedTools(List.of(firstTool, secondTool))
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Define nodes and edges for this subgraph
        subgraph
            .edge(AIAgentEdge.builder()
                .from(subgraph.nodeStart)
                .to(sendInput)
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(sendInput)
                .to(executeToolCall)
                .onToolCalls()
                .build()
            )
            .edge(executeToolCall, sendToolResult)
            .edge(AIAgentEdge.builder()
                .from(sendToolResult)
                .to(subgraph.nodeFinish)
                .onTextMessage()
                .build()
            )
            .build();

    })
    .build();

var strategy = strategyBuilder
    .edge(strategyBuilder.nodeStart, mySubgraph)
    .edge(mySubgraph, strategyBuilder.nodeFinish)
    .build();
```

### Configuring tools in a subgraph

Tools can be configured for a subgraph in several ways:

- Directly in the subgraph definition:

```
val mySubgraph by subgraph<String, String>(
   tools = listOf(AskUser)
 ) {
    // Subgraph definition
 }
```

```
var mySubgraph = AIAgentSubgraph.builder()
    .limitedTools(List.of(AskUser.INSTANCE))
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Subgraph definition
    })
    .build();
```

- From a tool registry:

```
val mySubgraph by subgraph<String, String>(
    tools = listOf(toolRegistry.getTool("AskUser"))
) {
    // Subgraph definition
}
```

```
var mySubgraph = AIAgentSubgraph.builder()
    .limitedTools(List.of(toolRegistry.getTool("AskUser")))
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Subgraph definition
    })
    .build();
```

- Dynamically during execution:

```
// Make a set of tools
this.llm.writeSession {
    tools = tools.filter { it.name in listOf("first_tool_name", "second_tool_name") }
}
```

```
var node = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        // Make a set of tools
        ctx.getLlm().writeSession(session -> {
            session.setTools(session.getTools().stream()
                .filter(t -> List.of("first_tool_name", "second_tool_name").contains(t.getName()))
                .collect(Collectors.toList()));
            return null;
        });
        return input;
    })
    .build();
```

## Advanced subgraph techniques

### Multi-part strategies

Complex workflows can be broken down into multiple subgraphs, each handling a specific part of the process:

```
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

```
var strategyBuilder = AIAgentGraphStrategy.builder("complex-workflow")
        .withInput(String.class)
        .withOutput(String.class);

var inputProcessing = AIAgentSubgraph.builder()
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Process the initial input
    })
    .build();

var reasoning = AIAgentSubgraph.builder()
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Perform reasoning based on the processed input
    })
    .build();

var toolRun = AIAgentSubgraph.builder()
    // Optional subset of tools from the tool registry
    .limitedTools(List.of(firstTool, secondTool))
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Run tools based on the reasoning
    })
    .build();

var responseGeneration = AIAgentSubgraph.builder()
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        // Generate a response based on the tool results
    })
    .build();

var strategy = strategyBuilder
    .edge(strategyBuilder.nodeStart, inputProcessing)
    .edge(inputProcessing, reasoning)
    .edge(reasoning, toolRun)
    .edge(toolRun, responseGeneration)
    .edge(responseGeneration, strategyBuilder.nodeFinish)
    .build();
```

## Best practices

When working with subgraphs, follow these best practices:

1. **Break complex workflows into subgraphs**: each subgraph should have a clear, focused responsibility.
1. **Pass only necessary context**: only pass the information that subsequent subgraphs need to function correctly.
1. **Document subgraph dependencies**: clearly document what each subgraph expects from previous subgraphs and what it provides to subsequent subgraphs.
1. **Test subgraphs in isolation**: ensure that each subgraph works correctly with various inputs before integrating it into a strategy.
1. **Consider token usage**: be mindful of token usage, especially when passing large histories between subgraphs.

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

The following example shows how subgraphs are used to create an agent strategy in a real-world scenario. The code sample includes three defined subgraphs, `researchSubgraph`, `planSubgraph`, and `executeSubgraph`, where each of the subgraphs has a defined and distinct purpose within the assistant flow.

```
// Define the agent strategy
val strategy = strategy<String, String>("assistant") {

    // A subgraph that includes a tool call
    val researchSubgraph by subgraph<String, String>(
        "research_subgraph",
        tools = listOf(WebSearchTool())
    ) {
        val nodeCallLLM by nodeLLMRequest("call_llm")
        val nodeExecuteTool by nodeExecuteTools()
        val nodeSendToolResult by nodeLLMSendToolResults()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
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
        edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
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
        val nodeExecuteTool by nodeExecuteTools()
        val nodeSendToolResult by nodeLLMSendToolResults()

        edge(nodeStart forwardTo nodeUpdatePrompt)
        edge(nodeUpdatePrompt forwardTo nodeCallLLM transformed { "Task: $agentInput" })
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
    }

    nodeStart then researchSubgraph then planSubgraph then executeSubgraph then nodeFinish
}
```

```
// Define the agent strategy
var strategyBuilder = AIAgentGraphStrategy.builder("assistant")
    .withInput(String.class)
    .withOutput(String.class);

// A subgraph that includes a tool call
var nodeCallLLM = AIAgentNode.llmRequest(null);
var nodeExecuteTool = AIAgentNode.executeTools(null);
var nodeSendToolResult = AIAgentNode.llmSendToolResults(null);

var researchSubgraph = AIAgentSubgraph.builder("research_subgraph")
    .limitedTools(new WebSearchToolSet())
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        subgraph
            .edge(AIAgentEdge.builder()
                .from(subgraph.nodeStart)
                .to(nodeCallLLM)
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(nodeCallLLM)
                .to(nodeExecuteTool)
                .onToolCalls()
                .build()
            )
            .edge(nodeExecuteTool, nodeSendToolResult)
            .edge(AIAgentEdge.builder()
                .from(nodeSendToolResult)
                .to(nodeExecuteTool)
                .onToolCalls()
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(nodeCallLLM)
                .to(subgraph.nodeFinish)
                .onTextMessage()
                .build()
            )
            .build();
    })
    .build();

var nodeUpdatePrompt = AIAgentNode.builder()
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((research, ctx) -> {
        ctx.getLlm().writeSession(session -> {
            session.setPrompt(Prompt.builder("research_prompt")
                .system(
                    "You are given a problem and some research on how it can be solved." +
                    "Make step by step a plan on how to solve given task."
                )
                .user("Research: " + research)
                .build());
            return null;
        });
        return "Task: " + ctx.getAgentInput();
    })
    .build();
var nodeCallLLMPlan = AIAgentNode.llmRequest(null);

var planSubgraph = AIAgentSubgraph.builder("plan_subgraph")
    .limitedTools(Collections.emptyList())
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        subgraph
            .edge(subgraph.nodeStart, nodeUpdatePrompt)
            .edge(AIAgentEdge.builder()
                .from(nodeUpdatePrompt)
                .to(nodeCallLLMPlan)
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(nodeCallLLMPlan)
                .to(subgraph.nodeFinish)
                .onTextMessage()
                .build()
            )
            .build();
    })
    .build();

var nodeUpdatePromptExecute = AIAgentNode.builder()
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((plan, ctx) -> {
        ctx.getLlm().writeSession(session -> {
            session.setPrompt(Prompt.builder("execute_prompt")
                .system(
                    "You are given a task and detailed plan how to execute it." +
                    "Perform execution by calling relevant tools."
                )
                .user("Execute: " + plan)
                .user("Plan: " + plan)
                .build());
            return null;
        });
        return "Task: " + ctx.getAgentInput();
    })
    .build();

var nodeCallLLMExecute = AIAgentNode.llmRequest(null);
var nodeExecuteToolExecute = AIAgentNode.executeTools(null);
var nodeSendToolResultExecute = AIAgentNode.llmSendToolResults(null);

var executeSubgraph = AIAgentSubgraph.builder("execute_subgraph")
    .limitedTools(new ActionToolSet())
    .withInput(String.class)
    .withOutput(String.class)
    .define(subgraph -> {
        subgraph
            .edge(subgraph.nodeStart, nodeUpdatePromptExecute)
            .edge(AIAgentEdge.builder()
                .from(nodeUpdatePromptExecute)
                .to(nodeCallLLMExecute)
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(nodeCallLLMExecute)
                .to(nodeExecuteToolExecute)
                .onToolCalls()
                .build()
            )
            .edge(nodeExecuteToolExecute, nodeSendToolResultExecute)
            .edge(AIAgentEdge.builder()
                .from(nodeSendToolResultExecute)
                .to(nodeExecuteToolExecute)
                .onToolCalls()
                .build()
            )
            .edge(AIAgentEdge.builder()
                .from(nodeCallLLMExecute)
                .to(subgraph.nodeFinish)
                .onIsInstance(Message.Assistant.class)
                .onTextMessage()
                .build()
            )
            .build();
    })
    .build();

var strategy = strategyBuilder
    .edge(strategyBuilder.nodeStart, researchSubgraph)
    .edge(researchSubgraph, planSubgraph)
    .edge(planSubgraph, executeSubgraph)
    .edge(executeSubgraph, strategyBuilder.nodeFinish)
    .build();
```
