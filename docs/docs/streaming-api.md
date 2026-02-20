# Streaming API

Koog’s **Streaming API** lets you consume **LLM output incrementally** as a `Flow<StreamFrame>`. Instead of waiting for a full response, your code can:

- render assistant text as it arrives,
- detect **tool calls** live and act on them,
- know when a stream **ends** and why.

The stream carries **typed frames** organized into two categories:

**Delta frames** (incremental/partial content):
- `StreamFrame.TextDelta(text: String, index: Int?)` — incremental assistant text
- `StreamFrame.ReasoningDelta(text: String?, summary: String?, index: Int?)` — incremental reasoning text and summary
- `StreamFrame.ToolCallDelta(id: String?, name: String?, content: String?, index: Int?)` — partial tool invocation

**Complete frames** (full content):
- `StreamFrame.TextComplete(text: String)` — complete assistant text
- `StreamFrame.ReasoningComplete(text: List<String>, summary: List<String>?)` — complete reasoning with optional summary
- `StreamFrame.ToolCallComplete(id: String?, name: String, content: String)` — complete tool invocation

**End marker**:
- `StreamFrame.End(finishReason: String?)` — end-of-stream marker

Helpers are provided to extract plain text, convert frames to `Message.Response` objects, and safely **combine chunked tool calls**.

## API overview

With streaming you can:

- Process data as it arrives (improves UI responsiveness)
- Parse structured info on the fly (Markdown/JSON/etc.)
- Emit objects as they complete
- Trigger tools in real time
- Access model reasoning in real-time (for supported models)

You can operate either on the **frames** themselves or on **plain text** derived from frames.

### Delta vs Complete Frames

The streaming API distinguishes between two types of frames:

- **Delta frames** (`DeltaFrame`) — Incremental/partial content that arrives in chunks. These are ideal for real-time display as content streams in. Examples: `TextDelta`, `ReasoningDelta`, `ToolCallDelta`.

- **Complete frames** (`CompleteFrame`) — Full content emitted after all deltas for that content type have been received. These are useful for final processing and conversion to `Message.Response` objects. Examples: `TextComplete`, `ReasoningComplete`, `ToolCallComplete`.

Typically, you'll work with delta frames for UI updates and complete frames for extracting final structured data.

---
## Usage

### Working with frames directly

This is the most general approach: react to each frame kind.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.prompt.streaming.StreamFrame
    
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    llm.writeSession {
        appendPrompt { user("Tell me a joke, then call a tool with JSON args.") }
    
        val stream = requestLLMStreaming() // Flow<StreamFrame>
    
        stream.collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> print(frame.text)
                is StreamFrame.ReasoningDelta -> print("[Reasoning] text=${frame.text} summary=${frame.summary}")
                is StreamFrame.ToolCallComplete -> {
                    println("\n🔧 Tool call: ${frame.name} args=${frame.content}")
                    // Optionally parse lazily:
                    // val json = frame.contentJson
                }
                is StreamFrame.End -> println("\n[END] reason=${frame.finishReason}")
                else -> {} // Handle other frame types (TextComplete, ToolCallDelta, etc.)
            }
        }
    }
    ```
    <!--- KNIT example-streaming-api-01.kt -->

=== "Java"

    ```java
    // Pseudo-usage inside a Java-accessible context
    // FAILED: The requestLLMStreaming() call is available only inside the Kotlin DSL llm.writeSession { ... }
    // and returns a Kotlin Flow<StreamFrame>. There is no Java NonSuspend wrapper for streaming yet,
    // and collecting Kotlin Flow from Java requires coroutines and Continuation, which are not exposed by a Java API here.
    // The frame-handling logic below illustrates the type switch that would be used once a Java streaming API is available.

    StreamFrame frame = null; // placeholder
    if (frame instanceof StreamFrame.Append) {
        System.out.print(((StreamFrame.Append) frame).getText());
    } else if (frame instanceof StreamFrame.ToolCall) {
        StreamFrame.ToolCall tc = (StreamFrame.ToolCall) frame;
        System.out.println("\n\uD83D\uDD27 Tool call: " + tc.getName() + " args=" + tc.getContent());
    } else if (frame instanceof StreamFrame.End) {
        System.out.println("\n[END] reason=" + ((StreamFrame.End) frame).getFinishReason());
    }
    ```

It is important to note that you can parse the output by working directly with a raw string stream.
This approach gives you more flexibility and control over the parsing process.

Here is a raw string stream with the Markdown definition of the output structure:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    fun markdownBookDefinition(): MarkdownStructureDefinition {
        return MarkdownStructureDefinition("name", schema = { /*...*/ })
    }

    val mdDefinition = markdownBookDefinition()

    llm.writeSession {
        val stream = requestLLMStreaming(mdDefinition)
        // Access the raw string chunks directly
        stream.collect { chunk ->
            // Process each chunk of text as it arrives
            println("Received chunk: $chunk") // The chunks together will be structured as a text following the mdDefinition schema
        }
    }
    ```
    <!--- KNIT example-streaming-api-02.kt -->

=== "Java"

    ```java
    // FAILED: markdownBookDefinition() and requestLLMStreaming(definition) are part of the Kotlin DSL used within
    // llm.writeSession { ... }. There is currently no Java builder/wrapper to start an LLM streaming session with a
    // MarkdownStructureDefinition and to collect Kotlin Flow<String> chunks from Java without coroutines interop.
    MarkdownStructureDefinition mdDefinition = new MarkdownStructureDefinition("name", schema -> {
        // ... schema definition ...
        return null;
    }, examples -> null);
    // String chunks streaming and collection would require a Java-facing streaming client which is not exposed.
    ```

### Working with reasoning frames

Models that support reasoning (such as Claude Sonnet 4.5 or GPT-o1) emit reasoning frames during streaming. You can access both the reasoning process and its summary:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.streaming.StreamFrame

val strategy = strategy<String, String>("strategy_name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    appendPrompt { user("Solve this complex problem: ...") }

    val stream = requestLLMStreaming()
    val reasoningSteps = mutableListOf<String>()
    val summarySteps = mutableListOf<String>()

    stream.collect { frame ->
        when (frame) {
            is StreamFrame.ReasoningDelta -> {
                frame.text?.let { 
                    reasoningSteps.add(it)
                    print(frame.text) // Display reasoning as it arrives
                }
                frame.summary?.let {
                    summarySteps.add(it)
                    print(frame.summary) // Display reasoning summary as it arrives
                }
            }
            is StreamFrame.ReasoningComplete -> {
                // Access complete reasoning
                println("\nComplete reasoning: ${frame.text.joinToString("")}")
                println("Summary: ${frame.summary?.joinToString("") ?: "N/A"}")
            }
            is StreamFrame.TextDelta -> print(frame.text)
            is StreamFrame.End -> println("\n[END]")
            else -> {}
        }
    }
}
```
<!--- KNIT example-streaming-api-reasoning-01.kt -->

### Working with a raw text stream (derived)

If you have existing streaming parsers that expect `Flow<String>`,
derive text chunks via `filterTextOnly()` or collect them with `collectText()`.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.prompt.streaming.filterTextOnly
    import ai.koog.prompt.streaming.collectText
    val strategy = strategy<String, String>("strategy_name") {
        val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    llm.writeSession {
        val frames = requestLLMStreaming()

        // Stream text chunks as they come:
        frames.filterTextOnly().collect { chunk -> print(chunk) }

        // Or, gather all text into one String after End:
        val fullText = frames.collectText()
        println("\n---\n$fullText")
    }
    ```
    <!--- KNIT example-streaming-api-02-01.kt -->

=== "Java"

    ```java
    // FAILED: filterTextOnly() and collectText() are Kotlin extension functions on Flow<StreamFrame> and Flow<String>,
    // not directly accessible from Java without using the generated *Kt classes and coroutines Continuation machinery.
    // A Java-friendly streaming API is required to replicate this example.
    ```

### Listening to stream events in event handlers

You can listen to stream events in [agent event handlers](agent-event-handlers.md).

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.agent.GraphAIAgent
    import ai.koog.agents.features.eventHandler.feature.handleEvents
    import ai.koog.prompt.streaming.StreamFrame
    import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
    
    fun GraphAIAgent.FeatureContext.installStreamingApi() {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    handleEvents {
        onToolCallStarting { context ->
            println("\n🔧 Using ${context.toolName} with ${context.toolArgs}... ")
        }
        onLLMStreamingFrameReceived { context ->
            when (val frame = context.streamFrame) {
                is StreamFrame.TextDelta -> print(frame.text)
                is StreamFrame.ReasoningDelta -> print("[Reasoning] text=${frame.text} summary=${frame.summary}")
                else -> {} // Handle other frame types if needed
            }
        }
        onLLMStreamingFailed { context ->
            println("❌ Error: ${context.error}")
        }
        onLLMStreamingCompleted {
            println("🏁 Done")
        }
    }
    ```
    <!--- KNIT example-streaming-api-02-02.kt -->

=== "Java"

    ```java
    // FAILED: handleEvents { ... } is a Kotlin DSL for agent features. Java interop would require a Feature installation
    // method with functional interfaces or builders, which is not exposed for streaming event hooks in the current API.
    // If/when a Java Feature API is provided, equivalent handlers can be installed via .install(Feature, cfg -> { ... }).
    ```

### Converting frames to `Message.Response`

You can transform a collected list of frames to standard message objects:
- `toAssistantMessageOrNull()` — extracts `Message.Assistant` from text frames
- `toReasoningMessageOrNull()` — extracts `Message.Reasoning` from reasoning frames
- `toToolCallMessages()` — extracts `Message.Tool.Call` from tool call frames
- `toMessageResponses()` — converts all complete frames to their corresponding `Message.Response` objects

## Examples

### Structured data while streaming (Markdown example)

Although it is possible to work with a raw string stream,
it is often more convenient to work with [structured data](structured-output.md).

The structured data approach includes the following key components:

1. **MarkdownStructureDefinition**: a class to help you define the schema and examples for structured data in
   Markdown format.
2. **markdownStreamingParser**: a function to create a parser that processes a stream of Markdown chunks and emits
   events.

The sections below provide step-by-step instructions and code samples related to processing a stream of structured data. 

#### 1. Define your data structure

First, define a data class to represent your structured data:

=== "Kotlin"

    <!--- INCLUDE
    import kotlinx.serialization.Serializable
    -->
    ```kotlin
    @Serializable
    data class Book(
        val title: String,
        val author: String,
        val description: String
    )
    ```
    <!--- KNIT example-streaming-api-03.kt -->

=== "Java"

    ```java
    // A simple Java POJO equivalent to the Kotlin @Serializable data class.
    public class Book {
        public final String title;
        public final String author;
        public final String description;

        public Book(String title, String author, String description) {
            this.title = title;
            this.author = author;
            this.description = description;
        }
    }
    ```

#### 2. Define the Markdown structure

Create a definition that specifies how your data should be structured in Markdown with the
`MarkdownStructureDefinition` class:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.markdown.markdown
    import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
    -->
    ```kotlin
    fun markdownBookDefinition(): MarkdownStructureDefinition {
        return MarkdownStructureDefinition("bookList", schema = {
            markdown {
                header(1, "title")
                bulleted {
                    item("author")
                    item("description")
                }
            }
        }, examples = {
            markdown {
                header(1, "The Great Gatsby")
                bulleted {
                    item("F. Scott Fitzgerald")
                    item("A novel set in the Jazz Age that tells the story of Jay Gatsby's unrequited love for Daisy Buchanan.")
                }
            }
        })
    }
    ```
    <!--- KNIT example-streaming-api-04.kt -->

=== "Java"

    ```java
    // FAILED: The Kotlin DSL builders (markdown { header(...); bulleted { ... } }) are Kotlin-only.
    // Java can construct MarkdownStructureDefinition but cannot use the Kotlin lambda DSL for schema/examples directly.
    MarkdownStructureDefinition def = new MarkdownStructureDefinition("bookList", schema -> null, examples -> null);
    ```

#### 3. Create a parser for your data structure

The `markdownStreamingParser` provides several handlers for different Markdown elements:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.exampleStreamingApi03.Book
    import ai.koog.prompt.structure.markdown.markdownStreamingParser
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.flow
    fun parseMarkdownStreamToBooks(markdownStream: Flow<String>): Flow<Book> {
        return flow {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    markdownStreamingParser {
        // Handle level 1 headings (level ranges from 1 to 6)
        onHeader(1) { headerText -> }
        // Handle bullet points
        onBullet { bulletText -> }
        // Handle code blocks
        onCodeBlock { codeBlockContent -> }
        // Handle lines matching a regex pattern
        onLineMatching(Regex("pattern")) { line -> }
        // Handle the end of the stream
        onFinishStream { remainingText -> }
    }
    ```
    <!--- KNIT example-streaming-api-05.kt -->

=== "Java"

    ```java
    // FAILED: markdownStreamingParser { ... } is a Kotlin DSL that relies on Kotlin function types.
    // There is no Java-oriented builder for registering handlers at the time of writing.
    ```

Using the defined handlers, you can implement a function that parses the Markdown stream and emits your data objects 
with the `markdownStreamingParser` function.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.exampleStreamingApi03.Book
    import ai.koog.prompt.structure.markdown.markdownStreamingParser
    import ai.koog.prompt.streaming.StreamFrame
    import ai.koog.prompt.streaming.filterTextOnly
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.flow
    -->
    ```kotlin
    fun parseMarkdownStreamToBooks(markdownStream: Flow<StreamFrame>): Flow<Book> {
       return flow {
          markdownStreamingParser {
             var currentBookTitle = ""
             val bulletPoints = mutableListOf<String>()

             // Handle the event of receiving the Markdown header in the response stream
             onHeader(1) { headerText ->
                // If there was a previous book, emit it
                if (currentBookTitle.isNotEmpty() && bulletPoints.isNotEmpty()) {
                   val author = bulletPoints.getOrNull(0) ?: ""
                   val description = bulletPoints.getOrNull(1) ?: ""
                   emit(Book(currentBookTitle, author, description))
                }

                currentBookTitle = headerText
                bulletPoints.clear()
             }

             // Handle the event of receiving the Markdown bullets list in the response stream
             onBullet { bulletText ->
                bulletPoints.add(bulletText)
             }

             // Handle the end of the response stream
             onFinishStream {
                // Emit the last book, if present
                if (currentBookTitle.isNotEmpty() && bulletPoints.isNotEmpty()) {
                   val author = bulletPoints.getOrNull(0) ?: ""
                   val description = bulletPoints.getOrNull(1) ?: ""
                   emit(Book(currentBookTitle, author, description))
                }
             }
          }.parseStream(markdownStream.filterTextOnly())
       }
    }
    ```
    <!--- KNIT example-streaming-api-06.kt -->

=== "Java"

    ```java
    // FAILED: This implementation uses Kotlin Flow and the markdownStreamingParser DSL, both suspend/DSL-based.
    // Java cannot call these without coroutine interop and Java builders. A future NonSuspend parser or Java builder
    // would be needed to provide equivalent functionality.
    ```

#### 4. Use the parser in your agent strategy

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.forwardTo
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.example.exampleStreamingApi03.Book
    import ai.koog.agents.example.exampleStreamingApi04.markdownBookDefinition
    import ai.koog.agents.example.exampleStreamingApi06.parseMarkdownStreamToBooks
    -->
    ```kotlin
    val agentStrategy = strategy<String, List<Book>>("library-assistant") {
       // Describe the node containing the output stream parsing
       val getMdOutput by node<String, List<Book>> { booksDescription ->
          val books = mutableListOf<Book>()
          val mdDefinition = markdownBookDefinition()

          llm.writeSession {
             appendPrompt { user(booksDescription) }
             // Initiate the response stream in the form of the definition `mdDefinition`
             val markdownStream = requestLLMStreaming(mdDefinition)
             // Call the parser with the result of the response stream and perform actions with the result
             parseMarkdownStreamToBooks(markdownStream).collect { book ->
                books.add(book)
                println("Parsed Book: ${book.title} by ${book.author}")
             }
          }

          books
       }
       // Describe the agent's graph making sure the node is accessible
       edge(nodeStart forwardTo getMdOutput)
       edge(getMdOutput forwardTo nodeFinish)
    }
    ```
    <!--- KNIT example-streaming-api-07.kt -->

=== "Java"

    ```java
    // FAILED: The strategy { ... } and llm.writeSession { ... } constructs are Kotlin DSLs.
    // While Koog provides AIAgent.builder() for Java agents, there is no direct Java equivalent to define
    // a streaming node with requestLLMStreaming(mdDefinition) and collect Kotlin Flow results without coroutine interop.
    ```

### Advanced usage: Streaming with tools

You can also use the Streaming API with tools to process data as it arrives. 
The following sections provide a brief step-by-step guide on how to define a tool and use it with streaming data.

### 1. Define a tool for your data structure

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.SimpleTool
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.example.exampleStreamingApi03.Book
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
    
    class BookTool(): SimpleTool<Book>(
        argsSerializer = Book.serializer(),
        name = NAME,
        description = "A tool to parse book information from Markdown"
    ) {
    
        companion object { const val NAME = "book" }
    
        override suspend fun execute(args: Book): String {
            println("${args.title} by ${args.author}:\n ${args.description}")
            return "Done"
        }
    }
    ```
    <!--- KNIT example-streaming-api-08.kt -->

=== "Java"

    ```java
    // Java equivalent POJO (reused from earlier):
    public class Book {
        public final String title;
        public final String author;
        public final String description;
        public Book(String title, String author, String description) {
            this.title = title;
            this.author = author;
            this.description = description;
        }
    }

    // FAILED: Extending Kotlin SimpleTool<Book> from Java and overriding a suspend execute(args: Book) is not possible.
    // Java cannot implement a suspend function; a Java-facing Tool API (non-suspending) would be required.
    ```

### 2. Use the tool with streaming data

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.forwardTo
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.example.exampleStreamingApi04.markdownBookDefinition
    import ai.koog.agents.example.exampleStreamingApi06.parseMarkdownStreamToBooks
    import ai.koog.agents.example.exampleStreamingApi08.BookTool
    import ai.koog.agents.core.agent.session.callToolRaw
    -->
    ```kotlin
    val agentStrategy = strategy<String, Unit>("library-assistant") {
       val getMdOutput by node<String, Unit> { input ->
          val mdDefinition = markdownBookDefinition()

          llm.writeSession {
             appendPrompt { user(input) }
             val markdownStream = requestLLMStreaming(mdDefinition)

             parseMarkdownStreamToBooks(markdownStream).collect { book ->
                callToolRaw(BookTool.NAME, book)
                /* Other possible options:
                    callTool(BookTool::class, book)
                    callTool<BookTool>(book)
                    findTool(BookTool::class).execute(book)
                */
             }

             // We can make parallel tool calls
             parseMarkdownStreamToBooks(markdownStream).toParallelToolCallsRaw(toolClass=BookTool::class).collect {
                println("Tool call result: $it")
             }
          }
       }

       edge(nodeStart forwardTo getMdOutput)
       edge(getMdOutput forwardTo nodeFinish)
     }
    ```
    <!--- KNIT example-streaming-api-09.kt -->

=== "Java"

    ```java
    // FAILED: The calls to callToolRaw / toParallelToolCallsRaw and the surrounding strategy/llm.writeSession are Kotlin DSL
    // and suspend-based APIs. A Java NonSuspend* tool invocation or builder-based strategy definition would be needed.
    ```

### 3. Register the tool in your agent configuration

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.exampleStreamingApi08.BookTool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

-->
```kotlin
val toolRegistry = ToolRegistry {
    tool(BookTool())
}

val runner = AIAgent(
    promptExecutor = simpleOpenAIExecutor("OPENAI_API_KEY"),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry
)
```
<!--- KNIT example-streaming-api-10.kt -->

## Best practices

1. **Define clear structures**: create clear and unambiguous markdown structures for your data.

2. **Provide good examples**: include comprehensive examples in your `MarkdownStructureDefinition` to guide the LLM.

3. **Handle incomplete data**: always check for null or empty values when parsing data from the stream.

4. **Clean up resources**: use the `onFinishStream` handler to clean up resources and process any remaining data.

5. **Handle errors**: implement proper error handling for malformed Markdown or unexpected data.

6. **Testing**: test your parser with various input scenarios, including partial chunks and malformed input.

7. **Parallel processing**: for independent data items, consider using parallel tool calls for better performance.
