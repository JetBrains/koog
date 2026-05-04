# Testing

## Overview

The Testing feature provides a comprehensive framework for testing AI agent pipelines, subgraphs, and tool interactions
in the Koog framework. It enables developers to create controlled test environments with mock LLM (Large
Language Model) executors, tool registries, and agent environments.

### Purpose

The primary purpose of this feature is to facilitate testing of agent-based AI features by:

- Mocking LLM responses to specific prompts
- Simulating tool calls and their results
- Testing agent pipeline subgraphs and their structures
- Verifying the correct flow of data through agent nodes
- Providing assertions for expected behaviors

## Configuration and initialization

### Setting up test dependencies

Before setting up a test environment, make sure that you have added the following dependencies:

<!--- INCLUDE
/*
-->
<!--- SUFFIX
*/
-->
=== "Gradle (Kotlin)"

    <!--- INCLUDE
    /*
    -->
    <!--- SUFFIX
    */
    -->
    ```kotlin title="build.gradle.kts"
    dependencies {
       testImplementation("ai.koog:agents-test:LATEST_VERSION")
       testImplementation(kotlin("test"))
    }
    ```
    <!--- KNIT example-testing-dependencies-gradle-kotlin-01.kt -->

=== "Gradle (Groovy)"

    <!--- INCLUDE
    /*
    -->
    <!--- SUFFIX
    */
    -->
    ```groovy title="build.gradle"
    dependencies {
       testImplementation 'ai.koog:agents-test:LATEST_VERSION'
       testImplementation 'org.jetbrains.kotlin:kotlin-test'
    }
    ```
    <!--- KNIT example-testing-dependencies-groovy-01.kt -->

=== "Maven"

    <!--- INCLUDE
    /*
    -->
    <!--- SUFFIX
    */
    -->
    ```xml title="pom.xml"
    <dependency>
       <groupId>ai.koog</groupId>
       <artifactId>agents-test-jvm</artifactId>
       <version>LATEST_VERSION</version>
       <scope>test</scope>
    </dependency>
    <dependency>
       <groupId>org.jetbrains.kotlin</groupId>
       <artifactId>kotlin-test</artifactId>
       <version>LATEST_VERSION</version>
       <scope>test</scope>
    </dependency>
    ```
    <!--- KNIT example-testing-dependencies-maven-01.kt -->

### Mocking LLM responses

The basic form of testing involves mocking LLM responses to ensure deterministic behavior. You can do this using  `MockLLMBuilder` and related utilities.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.testing.tools.getMockExecutor
    val toolRegistry = ToolRegistry {}
    -->
    ```kotlin
    // Create a mock LLM executor
    val mockLLMApi = getMockExecutor {
      // Mock a simple text response
      mockLLMAnswer("Hello!") onRequestContains "Hello"

      // Mock a default response
      mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
    }
    ```
    <!--- KNIT example-testing-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.tools.MockPromptExecutor;

    class ExampleTesting01 {
    void main() {
    -->
    <!--- SUFFIX
    }
    }
    -->
    ```java
    // Create a mock LLM executor
    var mockLLMApi = MockPromptExecutor.builder()
        .mockLLMAnswer("Hello!").onRequestContains("Hello")
        .mockLLMAnswer("I don't know how to answer that.").asDefaultResponse()
        .build();
    ```
    <!--- KNIT example-testing-java-01.java -->

### Mocking tool calls

You can mock the LLM to call specific tools based on input patterns:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.*
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.ext.tool.SayToUser
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    public object CreateTool : Tool<CreateTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        /**
        * Represents the arguments for the [AskUser] tool
        *
        * @property message The message to be used as an argument for the tool's execution.
        */
        @Serializable
        public data class Args(
            @property:LLMDescription("Message from the agent")
            val message: String
        )
        override suspend fun execute(args: Args): String = args.message
    }
    public object SearchTool : Tool<SearchTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        /**
        * Represents the arguments for the [AskUser] tool
        *
        * @property message The message to be used as an argument for the tool's execution.
        */
        @Serializable
        public data class Args(
            @property:LLMDescription("Message from the agent")
            val query: String
        )
        override suspend fun execute(args: Args): String = args.query
    }
    public object AnalyzeTool : Tool<AnalyzeTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        /**
        * Represents the arguments for the [AskUser] tool
        *
        * @property message The message to be used as an argument for the tool's execution.
        */
        @Serializable
        public data class Args(
            @property:LLMDescription("Message from the agent")
            val query: String
        )
        override suspend fun execute(args: Args): String = args.query
    }
    typealias PositiveToneTool = SayToUser
    typealias NegativeToneTool = SayToUser
    val mockLLMApi = getMockExecutor {
    -->
    <!--- SUFFIX
    }
    -->
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
    mockTool(AnalyzeTool) returns "Detailed analysis" onArguments AnalyzeTool.Args("analyze deeply")

    // Mock tool behavior with conditional argument matching
    mockTool(SearchTool) returns "Found results" onArgumentsMatching { args ->
      args.query.contains("important")
    }
    ```
    <!--- KNIT example-testing-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.agents.example.utils.Utils.ToneTools.PositiveToneTool;
    import ai.koog.agents.example.utils.Utils.ToneTools.NegativeToneTool;
    import ai.koog.agents.example.utils.Utils.CreateTool;
    import ai.koog.agents.example.utils.Utils.AnalyzeTool;
    import ai.koog.agents.example.utils.Utils.SearchTool;

    class ExampleTesting02 {
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    // Create a mock LLM executor
    PromptExecutor mockLLMApi = MockPromptExecutor.builder()
        .mockLLMToolCall(CreateTool.INSTANCE, new CreateTool.Args("solve")).onRequestEquals("Solve task")
        .mockTool(PositiveToneTool.INSTANCE).alwaysReturns("The text has a positive tone.")
        .mockTool(NegativeToneTool.INSTANCE).alwaysTells(() -> {
            // Perform some extra action
            System.out.println("Negative tone tool called");
            
                    // Return the result
                    return "The text has a negative tone.";
            })
        .mockTool(AnalyzeTool.INSTANCE).returns("Detailed analysis").onArguments(new AnalyzeTool.Args("analyze deeply"))
        .mockTool(SearchTool.INSTANCE).returns("Found results").onArgumentsMatching(args -> args.getQuery().contains("important"))
        .build();
    ```
    <!--- KNIT example-testing-java-02.java -->


The examples above demonstrate different ways to mock tools, from simple to more complex ones:

1. `alwaysReturns`: the simplest form, directly returns a value without a lambda.
2. `alwaysTells`: uses a lambda when you need to perform additional actions.
3. `returns...onArguments`: returns specific results for exact argument matches.
4. `returns...onArgumentsMatching`: returns results based on custom argument conditions.

### Enabling testing mode

To enable the testing mode on an agent, use the `withTesting()` function within the AIAgent constructor block:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.testing.feature.withTesting
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    val llmModel = OpenAIModels.Chat.GPT4o
    // Create the agent with testing enabled
    fun main() {
    val mockLLMApi  = getMockExecutor { }
    val toolRegistry = ToolRegistry.EMPTY
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    // Create the agent with testing enabled
    AIAgent(
        promptExecutor = mockLLMApi,
        toolRegistry = toolRegistry,
        llmModel = llmModel
    ) {
        // Enable testing mode
        withTesting()
    }
    ```
    <!--- KNIT example-testing-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;

    class ExampleTesting03 {
        void main() {
            var mockLLMApi = MockPromptExecutor.builder().build();
            var toolRegistry = ToolRegistry.builder().build();
            var llmModel = OpenAIModels.Chat.GPT4o;
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    // Create the agent with testing enabled
    AIAgent.builder()
        .promptExecutor(mockLLMApi)
        .toolRegistry(toolRegistry)
        .llmModel(llmModel)
        .install(Testing.Feature, config -> {
            // Enable testing mode
            // No additional configuration needed here for simple cases
        })
        .build();
    ```
    <!--- KNIT example-testing-java-03.java -->


## Advanced testing

### Testing the graph structure

Before testing the detailed node behavior and edge connections, it is important to verify the overall structure of your agent's graph. This includes checking that all required nodes exist and are properly connected in the expected subgraphs.

The Testing feature provides a comprehensive way to test your agent's graph structure. This approach is particularly valuable for complex agents with multiple subgraphs and interconnected nodes.

#### Basic structure testing

Start by validating the fundamental structure of your agent's graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message

    val mockLLMApi  = getMockExecutor { }
    val toolRegistry = ToolRegistry.EMPTY
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    AIAgent(
        // Constructor arguments
        promptExecutor = mockLLMApi,
        toolRegistry = toolRegistry,
        llmModel = llmModel
    ) {
        testGraph<String, String>("test") {
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
                val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")

                // Assert node reachability
                assertReachable(start, askLLM)
                assertReachable(askLLM, callTool)
            }
        }
    }
    ```
    <!--- KNIT example-testing-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.llm.LLModel;
    import ai.koog.prompt.message.Message;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.agents.core.tools.ToolRegistry;

    class ExampleTesting04 {
      PromptExecutor mockLLMApi;
      ToolRegistry toolRegistry;
      LLModel llmModel = OpenAIModels.Chat.GPT4o;
      {
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    AIAgent.builder()
        .promptExecutor(mockLLMApi)
        .toolRegistry(toolRegistry)
        .llmModel(llmModel)
        .install(Testing.Feature, config -> {
            config.verifyStrategy("test", strategyAssertions -> {
                var firstSubgraph = strategyAssertions.<String, String>assertSubgraphByName("first");
                var secondSubgraph = strategyAssertions.<String, String>assertSubgraphByName("second");
                
                var startNode = strategyAssertions.startNode();
                var finishNode = strategyAssertions.finishNode();
                
                // Assert subgraph connections
                startNode.alwaysGoesTo(firstSubgraph);
                firstSubgraph.alwaysGoesTo(secondSubgraph);
                secondSubgraph.alwaysGoesTo(finishNode);

                // Verify the first subgraph
                strategyAssertions.verifySubgraph(firstSubgraph, subgraphAssertions -> {
                    var start = subgraphAssertions.startNode();
                    var finish = subgraphAssertions.finishNode();

                    // Assert nodes by name
                    var askLLM = subgraphAssertions.assertNodeByName("callLLM");
                    var callTool = subgraphAssertions.assertNodeByName("executeTool");

                    // Assert node reachability
                    subgraphAssertions.assertReachable(start, askLLM);
                    subgraphAssertions.assertReachable(askLLM, callTool);
                });
            });
        })
        .build();
    ```
    <!--- KNIT example-testing-java-04.java -->


### Testing node behavior

Node behavior testing lets you verify that nodes in your agent's graph produce the expected outputs for the given inputs. 
This is crucial for ensuring that your agent's logic works correctly under different scenarios.

#### Basic node testing

Start with simple input and output validations for individual nodes:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    import ai.koog.prompt.executor.model.PromptExecutor
    import ai.koog.agents.core.tools.Tool

    val mockLLMApi = getMockExecutor { }
    val toolRegistry = ToolRegistry.EMPTY

    public object CreateTool : Tool<CreateTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        /**
        * Represents the arguments for the [AskUser] tool
        *
        * @property message The message to be used as an argument for the tool's execution.
        */
        @Serializable
        public data class Args(
            @property:LLMDescription("Message from the agent")
            val message: String
        )
        override suspend fun execute(args: Args): String = args.message
    }

    val llmModel = OpenAIModels.Chat.GPT4o

    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertNodes {

        // Test basic text responses
        askLLM withInput "Hello" outputs assistantMessage("Hello!")

        // Test tool call responses
        askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
    }
    ```
    <!--- KNIT example-testing-05.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;
    
    class ExampleTesting05 {
        class CreateToolArgs { CreateToolArgs(String s) {} };

        Tool<CreateToolArgs, String> CreateTool;

        public void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    var askLLM = subgraphAssertions.assertNodeByName("callLLM");

    // Test basic text responses
    askLLM.withInput("Hello").outputs(subgraphAssertions.assistantMessage("Hello!", null));

    // Test tool call responses
    askLLM.withInput("Solve task").outputs(subgraphAssertions.toolCallMessage(CreateTool, new CreateToolArgs("solve")));
    ```
    <!--- KNIT example-testing-java-05.java -->


The example above shows how to test the following behavior:
1. When the LLM node receives `Hello` as the input, it responds with a simple text message.
2. When it receives `Solve task`, it responds with a tool call.

#### Testing tool run nodes

You can also test nodes that run tools:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.core.tools.*
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.prompt.executor.model.PromptExecutor

    val mockLLMApi: PromptExecutor = TODO()
    val toolRegistry: ToolRegistry = TODO()
    object SolveTool : SimpleTool<SolveTool.Args>(
        argsType = typeToken<Args>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Message from the agent")
            val message: String
        )
        override suspend fun execute(args: Args): String {
            return args.message
        }
    }
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertNodes {
        // Test tool runs with specific arguments
        callTool withInput toolCallMessage(
            SolveTool,
            SolveTool.Args("solve")
        ) outputs toolResult(SolveTool, SolveTool.Args("solve"), "solved")
    }
    ```
    <!--- KNIT example-testing-06.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;
    
    class ExampleTesting06 {
        class SolveToolArgs { SolveToolArgs(String s) {} }

        Tool<SolveToolArgs, String> SolveTool;

        public void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var callTool = subgraphAssertions.assertNodeByName("executeTool");

    // Test tool runs with specific arguments
    callTool.withInput(subgraphAssertions.toolCallMessage(SolveTool, new SolveToolArgs("solve")))
         .outputs(subgraphAssertions.toolResult(SolveTool, new SolveToolArgs("solve"), "solved"));
    ```
    <!--- KNIT example-testing-java-06.java -->


This verifies that when the tool execution node receives a specific tool call signature, it produces the expected tool result.

#### Advanced node testing

For more complex scenarios, you can test nodes with structured inputs and outputs:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.*
    import ai.koog.agents.ext.tool.AskUser
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.prompt.executor.model.PromptExecutor

    val mockLLMApi: PromptExecutor = TODO()
    val toolRegistry: ToolRegistry = TODO()
    object AnalyzeTool : Tool<AnalyzeTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Message from the agent")
            val query: String,
            val depth: Int
        )
        override suspend fun execute(args: Args): String = args.query
    }
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
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
    <!--- KNIT example-testing-07.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;
    
    class ExampleTesting07 {
        class AnalyzeToolArgs { AnalyzeToolArgs(String s, Integer i) {} }

        Tool<AnalyzeToolArgs, String> AnalyzeTool;

        void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var askLLM = subgraphAssertions.assertNodeByName("callLLM");

    // Test with different inputs to the same node
    askLLM.withInput("Simple query").outputs(subgraphAssertions.assistantMessage("Simple response", null));

    // Test with complex parameters
    askLLM.withInput("Complex query with parameters")
         .outputs(subgraphAssertions.toolCallMessage(AnalyzeTool, new AnalyzeToolArgs("parameters", 3)));
    ```
    <!--- KNIT example-testing-java-07.java -->

You can also test complex tool call scenarios with detailed result structures:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.core.tools.*
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    import ai.koog.serialization.typeToken
    import kotlinx.serialization.Serializable
    import ai.koog.prompt.executor.model.PromptExecutor

    val mockLLMApi: PromptExecutor = TODO()
    val toolRegistry: ToolRegistry = TODO()
    object AnalyzeTool : Tool<AnalyzeTool.Args, AnalyzeTool.Result>(
        argsType = typeToken<Args>(),
        resultType = typeToken<Result>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        @Serializable
        data class Args(
            val query: String,
            val depth: Int
        )
        @Serializable
        data class Result(
            val analysis: String,
            val confidence: Double,
            val metadata: Map<String, String> = mapOf()
        )
        override suspend fun execute(args: Args): Result {
            return Result(
                args.query, 0.95,
                mapOf("source" to "mock", "timestamp" to "2023-06-15")
            )
        }
    }
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertNodes {
        // Test a complex tool call with a structured result
        callTool withInput toolCallMessage(
            AnalyzeTool,
            AnalyzeTool.Args(query = "complex", depth = 5)
        ) outputs toolResult(AnalyzeTool, AnalyzeTool.Args(query = "complex", depth = 5), AnalyzeTool.Result(
            analysis = "Detailed analysis",
            confidence = 0.95,
            metadata = mapOf("source" to "database", "timestamp" to "2023-06-15")
        ))
    }
    ```
    <!--- KNIT example-testing-08.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;
    
    class ExampleTesting08 {
        class AnalyzeToolArgs { AnalyzeToolArgs(String s, Integer i) {} }

        Tool<AnalyzeToolArgs, String> AnalyzeTool;
        
        void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    var callTool = subgraphAssertions.assertNodeByName("executeTool");

    callTool.withInput(subgraphAssertions.toolCallMessage(AnalyzeTool, new AnalyzeToolArgs("parameters", 3)))
         .outputs(subgraphAssertions.toolResult(AnalyzeTool, new AnalyzeToolArgs("parameters", 3), "analysis result"));
    ```
    <!--- KNIT example-testing-java-08.java -->

These advanced tests help ensure that your nodes handle complex data structures correctly, which is essential for sophisticated agent behaviors.

### Testing edge connections

Edge connections testing allows you to verify that your agent's graph correctly routes outputs from one node to the appropriate next node. This ensures that your agent follows the intended workflow paths based on different outputs.

#### Basic edge testing

Start with simple edge connection tests:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.core.tools.*
    import ai.koog.serialization.typeToken
    import ai.koog.agents.core.tools.annotations.LLMDescription
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    import kotlinx.serialization.KSerializer
    import kotlinx.serialization.Serializable
    import ai.koog.prompt.executor.model.PromptExecutor

    val mockLLMApi: PromptExecutor = TODO()
    val toolRegistry: ToolRegistry = TODO()
    
    public object CreateTool : Tool<CreateTool.Args, String>(
        argsType = typeToken<Args>(),
        resultType = typeToken<String>(),
        name = "message",
        description = "Service tool, used by the agent to talk with user"
    ) {
        /**
        * Represents the arguments for the [AskUser] tool
        *
        * @property message The message to be used as an argument for the tool's execution.
        */
        @Serializable
        public data class Args(
            @property:LLMDescription("Message from the agent")
            val message: String
        )
        override suspend fun execute(args: Args): String = args.message
    }

    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                    val giveFeedback = assertNodeByName<String, Message.Response>("giveFeedback")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertEdges {
        // Test text message routing
        askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback

        // Test tool call routing
        askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
    }
    ```
    <!--- KNIT example-testing-09.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;

    class ExampleTesting09 {
        class CreateToolArgs { CreateToolArgs(String s) {} };

        Tool<CreateToolArgs, String> CreateTool;

        void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
            var askLLM = subgraphAssertions.assertNodeByName("callLLM");
            var callTool = subgraphAssertions.assertNodeByName("executeTool");
            var giveFeedback = subgraphAssertions.assertNodeByName("giveFeedback");
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    // Test text message routing
    askLLM.withOutput(subgraphAssertions.assistantMessage("Hello!")).goesTo(giveFeedback);

    // Test tool call routing
    askLLM.withOutput(subgraphAssertions.toolCallMessage(CreateTool, "CreateTool")).goesTo(callTool);
    ```
    <!--- KNIT example-testing-java-09.java -->

This example verifies the following behavior:
1. When the LLM node outputs a simple text message, the flow is directed to the `giveFeedback` node.
2. When it outputs a tool call, the flow is directed to the `callTool` node.

#### Testing conditional routing

You can test a more complex routing logic based on the content of outputs:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.example.exampleTesting02.mockLLMApi
    import ai.koog.agents.example.exampleTesting01.toolRegistry
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                    val askForInfo = assertNodeByName<String, ReceivedToolResult>("askForInfo")
                    val processRequest = assertNodeByName<String, Message.Response>("processRequest")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertEdges {
        // Different text responses can route to different nodes
        askLLM withOutput assistantMessage("Need more information") goesTo askForInfo
        askLLM withOutput assistantMessage("Ready to proceed") goesTo processRequest
    }
    ```
    <!--- KNIT example-testing-10.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;

    class ExampleTesting10 {
        void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
            var askLLM = subgraphAssertions.assertNodeByName("callLLM");
            var askForInfo = subgraphAssertions.assertNodeByName("askForInfo");
            var processRequest = subgraphAssertions.assertNodeByName("processRequest");
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    askLLM.withOutput(subgraphAssertions.assistantMessage("Need more information")).goesTo(askForInfo);
    askLLM.withOutput(subgraphAssertions.assistantMessage("Ready to proceed")).goesTo(processRequest);
    ```
    <!--- KNIT example-testing-java-10.java -->

#### Advanced edge testing

For sophisticated agents, you can test conditional routing based on structured data in tool results:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.example.exampleTesting02.mockLLMApi
    import ai.koog.agents.example.exampleTesting01.toolRegistry
    import ai.koog.agents.example.exampleTesting08.AnalyzeTool
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                    val processResult = assertNodeByName<String, Message.Response>("processResult")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertEdges {
        // Test routing based on tool result content
        callTool withOutput toolResult(
            AnalyzeTool,
            AnalyzeTool.Args(query = "parameters", depth = 3),
            AnalyzeTool.Result(analysis = "Needs more processing", confidence = 0.5)
        ) goesTo processResult
    }
    ```
    <!--- KNIT example-testing-11.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;

    class ExampleTesting11 {
        class AnalyzeToolArgs { AnalyzeToolArgs(String s, Integer i) {} };
        class AnalyzeToolResult { AnalyzeToolResult(String s, Double d) {} };
        Tool<AnalyzeToolArgs, AnalyzeToolResult> AnalyzeTool;

        public void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
            var callTool = subgraphAssertions.assertNodeByName("executeTool");
            var processResult = subgraphAssertions.assertNodeByName("processResult");
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    callTool.withOutput(
        subgraphAssertions.toolResult(
            AnalyzeTool,
            new AnalyzeToolArgs("parameters", 3),
            new AnalyzeToolResult("Needs more processing", 0.5)
        )
    ).goesTo(processResult);
    ```
    <!--- KNIT example-testing-java-11.java -->

You can also test complex decision paths based on different result properties:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.example.exampleTesting02.mockLLMApi
    import ai.koog.agents.example.exampleTesting01.toolRegistry
    import ai.koog.agents.example.exampleTesting08.AnalyzeTool
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
            testGraph<String, String>("test") {
                assertNodes {
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                    val finish = assertNodeByName<String, Message.Response>("finish")
                    val verifyResult = assertNodeByName<String, Message.Response>("verifyResult")
    -->
    <!--- SUFFIX
                }
            }
        }
    }
    -->
    ```kotlin
    assertEdges {
        // Route to different nodes based on confidence level
        callTool withOutput toolResult(
            AnalyzeTool,
            AnalyzeTool.Args(query = "parameters", depth = 3),
            AnalyzeTool.Result(analysis = "Complete", confidence = 0.9)
        ) goesTo finish

        callTool withOutput toolResult(
            AnalyzeTool,
            AnalyzeTool.Args(query = "parameters", depth = 3),
            AnalyzeTool.Result(analysis = "Uncertain", confidence = 0.3)
        ) goesTo verifyResult
    }
    ```
    <!--- KNIT example-testing-12.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;
    import ai.koog.agents.core.environment.ReceivedToolResult;

    class ExampleTesting12 {
        class AnalyzeToolArgs { AnalyzeToolArgs(String s, Integer i) {} };
        class AnalyzeToolResult { AnalyzeToolResult(String s, Double d) {} };
        Tool<AnalyzeToolArgs, AnalyzeToolResult> AnalyzeTool;

        void run(Testing.Config.SubgraphAssertionsBuilder subgraphAssertions) {
            var callTool = subgraphAssertions.<Message.Tool.Call, ReceivedToolResult>assertNodeByName("executeTool");
            var finish = subgraphAssertions.<String, Message.Response>assertNodeByName("finish");
            var verifyResult = subgraphAssertions.<String, Message.Response>assertNodeByName("verifyResult");
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // Route to different nodes based on confidence level
    callTool.withOutput(
        subgraphAssertions.toolResult(
            AnalyzeTool,
            new AnalyzeToolArgs("parameters", 3),
            new AnalyzeToolResult("Complete", 0.9)
        )
    ).goesTo(finish);

    callTool.withOutput(
        subgraphAssertions.toolResult(
            AnalyzeTool,
            new AnalyzeToolArgs("parameters", 3),
            new AnalyzeToolResult("Uncertain", 0.3)
        )
    ).goesTo(verifyResult);
    ```
    <!--- KNIT example-testing-java-12.java -->

These advanced edge tests help ensure that your agent makes the correct decisions based on the content and structure of node outputs, which is essential for creating intelligent, context-aware workflows.

## Complete testing example

Here is a user story that demonstrates a complete testing scenario:

You are developing a tone analysis agent that analyzes the tone of the text and provides feedback. The agent uses tools for detecting positive, negative, and neutral tones.

Here is how you can test this agent:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.utils.Utils.ToneTools
    import ai.koog.agents.example.utils.Utils.ToneTools.NegativeToneTool
    import ai.koog.agents.example.utils.Utils.ToneTools.PositiveToneTool
    import ai.koog.agents.example.utils.Utils.ToneTools.NeutralToneTool
    import ai.koog.agents.example.utils.Utils.ToneTools.ToneTool
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.config.AIAgentConfig
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.example.exampleCustomStrategyGraphs11.toneStrategy
    import ai.koog.agents.ext.tool.SayToUser
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.agents.features.eventHandler.feature.handleEvents
    import ai.koog.agents.testing.feature.withTesting
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.llm.LLModel
    import io.mockk.mockk
    import kotlin.test.assertEquals
    -->
    ```kotlin
    suspend fun testToneAgent() {
        // Create a list to track tool calls
        var toolCalls = mutableListOf<String>()
        var result: String? = null
    
        // Create a tool registry
        val toolRegistry = ToolRegistry {
            // A special tool, required with this type of agent
            tool(SayToUser)
    
            with(ToneTools) {
                tools()
            }
        }
    
        val positiveText = "I love this product!"
        val negativeText = "Awful service, hate the app."
        val defaultText = "I don't know how to answer this question."
    
        val positiveResponse = "The text has a positive tone."
        val negativeResponse = "The text has a negative tone."
        val neutralResponse = "The text has a neutral tone."
    
        val mockLLMApi = getMockExecutor {
            // Set up LLM responses for different input texts
            mockLLMToolCall(NeutralToneTool, ToneTool.Args(defaultText)) onRequestEquals defaultText
            mockLLMToolCall(PositiveToneTool, ToneTool.Args(positiveText)) onRequestEquals positiveText
            mockLLMToolCall(NegativeToneTool, ToneTool.Args(negativeText)) onRequestEquals negativeText
    
            // Mock the behavior where the LLM responds with just tool responses when the tools return results
            mockLLMAnswer(positiveResponse) onRequestContains positiveResponse
            mockLLMAnswer(negativeResponse) onRequestContains negativeResponse
            mockLLMAnswer(neutralResponse) onRequestContains neutralResponse
    
            mockLLMAnswer(defaultText).asDefaultResponse
    
            // Tool mocks
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
    
        // Create a strategy
        val strategy = toneStrategy("tone_analysis", toolRegistry)
    
        // Create an agent configuration
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
    
        // Create an agent with testing enabled
        val agent = AIAgent(
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            strategy = strategy,
            agentConfig = agentConfig,
        ) {
            handleEvents {
                onToolCallStarting { toolCallContext ->
                    println("[DEBUG_LOG] Tool called: tool ${toolCallContext.toolName}, args ${toolCallContext.toolArgs}")
                    toolCalls.add(toolCallContext.toolName)
                }
            }
    
            withTesting()
        }
    
        // Test the positive text
        agent.run(positiveText)
        assertEquals("The text has a positive tone.", result, "Positive tone result should match")
        assertEquals(1, toolCalls.size, "One tool is expected to be called")
    
        // Test the negative text
        agent.run(negativeText)
        assertEquals("The text has a negative tone.", result, "Negative tone result should match")
        assertEquals(2, toolCalls.size, "Two tools are expected to be called")
    
        //Test the neutral text
        agent.run(defaultText)
        assertEquals("The text has a neutral tone.", result, "Neutral tone result should match")
        assertEquals(3, toolCalls.size, "Three tools are expected to be called")
    }
    ```
    <!--- KNIT example-testing-13.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.llm.LLModel;
    import ai.koog.prompt.message.Message;
    import ai.koog.agents.core.tools.Tool;

    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.example.utils.Utils.ToneTools.NegativeToneTool;
    import ai.koog.agents.example.utils.Utils.ToneTools.PositiveToneTool;
    import ai.koog.agents.example.utils.Utils.ToneTools.NeutralToneTool;
    import ai.koog.agents.example.utils.Utils.ToneTools.ToneTool;
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.config.AIAgentConfig;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.ext.tool.SayToUser;
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.agents.features.eventHandler.feature.EventHandler;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.dsl.Prompt;
    import ai.koog.prompt.llm.LLModel;
    
    import java.util.List;
    
    import static org.junit.jupiter.api.Assertions.assertEquals;
    
    class ExampleTesting13 {
        AIAgentGraphStrategy<String, String> toneStrategy(String name, ToolRegistry toolRegistry) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        LLModel llmModel;
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    void testToneAgent() {
        // Create a list to track tool calls
        var toolCalls = List.of();
        String result = null;

        // Create a tool registry
        var toolRegistry = ToolRegistry.builder()
            .tool(SayToUser.INSTANCE)
            .tool(PositiveToneTool.INSTANCE)
            .tool(NegativeToneTool.INSTANCE)
            .tool(NeutralToneTool.INSTANCE)
            .build();

        var positiveText = "I love this product!";
        var negativeText = "Awful service, hate the app.";
        var defaultText = "I don't know how to answer this question.";

        var positiveResponse = "The text has a positive tone.";
        var negativeResponse = "The text has a negative tone.";
        var neutralResponse = "The text has a neutral tone.";

        var mockLLMApi = MockPromptExecutor.builder()
            // Set up LLM responses for different input texts
            .mockLLMToolCall(NeutralToneTool.INSTANCE, new ToneTool.Args(defaultText)).onRequestEquals(defaultText)
            .mockLLMToolCall(PositiveToneTool.INSTANCE, new ToneTool.Args(positiveText)).onRequestEquals(positiveText)
            .mockLLMToolCall(NegativeToneTool.INSTANCE, new ToneTool.Args(negativeText)).onRequestEquals(negativeText)

            // Mock the behavior where the LLM responds with just tool responses when the tools return results
            .mockLLMAnswer(positiveResponse).onRequestContains(positiveResponse)
            .mockLLMAnswer(negativeResponse).onRequestContains(negativeResponse)
            .mockLLMAnswer(neutralResponse).onRequestContains(neutralResponse)

            .mockLLMAnswer(defaultText).asDefaultResponse()

            // Tool mocks
            .mockTool(PositiveToneTool.INSTANCE).alwaysTells(() ->
                {
                    toolCalls.add("Positive tone tool called");
                    return positiveResponse;
                }
            )
            .mockTool(NegativeToneTool.INSTANCE).alwaysTells(() ->
                {
                    toolCalls.add("Negative tone tool called");
                    return negativeResponse;
                }
            )
            .mockTool(NeutralToneTool.INSTANCE).alwaysTells(() ->
                {
                    toolCalls.add("Neutral tone tool called");
                    return neutralResponse;
                }
            )
            .build();

        // Create a strategy
        var strategy = toneStrategy("tone_analysis", toolRegistry);

        // Create an agent configuration
        var agentConfig = AIAgentConfig.builder()
            .model(llmModel)
            .prompt(Prompt.builder("test-agent")
                .system("you are a helpful assistant")
                .build()
            )
            .maxAgentIterations(10)
            .build();

        // Create an agent with testing enabled
        var agent = AIAgent.builder()
            .promptExecutor(mockLLMApi)
            .toolRegistry(toolRegistry)
            .graphStrategy(strategy)
            .agentConfig(agentConfig)
            .install(EventHandler.Feature, (config) ->
                {
                    config.onToolCallStarting((toolCallContext) ->
                        {
                            toolCalls.add(toolCallContext.getToolName());
                        }
                    );
                }
            )
            .install(Testing.Feature, (config) -> {})
            .build();

        // Test the positive text
        agent.run(positiveText);
        assertEquals("The text has a positive tone.", result, "Positive tone result should match");
        assertEquals(1, toolCalls.size(), "One tool is expected to be called");

        // Test the negative text
        agent.run(negativeText);
        assertEquals("The text has a negative tone.", result, "Negative tone result should match");
        assertEquals(2, toolCalls.size(), "Two tools are expected to be called");

        //Test the neutral text
        agent.run(defaultText);
        assertEquals("The text has a neutral tone.", result, "Neutral tone result should match");
        assertEquals(3, toolCalls.size(), "Three tools are expected to be called");
    }
    ```
    <!--- KNIT example-testing-java-13.java -->

For more complex agents with multiple subgraphs, you can also test the graph structure:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.agent.config.AIAgentConfig
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.testing.tools.getMockExecutor
    import ai.koog.prompt.dsl.prompt
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeExecuteTool
    import ai.koog.agents.core.dsl.extension.nodeLLMRequest
    import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
    import ai.koog.agents.core.dsl.extension.onAssistantMessage
    import ai.koog.agents.core.dsl.extension.onToolCall
    import ai.koog.agents.core.environment.ReceivedToolResult
    import ai.koog.agents.example.exampleTesting02.CreateTool
    import ai.koog.agents.example.exampleTesting06.SolveTool
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.agents.testing.tools.DummyTool
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.Message
    -->
    ```kotlin
    fun testMultiSubgraphAgentStructure() {
        val strategy = strategy("test") {
            val firstSubgraph by subgraph(
                "first",
                tools = listOf(DummyTool(), CreateTool, SolveTool)
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
            tool(DummyTool())
            tool(CreateTool)
            tool(SolveTool)
        }
    
        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }
    
        val basePrompt = prompt("test") {}
    
        AIAgent(
            toolRegistry = toolRegistry,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt = basePrompt, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 100),
            promptExecutor = mockLLMApi,
        ) {
            testGraph<String, String>("test") {
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
                        ) outputs toolResult(SolveTool, SolveTool.Args("solve"), "solved")
    
                        callTool withInput toolCallMessage(
                            CreateTool,
                            CreateTool.Args("solve")
                        ) outputs toolResult(CreateTool, CreateTool.Args("solve"), "created")
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
    <!--- KNIT example-testing-14.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.llm.LLModel;
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.config.AIAgentConfig;
    import ai.koog.agents.core.agent.entity.ToolSelectionStrategy;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.core.environment.ReceivedToolResult;
    import ai.koog.agents.example.exampleTesting02.CreateTool;
    import ai.koog.agents.example.exampleTesting06.SolveTool;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.agents.testing.tools.DummyTool;
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.prompt.dsl.Prompt;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.message.Message;
    
    class ExampleTesting14 {
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    void testMultiSubgraphAgentStructure() {

        var toolRegistry = ToolRegistry.builder()
            .tool(new DummyTool())
            .tool(CreateTool.INSTANCE)
            .tool(SolveTool.INSTANCE)
            .build();

        var mockLLMApi = MockPromptExecutor.builder()
            .mockLLMAnswer("Hello!").onRequestContains("Hello")
            .mockLLMToolCall(CreateTool.INSTANCE, new CreateTool.Args("solve")).onRequestEquals("Solve task")
            .build();

        var basePrompt = Prompt.Empty;

        AIAgent.builder()
            .toolRegistry(toolRegistry)
            .graphStrategy("test-strategy", (builder) ->
                builder
                    .withInput(String.class)
                    .withOutput(String.class)
                    /* define graph here */
                    .build()
            )
            .agentConfig(
                AIAgentConfig.builder()
                    .model(OpenAIModels.Chat.GPT4o)
                    .prompt(basePrompt)
                    .maxAgentIterations(100)
                    .build()
            )
            .promptExecutor(mockLLMApi)
            .install(Testing.Feature, (config) ->
                config.verifyStrategy("test-strategy", (strategyAssertions) ->
                    {
                        var firstSubgraph = strategyAssertions.<String, String>assertSubgraphByName("first");
                        var secondSubgraph = strategyAssertions.<String, String>assertSubgraphByName("second");

                        strategyAssertions.startNode().alwaysGoesTo(firstSubgraph);
                        firstSubgraph.alwaysGoesTo(secondSubgraph);
                        secondSubgraph.alwaysGoesTo(strategyAssertions.finishNode());

                        strategyAssertions.verifySubgraph(firstSubgraph, (subgraphAssertions) ->
                            {
                                var start = subgraphAssertions.startNode();
                                var finish = subgraphAssertions.finishNode();

                                var askLLM = subgraphAssertions.assertNodeByName("callLLM");
                                var callTool = subgraphAssertions.assertNodeByName("executeTool");
                                var giveFeedback = subgraphAssertions.assertNodeByName("giveFeedback");

                                subgraphAssertions.assertReachable(start, askLLM);
                                subgraphAssertions.assertReachable(askLLM, callTool);

                                askLLM.withInput("Hello").outputs(subgraphAssertions.assistantMessage("Hello!"));
                                askLLM.withInput("Solve task").outputs(subgraphAssertions.toolCallMessage(CreateTool.INSTANCE, new CreateTool.Args("solve")));

                                callTool.withInput(subgraphAssertions.toolCallMessage(
                                    SolveTool.INSTANCE,
                                    new SolveTool.Args("solve")
                                )).outputs(subgraphAssertions.toolResult(SolveTool.INSTANCE, new SolveTool.Args("solve"), "solved"));

                                callTool.withInput(subgraphAssertions.toolCallMessage(
                                    CreateTool.INSTANCE,
                                    new CreateTool.Args("solve")
                                )).outputs(subgraphAssertions.toolResult(CreateTool.INSTANCE, new CreateTool.Args("solve"), "created"));

                                askLLM.withOutput(subgraphAssertions.assistantMessage("Hello!")).goesTo(giveFeedback);
                                askLLM.withOutput(subgraphAssertions.toolCallMessage(CreateTool.INSTANCE, new CreateTool.Args("solve"))).goesTo(callTool);
                            }
                        );
                    }
                )
            )
            .build();
    }
    ```
    <!--- KNIT example-testing-java-14.java -->

## API reference

For a complete API reference related to the Testing feature, see the reference documentation for the [agents-test](api:agents-test::) module.

## FAQ and troubleshooting

#### How do I mock a specific tool response?

Use the `mockTool` method in `MockLLMBuilder`:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.utils.Utils.myTool
    import ai.koog.agents.example.utils.Utils.myArgs
    import ai.koog.agents.example.utils.Utils.myResult
    import ai.koog.agents.testing.tools.getMockExecutor
    -->
    ```kotlin
    val mockExecutor = getMockExecutor {
        mockTool(myTool) alwaysReturns myResult

        // Or with conditions
        mockTool(myTool) returns myResult onArguments myArgs
    }
    ```
    <!--- KNIT example-testing-15.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.agents.example.utils.Utils.myTool;
    import ai.koog.agents.example.utils.Utils.myArgs;
    import ai.koog.agents.example.utils.Utils.myResult;

    class ExampleTesting15 {
        {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    PromptExecutor mockLLMApi = MockPromptExecutor.builder()
        .mockTool(myTool.INSTANCE).alwaysReturns(myResult.INSTANCE)
        .mockTool(myTool.INSTANCE).returns(myResult.INSTANCE).onArguments(myArgs.INSTANCE)
        .build();
    ```
    <!--- KNIT example-testing-java-15.java -->

#### How can I test complex graph structures?

Use the subgraph assertions, `verifySubgraph`, and node references:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.testing.feature.testGraph
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import ai.koog.agents.core.tools.ToolRegistry
    val mockLLMApi: PromptExecutor = TODO()
    val toolRegistry: ToolRegistry = TODO()
    val llmModel = OpenAIModels.Chat.GPT4o
    fun main() {
        AIAgent(
            // Constructor arguments
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            llmModel = llmModel
        ) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    testGraph<Unit, String>("test") {
        val mySubgraph = assertSubgraphByName<Unit, String>("mySubgraph")

        verifySubgraph(mySubgraph) {
            // Get references to nodes
            val nodeA = assertNodeByName<Unit, String>("nodeA")
            val nodeB = assertNodeByName<String, String>("nodeB")

            // Assert reachability
            assertReachable(nodeA, nodeB)

            // Assert edge connections
            assertEdges {
                nodeA.withOutput("result") goesTo nodeB
            }
        }
    }
    ```
    <!--- KNIT example-testing-16.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.testing.feature.Testing;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.llm.LLModel;

    class ExampleTesting16 {
      PromptExecutor mockLLMApi;
      ToolRegistry toolRegistry;
      LLModel llmModel = OpenAIModels.Chat.GPT4o;
      {
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    AIAgent.builder()
        .promptExecutor(mockLLMApi)
        .toolRegistry(toolRegistry)
        .llmModel(llmModel)
        .install(Testing.Feature, config -> {
            config.verifyStrategy("test", strategyAssertions -> {
                var mySubgraph = strategyAssertions.assertSubgraphByName("mySubgraph");

                strategyAssertions.verifySubgraph(mySubgraph, subgraphAssertions -> {
                    var nodeA = subgraphAssertions.assertNodeByName("nodeA");
                    var nodeB = subgraphAssertions.assertNodeByName("nodeB");

                    subgraphAssertions.assertReachable(nodeA, nodeB);
                    
                    nodeA.withOutput("result").goesTo(nodeB);
                });
            });
        })
        .build();
    ```
    <!--- KNIT example-testing-java-16.java -->

#### How do I simulate different LLM responses based on input?

Use pattern matching methods:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.testing.tools.getMockExecutor
    val promptExecutor = 
    -->
    ```kotlin
    getMockExecutor {
        mockLLMAnswer("Response A") onRequestContains "topic A"
        mockLLMAnswer("Response B") onRequestContains "topic B"
        mockLLMAnswer("Exact response") onRequestEquals "exact question"
        mockLLMAnswer("Conditional response") onCondition { it.contains("keyword") && it.length > 10 }
    }
    ```
    <!--- KNIT example-testing-17.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.testing.tools.MockPromptExecutor;
    import ai.koog.prompt.executor.model.PromptExecutor;

    class ExampleTesting17 {
      {
    -->
    <!--- SUFFIX
      }
    }
    -->
    ```java
    PromptExecutor promptExecutor = MockPromptExecutor.builder()
        .mockLLMAnswer("Response A").onRequestContains("topic A")
        .mockLLMAnswer("Response B").onRequestContains("topic B")
        .mockLLMAnswer("Exact response").onRequestEquals("exact question")
        .mockLLMAnswer("Conditional response").onCondition(s -> s.contains("keyword") && s.length() > 10)
        .build();
    ```
    <!--- KNIT example-testing-java-17.java -->

### Troubleshooting

#### Mock executor always returns the default response

Check that your pattern matching is correct. Patterns are case-sensitive and must match exactly as
specified.

#### Tool calls are not being intercepted

Ensure that:

1. The tool registry is properly set up.
2. The tool names match exactly.
3. The tool actions are configured correctly.

#### Graph assertions are failing

1. Verify that node names are correct.
2. Check that the graph structure matches your expectations.
3. Use the `startNode()` and `finishNode()` methods to get the correct entry and exit points.
