# History compression

AI agents maintain a message history that includes user messages, assistant responses, tool calls, and tool responses.
This history grows with each interaction as the agent follows its strategy.

For long-running conversations, the history can become large and consume a lot of tokens.
History compression helps reduce this by summarizing the full list of messages into one or several messages that contain only important information necessary for further agent operation.

History compression addresses key challenges in agent systems:

- Optimizes context usage. Focused and smaller contexts improve LLM performance and prevent failures from exceeding token limits.
- Improves performance. Compressing history reduces the number of messages the LLM processes, resulting in faster responses.
- Enhances accuracy. Focusing on relevant information helps the LLM remain focused and complete tasks without distractions.
- Reduces costs. Reducing irrelevant messages lowers token usage, decreasing the overall cost of API calls.

## When to compress history

History compression is performed at specific steps in the agent workflow:

- Between logical steps (subgraphs) of the agent strategy.
- When context becomes too long.

## History compression implementation

There are two main approaches to implementing history compression in your agent:

- In a strategy graph.
- In a custom node.

### History compression in a strategy graph

To compress the history in a strategy graph, you need to use the `nodeLLMCompressHistory` node.
Depending on which step you decide to perform compression, the following scenarios are available: 

* To compress the history when it becomes too long, you can define a helper function and add the `nodeLLMCompressHistory` node to your strategy graph with the following logic:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.context.AIAgentContext
    import ai.koog.agents.core.dsl.builder.forwardTo
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeExecuteTool
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    import ai.koog.agents.core.dsl.extension.nodeLLMRequest
    import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
    import ai.koog.agents.core.dsl.extension.onAssistantMessage
    import ai.koog.agents.core.dsl.extension.onToolCall
    import ai.koog.agents.core.environment.ReceivedToolResult
    -->
    ```kotlin
    // Define that the history is too long if there are more than 100 messages
    private suspend fun AIAgentContext.historyIsTooLong(): Boolean = llm.readSession { prompt.messages.size > 100 }
    
    val strategy = strategy<String, String>("execute-with-history-compression") {
        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()
    
        // Compress the LLM history and keep the current ReceivedToolResult for the next node
        val compressHistory by nodeLLMCompressHistory<ReceivedToolResult>()
    
        edge(nodeStart forwardTo callLLM)
        edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(callLLM forwardTo executeTool onToolCall { true })
    
        // Compress history after executing any tool if the history is too long 
        edge(executeTool forwardTo compressHistory onCondition { historyIsTooLong() })
        edge(compressHistory forwardTo sendToolResult)
        // Otherwise, proceed to the next LLM request
        edge(executeTool forwardTo sendToolResult onCondition { !historyIsTooLong() })
    
        edge(sendToolResult forwardTo executeTool onToolCall { true })
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
    }
    ```
    <!--- KNIT example-history-compression-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ```
    <!--- KNIT example-history-compression-java-01.java -->

In this example, the strategy checks if the history is too long after each tool call.
The history is compressed before sending the tool result back to the LLM. This prevents the context from growing during long conversations.

* To compress the history between the logical steps (subgraphs) of your strategy, you can implement your strateg as follows:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    -->
    ```kotlin
    val strategy = strategy<String, String>("execute-with-history-compression") {
        val collectInformation by subgraph<String, String> {
            // Some steps to collect the information
        }
        val compressHistory by nodeLLMCompressHistory<String>()
        val makeTheDecision by subgraph<String, String> {
            // Some steps to make the decision based on the current compressed history and collected information
        }
        
        nodeStart then collectInformation then compressHistory then makeTheDecision
    }
    ```
    <!--- KNIT example-history-compression-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ```
    <!--- KNIT example-history-compression-java-02.java -->

In this example, the history is compressed after completing the information collection phase, but before proceeding to the decision-making phase.

### History compression in a custom node

If you are implementing a custom node, you can compress history using the `replaceHistoryWithTLDR()` function as follows:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR()
    }
    ```
    <!--- KNIT example-history-compression-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR()` is a suspend Kotlin extension on AIAgentLLMWriteSession
    // and requires a coroutine context. From Java, calling suspend functions directly
    // would require passing a Continuation and coroutine machinery, which is not part of the
    // public Java API. No non-suspending Java wrapper is available for this action.
    ```
    <!--- KNIT example-history-compression-java-03.java -->

This approach gives you more flexibility to implement compression at any point in your custom node logic, based on your specific requirements.

To learn more about custom nodes, see [Custom nodes](custom-nodes.md).

## History compression strategies

You can customize the compression process by passing an optional `strategy` parameter to `nodeLLMCompressHistory(strategy=...)` or to `replaceHistoryWithTLDR(strategy=...)`.
The framework provides several built-in strategies.

### WholeHistory (Default)

The default strategy that compresses the entire history into one TLDR message that summarizes what has been achieved so far.
This strategy works well for most general use cases where you want to maintain awareness of the entire conversation context while reducing token usage.

You can use it as follows: 

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = HistoryCompressionStrategy.WholeHistory
    )
    ```
    <!--- KNIT example-history-compression-04.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `nodeLLMCompressHistory<ProcessedInput>(strategy = HistoryCompressionStrategy.WholeHistory)`
    // is a Kotlin DSL node with a reified generic. There is no Java builder to add this node
    // into a graph strategy. The HistoryCompressionStrategy type exists, but invoking the DSL
    // from Java is not supported.
    ```
    <!--- KNIT example-history-compression-java-04.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)
    }
    ```
    <!--- KNIT example-history-compression-05.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)` is a suspend
    // Kotlin extension on AIAgentLLMWriteSession and requires coroutines. No non-suspending Java
    // wrapper exists in the current Koog API to perform this operation.
    ```
    <!--- KNIT example-history-compression-java-05.java -->

### FromLastNMessages

The strategy compresses only the last `n` messages into a TLDR message and completely discards earlier messages.
This is useful when only the latest achievements of the agent (or the latest discovered facts, the latest context) are relevant for solving the problem.

You can use it as follows:

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = HistoryCompressionStrategy.FromLastNMessages(5)
    )
    ```
    <!--- KNIT example-history-compression-06.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: The Kotlin DSL node `nodeLLMCompressHistory<ProcessedInput>(
    //   strategy = HistoryCompressionStrategy.FromLastNMessages(5)
    // )` relies on reified generics and DSL builders not callable from Java.
    ```
    <!--- KNIT example-history-compression-java-06.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.FromLastNMessages(5))
    }
    ```
    <!--- KNIT example-history-compression-07.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: Calling `replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.FromLastNMessages(5))`
    // requires a suspend Kotlin context. There is no Java-accessible non-suspending API for this.
    ```
    <!--- KNIT example-history-compression-java-07.java -->

### Chunked

The strategy splits the whole message history into chunks of a fixed size and compresses each chunk independently into a TLDR message.
This is useful when you need not only the concise TLDR of what has been done so far but also want to keep track of the overall progress, and some older information might also be important.

You can use it as follows:

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = HistoryCompressionStrategy.Chunked(10)
    )
    ```
    <!--- KNIT example-history-compression-08.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: The Kotlin graph DSL and `nodeLLMCompressHistory<ProcessedInput>(
    //   strategy = HistoryCompressionStrategy.Chunked(10)
    // )` are not available from Java due to reified generics and lack of a Java graph builder API.
    ```
    <!--- KNIT example-history-compression-java-08.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.Chunked(10))
    }
    ```
    <!--- KNIT example-history-compression-09.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.Chunked(10))` is a suspend
    // Kotlin API; there is no current non-suspending Java entry point to perform this action inside a node.
    ```
    <!--- KNIT example-history-compression-java-09.java -->

### RetrieveFactsFromHistory

The strategy searches for specific facts relevant to the provided list of concepts in the history and retrieves them.
It changes the whole history to just these facts and leaves them as context for future LLM requests.
This is useful when you have an idea of what exact facts will be relevant for the LLM to perform better on the task.

You can use it as follows:

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
    import ai.koog.agents.memory.model.Concept
    import ai.koog.agents.memory.model.FactType
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = RetrieveFactsFromHistory(
            Concept(
                keyword = "user_preferences",
                // Description to the LLM -- what specifically to search for
                description = "User's preferences for the recommendation system, including the preferred conversation style, theme in the application, etc.",
                // LLM would search for multiple relevant facts related to this concept:
                factType = FactType.MULTIPLE
            ),
            Concept(
                keyword = "product_details",
                // Description to the LLM -- what specifically to search for
                description = "Brief details about products in the catalog the user has been checking",
                // LLM would search for multiple relevant facts related to this concept:
                factType = FactType.MULTIPLE
            ),
            Concept(
                keyword = "issue_solved",
                // Description to the LLM -- what specifically to search for
                description = "Was the initial user's issue resolved?",
                // LLM would search for a single answer to the question:
                factType = FactType.SINGLE
            )
        )
    )
    ```
    <!--- KNIT example-history-compression-10.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `nodeLLMCompressHistory<ProcessedInput>(strategy = new RetrieveFactsFromHistory(...))`
    // is part of the Kotlin graph DSL with reified generics; Java cannot call it and there is no
    // equivalent Java graph builder API to inject this node.
    ```
    <!--- KNIT example-history-compression-java-10.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
    import ai.koog.agents.memory.model.Concept
    import ai.koog.agents.memory.model.FactType
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(
            strategy = RetrieveFactsFromHistory(
                Concept(
                    keyword = "user_preferences", 
                    // Description to the LLM -- what specifically to search for
                    description = "User's preferences for the recommendation system, including the preferred conversation style, theme in the application, etc.",
                    // LLM would search for multiple relevant facts related to this concept:
                    factType = FactType.MULTIPLE
                ),
                Concept(
                    keyword = "product_details",
                    // Description to the LLM -- what specifically to search for
                    description = "Brief details about products in the catalog the user has been checking",
                    // LLM would search for multiple relevant facts related to this concept:
                    factType = FactType.MULTIPLE
                ),
                Concept(
                    keyword = "issue_solved",
                    // Description to the LLM -- what specifically to search for
                    description = "Was the initial user's issue resolved?",
                    // LLM would search for a single answer to the question:
                    factType = FactType.SINGLE
                )
            )
        )
    }
    ```
    <!--- KNIT example-history-compression-11.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR(strategy = new RetrieveFactsFromHistory(...))` is a suspend
    // Kotlin extension requiring coroutines; no Java wrapper is available.
    ```
    <!--- KNIT example-history-compression-java-11.java -->

## Custom history compression strategy implementation

You can create your own history compression strategy by extending the `HistoryCompressionStrategy` abstract class and implementing the `compress` method.

Here is an example:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.prompt.message.Message
    -->
    ```kotlin
    class MyCustomCompressionStrategy : HistoryCompressionStrategy() {
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            // 1. Process the current history in llmSession.prompt.messages
            // 2. Create new compressed messages
            // 3. Update the prompt with the compressed messages
    
            // Save original messages to preserve them
            val originalMessages = llmSession.prompt.messages
            
            // Example implementation:
            val importantMessages = llmSession.prompt.messages.filter {
                // Your custom filtering logic
                it.content.contains("important")
            }.filterIsInstance<Message.Response>()
            
            // Note: you can also make LLM requests using the `llmSession` and ask the LLM to do some job for you using, for example, `llmSession.requestLLMWithoutTools()`
            // Or you can change the current model: `llmSession.model = AnthropicModels.Opus_4_6` and ask some other LLM model -- but don't forget to change it back after
    
            // Compose the prompt with the filtered messages
            val compressedMessages = composeMessageHistory(
                originalMessages,
                importantMessages,
                memoryMessages
            )
        }
    }
    ```
    <!--- KNIT example-history-compression-12.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: Custom strategies must extend Kotlin `HistoryCompressionStrategy` and override the
    // suspend method `compress(AIAgentLLMWriteSession, List<Message>)`. Java cannot implement
    // suspend functions directly. A Kotlin shim would be required; no direct Java implementation is possible.
    ```
    <!--- KNIT example-history-compression-java-12.java -->

In this example, the custom strategy filters messages that contain the word "important" and keeps only those in the compressed history.

Then you can use it as follows:

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    import ai.koog.agents.example.exampleHistoryCompression12.MyCustomCompressionStrategy
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = MyCustomCompressionStrategy()
    )
    ```
    <!--- KNIT example-history-compression-13.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: Even with a custom strategy, adding it via `nodeLLMCompressHistory<ProcessedInput>(
    //   strategy = new MyCustomCompressionStrategy()
    // )` is Kotlin DSL-only with reified generics; no Java graph builder is provided.
    ```
    <!--- KNIT example-history-compression-java-13.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    import ai.koog.agents.example.exampleHistoryCompression12.MyCustomCompressionStrategy
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(strategy = MyCustomCompressionStrategy())
    }
    ```
    <!--- KNIT example-history-compression-14.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR(strategy = new MyCustomCompressionStrategy())` is a suspend
    // Kotlin API; not callable from Java without coroutine interop glue, which the public API lacks.
    ```
    <!--- KNIT example-history-compression-java-14.java -->

##  Memory preservation during compression

All history compression methods have the `preserveMemory` parameter that determines whether memory-related messages should be preserved during compression.
These are messages that contain facts retrieved from memory or indicate that the memory feature is not enabled.

You can use the `preserveMemory` parameter as follows:

* In a strategy graph:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val compressHistory by nodeLLMCompressHistory<ProcessedInput>(
        strategy = HistoryCompressionStrategy.WholeHistory,
        preserveMemory = true
    )
    ```
    <!--- KNIT example-history-compression-15.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: Passing `preserveMemory = true` to `nodeLLMCompressHistory<ProcessedInput>(...)`
    // still relies on the Kotlin graph DSL with reified generics; Java cannot use this API directly.
    ```
    <!--- KNIT example-history-compression-java-15.java -->

* In a custom node:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.core.dsl.builder.subgraph
    import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
    import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
    typealias ProcessedInput = String
    val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    llm.writeSession {
        replaceHistoryWithTLDR(
            strategy = HistoryCompressionStrategy.WholeHistory,
            preserveMemory = true
        )
    }
    ```
    <!--- KNIT example-history-compression-16.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // FAILED: `replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory, preserveMemory = true)`
    // is a suspend Kotlin extension; there is no Java-accessible non-suspending wrapper in Koog.
    ```
    <!--- KNIT example-history-compression-java-16.java -->
