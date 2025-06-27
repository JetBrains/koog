# Streaming API

## Introduction

The Streaming API in the Koog framework lets you process structured data from Large Language Models 
(LLMs) as it arrives, rather than waiting for the entire response.
This page explains how to use the Streaming API to efficiently handle structured data in Markdown format.

## Streaming API overview

The Streaming API enables real-time processing of structured data from LLM responses. Instead of waiting for the
complete response, you can:

- Process data as it arrives in chunks
- Parse structured information on the fly
- Emit structured objects as they are completed
- Handle these objects immediately (collect them or pass to tools)

This approach is particularly useful as it provides the following benefits:

- Improving responsiveness in user interfaces
- Processing large responses efficiently
- Implementing real-time data processing pipelines

The Streaming API allows parsing the output as *structured data* from the .md format or as a set of *plain text*
chunks.

## Working with a raw string stream

It is important to note that you can parse the output by working directly with a raw string stream.
This approach gives you more flexibility and control over the parsing process.

Here is a raw string stream with the Markdown definition of the output structure:

```kotlin
fun markdownBookDefinition(): MarkdownStructuredDataDefinition {
    return MarkdownStructuredDataDefinition("name", schema = { /*...*/ })
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

This is an example of a raw string stream without the definition:

```kotlin
llm.writeSession {
    val stream = requestLLMStreaming()
    // Access the raw string chunks directly
    stream.collect { chunk ->
        // Process each chunk of text as it arrives
        println("Received chunk: $chunk") // The chunks will not be structured in a specific way
    }
}
```

## Working with a stream of structured data

Although it is possible to work with a raw string stream,
it is often more convenient to work with [structured data](structured-data.md).

The structured data approach includes the following key components:

1. **MarkdownStructuredDataDefinition**: a class to help you define the schema and examples for structured data in
   Markdown format.
2. **markdownStreamingParser**: a function to create a parser that processes a stream of Markdown chunks and emits
   events.

The sections below provide step-by-step instructions and code samples related to processing a stream of structured data. 

### 1. Define your data structure

First, define a data class to represent your structured data:

```kotlin
@Serializable
data class Book(
    val bookName: String,
    val author: String,
    val description: String
)
```

### 2. Define the Markdown structure

Create a definition that specifies how your data should be structured in Markdown with the
`MarkdownStructuredDataDefinition` class:

```kotlin
fun markdownBookDefinition(): MarkdownStructuredDataDefinition {
    return MarkdownStructuredDataDefinition("bookList", schema = {
        markdown {
            header(1, "bookName")
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

### 3. Create a parser for your data structure

The `markdownStreamingParser` provides several handlers for different Markdown elements:

```kotlin
markdownStreamingParser {
    // Handle level 1 headings
    // The heading level can be from 1 to 6
    onHeader(1) { headerText ->
        // Process heading text
    }

    // Handle bullet points
    onBullet { bulletText ->
        // Process bullet text
    }

    // Handle code blocks
    onCodeBlock { codeBlockContent ->
        // Process code block content
    }

    // Handle lines matching a regex pattern
    onLineMatching(Regex("pattern")) { line ->
        // Process matching lines
    }

    // Handle the end of the stream
    onFinishStream { remainingText ->
        // Process any remaining text or perform cleanup
    }
}
```

Using the defined handlers, you can implement a function that parses the Markdown stream and emits your data objects 
with the `markdownStreamingParser` function.

```kotlin
fun parseMarkdownStreamToBooks(markdownStream: Flow<String>): Flow<Book> {
    return flow {
        markdownStreamingParser {
            var currentBookName = ""
            val bulletPoints = mutableListOf<String>()

            // Handle the event of receiving the Markdown header in the response stream
            onHeader(1) { headerText ->
                // If there was a previous book, emit it
                if (currentBookName.isNotEmpty() && bulletPoints.isNotEmpty()) {
                    val author = bulletPoints.getOrNull(0) ?: ""
                    val description = bulletPoints.getOrNull(1) ?: ""
                    emit(Book(currentBookName, author, description))
                }

                currentBookName = headerText
                bulletPoints.clear()
            }

            // Handle the event of receiving the Markdown bullets list in the response stream
            onBullet { bulletText ->
                bulletPoints.add(bulletText)
            }

            // Handle the end of the response stream
            onFinishStream {
                // Emit the last book, if present
                if (currentBookName.isNotEmpty() && bulletPoints.isNotEmpty()) {
                    val author = bulletPoints.getOrNull(0) ?: ""
                    val description = bulletPoints.getOrNull(1) ?: ""
                    emit(Book(currentBookName, author, description))
                }
            }
        }.parseStream(markdownStream)
    }
}
```

### 4. Use the parser in your agent strategy

```kotlin
val agentStrategy = strategy("library-assistant") {
     // Describe the node containing the output stream parsing
     val getMdOutput by node<String, String> { input ->
         val books = mutableListOf<Book>()
         val mdDefinition = markdownBookDefinition()

         llm.writeSession {
             updatePrompt { user(input) }
             // Initiate the response stream in the form of the definition `mdDefinition`
             val markdownStream = requestLLMStreaming(mdDefinition)
             // Call the parser with the result of the response stream and perform actions with the result
             parseMarkdownStreamToBooks(markdownStream).collect { book ->
                 books.add(book)
                 println("Parsed Book: ${book.bookName} by ${book.author}")
             }
         }
         // A custom function for output formatting
         formatOutput(books)
     }
     // Describe the agent's graph making sure the node is accessible
     edge(nodeStart forwardTo getMdOutput)
     edge(getMdOutput forwardTo nodeFinish)
 }
```

## Advanced usage: Streaming with tools

You can also use the Streaming API with tools to process data as it arrives. The following sections provide a brief
step-by-step guide on how to define a tool and use it with streaming data.

### 1. Define a tool for your data structure

```kotlin
class BookTool(): SimpleTool<Book>() {
    companion object {
        const val NAME = "book"
    }

    override suspend fun doExecute(args: Book): String {
        println("${args.bookName} by ${args.author}:\n ${args.description}")
        return "Done"
    }

    override val argsSerializer: KSerializer<Book>
        get() = Book.serializer()
    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = NAME,
            description = "A tool to parse book information from Markdown",
            requiredParameters = listOf(),
            optionalParameters = listOf()
        )
}
```

### 2. Use the tool with streaming data

```kotlin
val agentStrategy = strategy("library-assistant") {
     val getMdOutput by node<String, String> { input ->
         val mdDefinition = markdownBookDefinition()

         llm.writeSession {
             updatePrompt { user(input) }
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
             parseMarkdownStreamToBooks(markdownStream).toParallelToolCallsRaw(BookTool::class).collect()
         }
         ""
     }

     edge(nodeStart forwardTo getMdOutput)
     edge(getMdOutput forwardTo nodeFinish)
 }
```

### 3. Register the tool in your agent configuration

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

## Best practices

1. **Define clear structures**: create clear and unambiguous markdown structures for your data.

2. **Provide good examples**: include comprehensive examples in your `MarkdownStructuredDataDefinition` to guide the LLM.

3. **Handle incomplete data**: always check for null or empty values when parsing data from the stream.

4. **Clean up resources**: use the `onFinishStream` handler to clean up resources and process any remaining data.

5. **Handle errors**: implement proper error handling for malformed Markdown or unexpected data.

6. **Testing**: test your parser with various input scenarios, including partial chunks and malformed input.

7. **Parallel processing**: for independent data items, consider using parallel tool calls for better performance.
