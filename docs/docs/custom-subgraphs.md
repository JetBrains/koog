## Creating and configuring subgraphs

The following sections provide code templates and common patterns in the creation of subgraphs for agentic workflows.

### Basic subgraph creation

Custom subgraphs are typically created using the following patterns:

* Subgraph with a specified tool selection strategy:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    typealias StrategyInput = Unit
    typealias StrategyOutput = Unit
    typealias Input = Unit
    typealias Output = Unit
    val strategy =
    -->
    ```kotlin
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
    <!--- KNIT example-custom-subgraphs-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.agent.entity.ToolSelectionStrategy;
    class exampleCustomSubgraphsJava01 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava01.java -->


* Subgraph with a specified list of tools (subset of tools from a defined tool registry):

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.ext.tool.SayToUser
    typealias StrategyInput = Unit
    typealias StrategyOutput = Unit
    typealias Input = Unit
    typealias Output = Unit
    val firstTool = SayToUser
    val secondTool = AskUser
    val strategy =
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.ext.tool.AskUser;
    import ai.koog.agents.ext.tool.SayToUser;
    import java.util.List;
    class exampleCustomSubgraphsJava02 {
        public static void main(String[] args) {
            var firstTool = SayToUser.INSTANCE;
            var secondTool = AskUser.INSTANCE;
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava02.java -->

For more information about parameters and parameter values, see the `subgraph` [API reference](api:agents-core::ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase.subgraph). For more
information about tools, see [Tools](tools/index.md).

The following code sample shows an actual implementation of a custom subgraph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.*
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.ext.tool.SayToUser
    val firstTool = SayToUser
    val secondTool = AskUser
    val strategy =
    -->
    ```kotlin
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
    <!--- KNIT example-custom-subgraphs-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentEdge;
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.ext.tool.AskUser;
    import ai.koog.agents.ext.tool.SayToUser;
    import ai.koog.prompt.message.Message;
    import ai.koog.prompt.message.MessagePart;
    import java.util.List;
    import java.util.stream.Collectors;
    class exampleCustomSubgraphsJava03 {
        public static void main(String[] args) {
            var firstTool = SayToUser.INSTANCE;
            var secondTool = AskUser.INSTANCE;
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava03.java -->

### Configuring tools in a subgraph

Tools can be configured for a subgraph in several ways:

* Directly in the subgraph definition:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.ext.tool.AskUser
    val strategy = strategy<String, String>("my-strategy") {
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.ext.tool.AskUser;
    import java.util.List;
    class exampleCustomSubgraphsJava04 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var mySubgraph = AIAgentSubgraph.builder()
        .limitedTools(List.of(AskUser.INSTANCE))
        .withInput(String.class)
        .withOutput(String.class)
        .define(subgraph -> {
            // Subgraph definition
        })
        .build();
    ```
    <!--- KNIT exampleCustomSubgraphsJava04.java -->

* From a tool registry:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.tools.ToolRegistry
    val toolRegistry = ToolRegistry.EMPTY
    val strategy = strategy<String, String>("my-strategy") {
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.tools.ToolRegistry;
    import java.util.List;
    class exampleCustomSubgraphsJava05 {
        public static void main(String[] args) {
            var toolRegistry = ToolRegistry.builder().build();
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var mySubgraph = AIAgentSubgraph.builder()
        .limitedTools(List.of(toolRegistry.getTool("AskUser")))
        .withInput(String.class)
        .withOutput(String.class)
        .define(subgraph -> {
            // Subgraph definition
        })
        .build();
    ```
    <!--- KNIT exampleCustomSubgraphsJava05.java -->

* Dynamically during execution:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    val strategy = strategy<String, String>("my-strategy") {
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import java.util.List;
    import java.util.stream.Collectors;
    class exampleCustomSubgraphsJava06 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava06.java -->

## Advanced subgraph techniques

### Multi-part strategies

Complex workflows can be broken down into multiple subgraphs, each handling a specific part of the process:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.ext.tool.SayToUser
    typealias A = Unit
    typealias B = Unit
    typealias C = Unit
    val firstTool = AskUser
    val secondTool = SayToUser
    val strategy =
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.ext.tool.AskUser;
    import ai.koog.agents.ext.tool.SayToUser;
    import java.util.List;
    class exampleCustomSubgraphsJava07 {
        public static void main(String[] args) {
            var firstTool = AskUser.INSTANCE;
            var secondTool = SayToUser.INSTANCE;
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava07.java -->

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

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeExecuteTools
    import ai.koog.agents.core.dsl.extension.nodeLLMRequest
    import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
    import ai.koog.agents.core.dsl.extension.onTextMessage
    import ai.koog.agents.core.dsl.extension.onToolCalls
    import ai.koog.agents.core.tools.SimpleTool
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.prompt.dsl.prompt
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    class WebSearchTool: SimpleTool<WebSearchTool.Args>(
        argsType = typeToken<Args>(),
        name = "web_search",
        description = "Search on the web"
    ) {
        @Serializable
        class Args(val query: String)
        override suspend fun execute(args: Args): String {
            return "Searching for ${args.query} on the web..."
        }
    }
    class DoAction: SimpleTool<DoAction.Args>(
        argsType = typeToken<Args>(),
        name = "do_action",
        description = "Do something"
    ) {
        @Serializable
        class Args(val action: String)
        override suspend fun execute(args: Args): String {
            return "Doing action..."
        }
    }
    class DoAnotherAction: SimpleTool<DoAnotherAction.Args>(
        argsType = typeToken<Args>(),
        name = "do_another_action",
        description = "Do something other"
    ) {
        @Serializable
        class Args(val action: String)
        override suspend fun execute(args: Args): String {
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
    <!--- KNIT example-custom-subgraphs-08.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentEdge;
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.tools.annotations.LLMDescription;
    import ai.koog.agents.core.tools.annotations.Tool;
    import ai.koog.agents.core.tools.reflect.ToolSet;
    import ai.koog.prompt.Prompt;
    import ai.koog.prompt.message.Message;
    import ai.koog.prompt.message.MessagePart;
    import java.util.Collections;
    import java.util.stream.Collectors;
    public class exampleCustomSubgraphsJava08 {
        static class WebSearchToolSet implements ToolSet {
            @Tool
            @LLMDescription("Search on the web")
            public String webSearch(@LLMDescription("The search query") String query) {
                return "Searching for " + query + " on the web...";
            }
        }
        static class ActionToolSet implements ToolSet {
            @Tool
            @LLMDescription("Do something")
            public String doAction(@LLMDescription("The action to perform") String action) {
                return "Doing action...";
            }
            @Tool
            @LLMDescription("Do something other")
            public String doAnotherAction(@LLMDescription("The action to perform") String action) {
                return "Doing another action...";
            }
        }
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomSubgraphsJava08.java -->
