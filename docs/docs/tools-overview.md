# Overview

 Agents use tools to perform specific tasks or access external systems.

## Tool workflow

The Koog framework offers the following workflow for working with tools:

1. Create a custom tool or use one of the built-in tools.
2. Add the tool to a tool registry.
3. Pass the tool registry to an agent.
4. Use the tool with the agent.

### Available tool types

There are three types of tools in the Koog framework:

- Built-in tools that provide functionality for agent-user interaction and conversation management. For details, see [Built-in tools](built-in-tools.md).
- Annotation-based custom tools that let you expose functions as tools to LLMs. For details, see [Annotation-based tools](annotation-based-tools.md).
- Custom tools that let you control tool parameters, metadata, execution logic, and how it is registered and invoked. For details, see [Class-based
  tools](class-based-tools.md).

### Tool registry

Before you can use a tool in an agent, you must add it to a tool registry.
The tool registry manages all tools available to the agent.

The key features of the tool registry:

- Organizes tools.
- Supports merging of multiple tool registries.
- Provides methods to retrieve tools by name or type.

To learn more, see [ToolRegistry](api:agents-tools::ai.koog.agents.core.tools.ToolRegistry).

Here is an example of how to create the tool registry and add the tool to it:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.core.tools.annotations.Tool
    import ai.koog.agents.core.tools.reflect.ToolSet
    import ai.koog.agents.core.tools.reflect.tools
    class MyToolSet : ToolSet {
        @Tool
        fun myTool(): String {
            // Tool implementation
            return "Result"
        }
    }
    val myTool = MyToolSet()
    -->
    ```kotlin
    val toolRegistry = ToolRegistry {
        tools(myTool)
    }
    ```
    <!--- KNIT example-tools-overview-01.kt -->

=== "Java"

    ```java
    // Create an instance of your ToolSet
    MyToolSet myTool = new MyToolSet();

    // Build the ToolRegistry and register tools from the ToolSet
    ToolRegistry toolRegistry = ToolRegistry.builder()
        .tools(myTool)
        .build();
    ```

To merge multiple tool registries, do the following:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.core.tools.annotations.Tool
    import ai.koog.agents.core.tools.reflect.ToolSet
    import ai.koog.agents.core.tools.reflect.tools
    class FirstToolSet : ToolSet {
        @Tool
        fun firstSampleTool(): String {
            // Tool implementation
            return "First result"
        }
    }
    class SecondToolSet : ToolSet {
        @Tool
        fun secondSampleTool(): String {
            // Tool implementation
            return "Second result"
        }
    }
    val firstSampleTool = FirstToolSet()
    val secondSampleTool = SecondToolSet()
    -->
    ```kotlin
    val firstToolRegistry = ToolRegistry {
        tools(firstSampleTool)
    }
    
    val secondToolRegistry = ToolRegistry {
        tools(secondSampleTool)
    }
    
    val newRegistry = firstToolRegistry + secondToolRegistry
    ```
    <!--- KNIT example-tools-overview-02.kt -->

=== "Java"
    
    ```java
    // Create instances of your ToolSets
    FirstToolSet firstSampleTool = new FirstToolSet();
    SecondToolSet secondSampleTool = new SecondToolSet();

    // Build separate tool registries
    ToolRegistry firstToolRegistry = ToolRegistry.builder()
        .tools(firstSampleTool)
        .build();

    ToolRegistry secondToolRegistry = ToolRegistry.builder()
        .tools(secondSampleTool)
        .build();

    ToolRegistry newRegistry = firstToolRegistry.plus(secondToolRegistry);
    ```

### Passing tools to an agent

To enable an agent to use a tool, you need to provide a tool registry that contains this tool as an argument when creating the agent:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.example.exampleToolsOverview01.toolRegistry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    -->
    ```kotlin
    // Agent initialization
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
        systemPrompt = "You are a helpful assistant with strong mathematical skills.",
        llmModel = OpenAIModels.Chat.GPT4o,
        // Pass your tool registry to the agent
        toolRegistry = toolRegistry
    )
    ```
    <!--- KNIT example-tools-overview-03.kt -->

=== "Java"

    ```java
    AIAgent<String, String> agent = AIAgent.builder()
        .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
        .systemPrompt("You are a helpful assistant with strong mathematical skills.")
        .llmModel(OpenAIModels.Chat.GPT4o)
        .toolRegistry(ToolRegistry.builder()
            .tools(secondSampleTool)
            .build()
        )
        .build();
    ```

### Calling tools

There are several ways to call tools within your agent code. The recommended approach is to use the provided methods
in the agent context rather than calling tools directly, as this ensures proper handling of tool operation within the
agent environment.

!!! tip
    Ensure you have implemented proper [error handling](agent-event-handlers.md) in your tools to prevent agent failure.

The tools are called within a specific session context represented by `AIAgentLLMWriteSession`.
It provides several methods for calling tools so that you can:

- Call a tool with the given arguments.
- Call a tool by its name and the given arguments.
- Call a tool by the provided tool class and arguments.
- Call a tool of the specified type with the given arguments.
- Call a tool that returns a raw string result.

For more details, see [API reference](api:agents-core::ai.koog.agents.core.agent.session.AIAgentLLMWriteSession).

#### Parallel tool calls

You can also call tools in parallel using the `toParallelToolCallsRaw` extension. For example:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.tools.SimpleTool
    import kotlinx.coroutines.flow.collect
    import kotlinx.coroutines.flow.flow
    import kotlinx.serialization.KSerializer
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    @Serializable
    data class Book(
        val title: String,
        val author: String,
        val description: String
    )
    
    class BookTool() : SimpleTool<Book>(
        argsSerializer = Book.serializer(),
        name = NAME,
        description = "A tool to parse book information from Markdown"
    ) {
        companion object {
            const val NAME = "book"
        }
    
        override suspend fun execute(args: Book): String {
            println("${args.title} by ${args.author}:\n ${args.description}")
            return "Done"
        }
    }
    
    val strategy = strategy<Unit, Unit>("strategy-name") {
    
        /*...*/
    
        val myNode by node<Unit, Unit> { _ ->
            llm.writeSession {
                flow {
                    emit(Book("Book 1", "Author 1", "Description 1"))
                }.toParallelToolCallsRaw(BookTool::class).collect()
            }
        }
    }
    
    ```
    <!--- KNIT example-tools-overview-04.kt -->

=== "Java"

    ```java
    // Java sketch for parallel tool calls
    // FAILED: The Kotlin API uses kotlinx.coroutines Flow, inline reified generics, and KFunction references
    // (toParallelToolCallsRaw(BookTool::class)), which are not directly invokable from Java without
    // specific Java-interop wrappers. In particular:
    //  - Flow-based extensions with reified type params have no Java equivalents (require Class<T> params or wrappers).
    //  - Referencing Kotlin function/class literals for tools (KFunction, ::class) is not straightforward from Java.
    // If a Java-friendly API is added (e.g., session.emitParallelToolCallsRaw(flow, BookTool.class, concurrency)),
    // the following structure could be used:
    //
    // AIAgentFunctionalContext ctx = ...;
    // AIAgentLLMWriteSession session = ctx.getLlm().writeSession();
    // Flow<Book> input = ...; // stream of Book args
    // Flow<String> results = /* session.emitParallelToolCallsRaw(input, BookTool.class, 16) */;
    // results.collect(/* consumer */);
    ```

#### Calling tools from nodes

When building agent workflows with nodes, you can use special nodes to call tools:

* **nodeExecuteTool**: calls a single tool call and returns its result. For details, see [API reference](api:agents-core::ai.koog.agents.core.dsl.extension.nodeExecuteTool).

* **nodeExecuteSingleTool** that calls a specific tool with the provided arguments. For details, see [API reference](api:agents-core::ai.koog.agents.core.dsl.extension.nodeExecuteSingleTool).

* **nodeExecuteMultipleTools** that performs multiple tool calls and returns their results. For details, see [API reference](api:agents-core::ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools).

* **nodeLLMSendToolResult** that sends a tool result to the LLM and gets a response. For details, see [API reference](api:agents-core::ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult).

* **nodeLLMSendMultipleToolResults** that sends multiple tool results to the LLM. For details, see [API reference](api:agents-core::ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults).

## Using agents as tools

The framework provides the capability to convert any AI agent into a tool that can be used by other agents. 
This powerful feature enables you to create hierarchical agent architectures where specialized agents can be called as tools by higher-level orchestrating agents.

### Converting agents to tools

To convert an agent into a tool, use the `AIAgentService` and the `createAgentTool()` extension function:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.AIAgentService
    import ai.koog.agents.core.agent.createAgentTool
    import ai.koog.agents.core.tools.ToolParameterDescriptor
    import ai.koog.agents.core.tools.ToolParameterType
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    const val apiKey = ""
    val analysisToolRegistry = ToolRegistry {}
    -->
    ```kotlin
    // Create a specialized agent service, responsible for creating financial analysis agents.
    val analysisAgentService = AIAgentService(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a financial analysis specialist.",
        toolRegistry = analysisToolRegistry
    )
    
    // Create a tool that would run financial analysis agent once called.
    val analysisAgentTool = analysisAgentService.createAgentTool(
        agentName = "analyzeTransactions",
        agentDescription = "Performs financial transaction analysis",
        inputDescription = "Transaction analysis request",
    )
    ```
    <!--- KNIT example-tools-overview-05.kt -->

=== "Java"

    ```java
    // Create a specialized agent service for financial analysis
    // NOTE: The Kotlin `createAgentTool` is an extension with reified generics and default serializers.
    // From Java, there's no direct equivalent without explicit KSerializer instances.
    // As of now, a Java-friendly overload is not exposed; thus this call cannot be reproduced exactly in Java.
    // FAILED: `AIAgentService.createAgentTool` (extension with reified serializers) is not directly callable from Java.
    // If a Java API becomes available (e.g., service.createAgentTool(String name, String desc, KSerializer<Input>, KSerializer<Output>, ...)),
    // you could create the tool like this and then add it to a ToolRegistry.
    // String apiKey = ""; // your API key
    // ToolRegistry analysisToolRegistry = ToolRegistry.builder().build();
    // AIAgentService<String, String, ?> analysisAgentService = AIAgentService.builder()
    //     .promptExecutor(simpleOpenAIExecutor(apiKey))
    //     .llmModel(OpenAIModels.Chat.GPT4o)
    //     .systemPrompt("You are a financial analysis specialist.")
    //     .toolRegistry(analysisToolRegistry)
    //     .build();
    //
    // var analysisAgentTool = /* analysisAgentService.createAgentTool("analyzeTransactions", "Performs financial transaction analysis", "Transaction analysis request", inputSerializer, outputSerializer, null, clock) */;
    ```

### Using agent tools in other agents

Once converted to a tool, you can add the agent tool to another agent's tool registry:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.example.exampleToolsOverview05.analysisAgentTool
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    const val apiKey = ""
    -->
    ```kotlin
    // Create a coordinator agent that can use specialized agents as tools
    val coordinatorAgent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You coordinate different specialized services.",
        toolRegistry = ToolRegistry {
            tool(analysisAgentTool)
            // Add other tools as needed
        }
    )
    ```
    <!--- KNIT example-tools-overview-06.kt -->

=== "Java"

    ```java
    // Coordinator agent that uses previously created `analysisAgentTool`
    // FAILED: Depends on creating `analysisAgentTool` in Java, which is blocked by the limitation explained above.
    // The rest of the wiring is straightforward once the tool instance exists.
    //
    // String apiKey = "";
    // ToolRegistry registry = ToolRegistry.builder()
    //     .tools(analysisAgentTool)
    //     .build();
    //
    // AIAgent<String, String> coordinatorAgent = AIAgent.builder()
    //     .promptExecutor(simpleOpenAIExecutor(apiKey))
    //     .llmModel(OpenAIModels.Chat.GPT4o)
    //     .systemPrompt("You coordinate different specialized services.")
    //     .toolRegistry(registry)
    //     .build();
    ```

### Agent tool execution

When an agent tool is called:

1. The arguments are deserialized according to the input descriptor.
2. The wrapped agent is executed with the deserialized input.
3. The agent's output is serialized and returned as the tool result.

### Benefits of agents as tools

- **Modularity**: Break complex workflows into specialized agents.
- **Reusability**: Use the same specialized agent across multiple coordinator agents.
- **Separation of concerns**: Each agent can focus on its specific domain.
