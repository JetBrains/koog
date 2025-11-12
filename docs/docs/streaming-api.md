# Streaming API


## Introduction

Koog’s **Streaming API** lets you consume **LLM output incrementally** as a `Flow<StreamFrame>`. Instead of waiting for a full response, your code can:

- render assistant text as it arrives,
- detect **tool calls** live and act on them,
- know when a stream **ends** and why.

The stream carries **typed frames**:

- `StreamFrame.Append(text: String)` — incremental assistant text
- `StreamFrame.ToolCall(id: String?, name: String, content: String)` — tool invocation (combined safely)
- `StreamFrame.End(finishReason: String?)` — end-of-stream marker

Helpers are provided to extract plain text, convert frames to `Message.Response` objects, and safely **combine chunked tool calls**.

---

## Streaming API overview

With streaming you can:

- Process data as it arrives (improves UI responsiveness)
- Parse structured info on the fly (Markdown/JSON/etc.)
- Emit objects as they complete
- Trigger tools in real time

You can operate either on the **frames** themselves or on **plain text** derived from frames.

---
## Usage

### Working with frames directly

This is the most general approach: react to each frame kind.

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
            is StreamFrame.Append -> print(frame.text)
            is StreamFrame.ToolCall -> {
                println("\n🔧 Tool call: ${frame.name} args=${frame.content}")
                // Optionally parse lazily:
                // val json = frame.contentJson
            }
            is StreamFrame.End -> println("\n[END] reason=${frame.finishReason}")
        }
    }
}
```
<!--- KNIT example-streaming-api-01.kt -->

It is important to note that you can parse the output by working directly with a raw string stream.
This approach gives you more flexibility and control over the parsing process.

Here is a raw string stream with the Markdown definition of the output structure:

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

### Working with a raw text stream (derived)

If you have existing streaming parsers that expect `Flow<String>`, 
derive text chunks via `filterTextOnly()` or collect them with `collectText()`.

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

### Listening to stream events in event handlers

You can listen to stream events in [agent event handlers](agent-event-handlers.md).

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
        println("\n🔧 Using ${context.tool.name} with ${context.toolArgs}... ")
    }
    onLLMStreamingFrameReceived { context ->
        (context.streamFrame as? StreamFrame.Append)?.let { frame ->
            print(frame.text)
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

### Converting frames to `Message.Response`

You can transform a collected list of frames to standard message objects:
- `toAssistantMessageOrNull()`
- `toToolCallMessages()`
- `toMessageResponses()`

---

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

#### 2. Define the Markdown structure

Create a definition that specifies how your data should be structured in Markdown with the
`MarkdownStructureDefinition` class:

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

#### 3. Create a parser for your data structure

The `markdownStreamingParser` provides several handlers for different Markdown elements:

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

Using the defined handlers, you can implement a function that parses the Markdown stream and emits your data objects 
with the `markdownStreamingParser` function.

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

#### 4. Use the parser in your agent strategy

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

### Advanced usage: Streaming with tools

You can also use the Streaming API with tools to process data as it arrives. 
The following sections provide a brief step-by-step guide on how to define a tool and use it with streaming data.

### 1. Define a tool for your data structure

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

class BookTool(): SimpleTool<Book>() {
    
    companion object { const val NAME = "book" }

    override suspend fun doExecute(args: Book): String {
        println("${args.title} by ${args.author}:\n ${args.description}")
        return "Done"
    }

    override val argsSerializer: KSerializer<Book>
        get() = Book.serializer()

    override val name: String = NAME
    override val description: String = "A tool to parse book information from Markdown"
}
```
<!--- KNIT example-streaming-api-08.kt -->

### 2. Use the tool with streaming data

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.example.exampleStreamingApi04.markdownBookDefinition
import ai.koog.agents.example.exampleStreamingApi06.parseMarkdownStreamToBooks
import ai.koog.agents.example.exampleStreamingApi08.BookTool

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

### 3. Register the tool in your agent configuration

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.exampleComplexWorkflowAgents01.token
import ai.koog.agents.example.exampleComplexWorkflowAgents06.agentStrategy
import ai.koog.agents.example.exampleComplexWorkflowAgents07.agentConfig
import ai.koog.agents.example.exampleStreamingApi08.BookTool
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

-->
```kotlin
val toolRegistry = ToolRegistry {
   tool(BookTool())
}

val runner = AIAgent(
   promptExecutor = simpleOpenAIExecutor(token),
   toolRegistry = toolRegistry,
   strategy = agentStrategy,
   agentConfig = agentConfig
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
