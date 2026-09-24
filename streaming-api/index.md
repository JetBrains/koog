# Streaming API

Koog’s **Streaming API** lets you consume **LLM output incrementally** as a `Flow<StreamFrame>` in Kotlin / `Flow.Publisher<StreamFrame>` in Java. Instead of waiting for a full response, your code can:

- render assistant text as it arrives,
- detect **tool calls** live and act on them,
- know when a stream **ends** and why.

The stream carries **typed frames** organized into two categories:

**Delta frames** (incremental/partial content):

- `StreamFrame.TextDelta(text: String, index: Int?)` — incremental assistant text
- `StreamFrame.ReasoningDelta(text: String?, summary: String?, index: Int?)` — incremental reasoning text and summary
- `StreamFrame.ToolCallDelta(id: String?, name: String?, content: String?, index: Int?)` — partial tool invocation

**Complete frames** (full content):

- `StreamFrame.TextComplete(text: String, index: Int?)` — complete assistant text
- `StreamFrame.ReasoningComplete(content: List<String>, summary: List<String>?, encrypted: String?, index: Int?)` — complete reasoning with optional summary and encrypted content
- `StreamFrame.ToolCallComplete(id: String?, name: String, content: String, index: Int?)` — complete tool invocation

**End marker**:

- `StreamFrame.End(finishReason: String?, metaInfo: ResponseMetaInfo)` — end-of-stream marker with response metadata

**Delta frames** (incremental/partial content):

- `StreamFrame.TextDelta` — incremental assistant text. Fields: `getText()`, `getIndex()`.
- `StreamFrame.ReasoningDelta` — incremental reasoning text and summary. Fields: `getText()`, `getSummary()`, `getIndex()`.
- `StreamFrame.ToolCallDelta` — partial tool invocation. Fields: `getId()`, `getName()`, `getContent()`, `getIndex()`.

**Complete frames** (full content):

- `StreamFrame.TextComplete` — complete assistant text. Fields: `getText()`, `getIndex()`.
- `StreamFrame.ReasoningComplete` — complete reasoning with optional summary and encrypted content. Fields: `getText()` (returns `List<String>`), `getSummary()` (returns `List<String>`), `getEncrypted()`, `getIndex()`.
- `StreamFrame.ToolCallComplete` — complete tool invocation. Fields: `getId()`, `getName()`, `getContent()`, `getIndex()`. Also provides `getContentJson()` and `getContentJsonResult()` for JSON parsing.

**End marker**:

- `StreamFrame.End` — end-of-stream marker. Fields: `getFinishReason()`, `getMetaInfo()`.

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

______________________________________________________________________

## Usage

### Working with frames directly

This is the most general approach: react to each frame kind.

```
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

```
ctx.getLlm().writeSession(session -> {
    session.appendPrompt(prompt -> {
        prompt.user("Tell me a joke, then call a tool with JSON args.");
        return null;
    });

    Flow.Publisher<StreamFrame> stream = session.requestLLMStreaming();

    stream.subscribe(new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamFrame frame) {
            if (frame instanceof StreamFrame.TextDelta delta) {
                System.out.print(delta.getText());
            } else if (frame instanceof StreamFrame.ReasoningDelta reasoning) {
                System.out.print("[Reasoning] text=" + reasoning.getText()
                    + " summary=" + reasoning.getSummary());
            } else if (frame instanceof StreamFrame.ToolCallComplete toolCall) {
                System.out.println("\nTool call: " + toolCall.getName()
                    + " args=" + toolCall.getContent());
            } else if (frame instanceof StreamFrame.End end) {
                System.out.println("\n[END] reason=" + end.getFinishReason());
            }
            // Handle other frame types (TextComplete, ToolCallDelta, etc.)
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Stream error: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
        }
    });

    return null;
});
```

It is important to note that you can parse the output by working directly with a raw string stream. This approach gives you more flexibility and control over the parsing process.

Here is a raw string stream with the Markdown definition of the output structure:

```
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

```
StructureDefinition mdDefinition = markdownBookDefinition();

ctx.getLlm().writeSession(session -> {
    session.appendPrompt(prompt -> {
        prompt.user(input);
    });

    Flow.Publisher<StreamFrame> stream = session.requestLLMStreaming(mdDefinition);

    // Access the raw frames directly
    stream.subscribe(new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamFrame frame) {
            // Process each frame as it arrives
            System.out.println("Received frame: " + frame);
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Stream error: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
        }
    });

    return null;
});
```

### Working with reasoning frames

Models that support reasoning (such as Claude Sonnet 4.5 or GPT-o1) emit reasoning frames during streaming. You can access both the reasoning process and its summary:

```
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
                println("\nComplete reasoning: ${frame.content.joinToString("")}")
                println("Summary: ${frame.summary?.joinToString("") ?: "N/A"}")
            }
            is StreamFrame.TextDelta -> print(frame.text)
            is StreamFrame.End -> println("\n[END]")
            else -> {}
        }
    }
}
```

```
ctx.getLlm().writeSession(session -> {
    session.appendPrompt(prompt -> {
        prompt.user("Solve this complex problem: ...");
        return null;
    });

    Flow.Publisher<StreamFrame> stream = session.requestLLMStreaming();
    List<String> reasoningSteps = new ArrayList<>();
    List<String> summarySteps = new ArrayList<>();

    stream.subscribe(new Flow.Subscriber<StreamFrame>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamFrame frame) {
            if (frame instanceof StreamFrame.ReasoningDelta reasoning) {
                if (reasoning.getText() != null) {
                    reasoningSteps.add(reasoning.getText());
                    System.out.print(reasoning.getText());
                }
                if (reasoning.getSummary() != null) {
                    summarySteps.add(reasoning.getSummary());
                    System.out.print(reasoning.getSummary());
                }
            } else if (frame instanceof StreamFrame.ReasoningComplete complete) {
                // Access complete reasoning
                System.out.println("\nComplete reasoning: "
                    + String.join("", complete.getContent()));
                System.out.println("Summary: "
                    + (complete.getSummary() != null
                        ? String.join("", complete.getSummary()) : "N/A"));
            } else if (frame instanceof StreamFrame.TextDelta delta) {
                System.out.print(delta.getText());
            } else if (frame instanceof StreamFrame.End) {
                System.out.println("\n[END]");
            }
        }

        @Override
        public void onError(Throwable throwable) { }

        @Override
        public void onComplete() { }
    });

    return null;
});
```

### Working with a raw text stream (derived)

If you have existing streaming parsers that expect `Flow<String>`, derive text chunks via `filterTextOnly()` or collect them with `collectText()`.

```
llm.writeSession {
    val frames = requestLLMStreaming()

    // Stream text chunks as they come:
    frames.filterTextOnly().collect { chunk -> print(chunk) }

    // Or, gather all text into one String after End:
    val fullText = frames.collectText()
    println("\n---\n$fullText")
}
```

```
ctx.getLlm().writeSession(session -> {
    Flow.Publisher<StreamFrame> frames = session.requestLLMStreaming();

    // Stream text chunks as they come (equivalent of filterTextOnly):
    StringBuilder fullText = new StringBuilder();
    frames.subscribe(new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamFrame frame) {
            if (frame instanceof StreamFrame.TextDelta delta) {
                System.out.print(delta.getText());
                fullText.append(delta.getText());
            }
        }

        @Override
        public void onError(Throwable throwable) { }

        @Override
        public void onComplete() {
            // fullText now contains all text (equivalent of collectText)
            System.out.println("\n---\n" + fullText);
        }
    });

    return null;
});
```

### Listening to stream events in event handlers

You can listen to stream events in [agent event handlers](../features/agent-event-handlers/).

```
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

```
.install(EventHandler.Feature, config -> {
    config.onToolCallStarting(ctx -> {
        System.out.println("\nUsing " + ctx.getToolName() + " with " + ctx.getToolArgs() + "... ");
    });

    config.onLLMStreamingFrameReceived(ctx -> {
        StreamFrame frame = ctx.getStreamFrame();
        if (frame instanceof StreamFrame.TextDelta delta) {
            System.out.print(delta.getText());
        } else if (frame instanceof StreamFrame.ReasoningDelta reasoning) {
            System.out.print("[Reasoning] text=" + reasoning.getText()
                + " summary=" + reasoning.getSummary());
        }
    });

    config.onLLMStreamingFailed(ctx -> {
        System.out.println("Error: " + ctx.getError());
    });

    config.onLLMStreamingCompleted(ctx -> {
        System.out.println("Done");
    });
})
```

### Converting frames to `Message.Response`

You can transform a collected list of frames to standard message objects:

- `toAssistantMessageOrNull()` — extracts `Message.Assistant` from text frames
- `toReasoningMessageOrNull()` — extracts `MessagePart.Reasoning` from reasoning frames
- `toToolCallMessages()` — extracts `MessagePart.Tool.Call` from tool call frames
- `toMessageResponses()` — converts all complete frames to their corresponding `Message.Response` objects

## Examples

### Structured data while streaming (Markdown example)

Although it is possible to work with a raw string stream, it is often more convenient to work with [structured data](../structured-output/).

The structured data approach includes the following key components:

1. **MarkdownStructureDefinition**: a class to help you define the schema and examples for structured data in Markdown format.
1. **markdownStreamingParser**: a function to create a parser that processes a stream of Markdown chunks and emits events.

The sections below provide step-by-step instructions and code samples related to processing a stream of structured data.

#### 1. Define your data structure

First, define a data class to represent your structured data:

```
@Serializable
data class Book(
    val title: String,
    val author: String,
    val description: String
)
```

```
// TODO not yet supported in Java
```

#### 2. Define the Markdown structure

Create a definition that specifies how your data should be structured in Markdown with the `MarkdownStructureDefinition` class:

```
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

```
// TODO not yet supported in Java
```

#### 3. Create a parser for your data structure

The `markdownStreamingParser` provides several handlers for different Markdown elements:

```
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

```
// TODO not yet supported in Java
```

Using the defined handlers, you can implement a function that parses the Markdown stream and emits your data objects with the `markdownStreamingParser` function.

```
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

```
// TODO not yet supported in Java
```

#### 4. Use the parser in your agent strategy

```
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

```
// TODO not yet supported in Java
```

### Advanced usage: Streaming with tools

You can also use the Streaming API with tools to process data as it arrives. The following sections provide a brief step-by-step guide on how to define a tool and use it with streaming data.

### 1. Define a tool for your data structure

```
@Serializable
data class Book(
   val title: String,
   val author: String,
   val description: String
)

class BookTool(): SimpleTool<Book>(
    argsType = typeToken<Book>(),
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

```
class BookTool implements ToolSet {
    @Tool
    @LLMDescription("A tool to parse book information from Markdown")
    public String book(
        @LLMDescription("Title of the book") String title,
        @LLMDescription("Author of the book") String author,
        @LLMDescription("Description of the book") String description
    ) {
        System.out.println(title + " by " + author + ":\n " + description);
        return "Done";
    }
}
```

### 2. Use the tool with streaming data

```
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

```
var strategy = AIAgentGraphStrategy.builder("library-assistant")
    .withInput(String.class)
    .withOutput(Void.class);

var getMdOutput = AIAgentNode.builder("getMdOutput")
    .withInput(String.class)
    .withOutput(Void.class)
    .withAction((input, ctx) -> {
        StructureDefinition mdDefinition = markdownBookDefinition();

        ctx.getLlm().writeSession(session -> {
            session.appendPrompt(prompt -> {
                prompt.user(input);
                return null;
            });

            Flow.Publisher<StreamFrame> markdownStream = session.requestLLMStreaming(mdDefinition);

            // Process streamed frames and invoke tools on ToolCallComplete frames
            markdownStream.subscribe(new Flow.Subscriber<StreamFrame>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamFrame frame) {
                    if (frame instanceof StreamFrame.ToolCallComplete toolCall) {
                        System.out.println("Tool call: " + toolCall.getName()
                            + " args=" + toolCall.getContent());
                    }
                }

                @Override
                public void onError(Throwable throwable) { }

                @Override
                public void onComplete() { }
            });

            return null;
        });

        return null;
    })
    .build();

strategy.edge(strategy.nodeStart, getMdOutput);
strategy.edge(getMdOutput, strategy.nodeFinish);
```

### 3. Register the tool in your agent configuration

```
val toolRegistry = ToolRegistry {
    tool(BookTool())
}

val runner = AIAgent(
    promptExecutor = simpleOpenAIExecutor("OPENAI_API_KEY"),
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry
)
```

```
ToolRegistry toolRegistry = ToolRegistry.builder()
    .tools(new BookTool())
    .build();

AIAgent<String, String> runner = AIAgent.<String, String>builder()
    .promptExecutor(PromptExecutor.builder().openAI("OPENAI_API_KEY").build())
    .llmModel(OpenAIModels.Chat.GPT4o)
    .toolRegistry(toolRegistry)
    .build();
```

## Best practices

1. **Define clear structures**: create clear and unambiguous markdown structures for your data.
1. **Provide good examples**: include comprehensive examples in your `MarkdownStructureDefinition` to guide the LLM.
1. **Handle incomplete data**: always check for null or empty values when parsing data from the stream.
1. **Clean up resources**: use the `onFinishStream` handler to clean up resources and process any remaining data.
1. **Handle errors**: implement proper error handling for malformed Markdown or unexpected data.
1. **Testing**: test your parser with various input scenarios, including partial chunks and malformed input.
1. **Parallel processing**: for independent data items, consider using parallel tool calls for better performance.
