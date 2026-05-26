# Streaming API

Koog’s **Streaming API** lets you consume **LLM output incrementally** as a `Flow<StreamFrame>` in Kotlin / `Flow.Publisher<StreamFrame>` in Java.
Instead of waiting for a full response, your code can:

- render assistant text as it arrives,
- detect **tool calls** live and act on them,
- know when a stream **ends** and why.

The stream carries **typed frames** organized into two categories:

=== "Kotlin"

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

=== "Java"

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

---
## Usage

### Working with frames directly

This is the most general approach: react to each frame kind.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
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

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.streaming.StreamFrame;
    import java.util.concurrent.Flow;
    class exampleStreamingApiJava01 {
        public static void main(String[] args) {
            var node = AIAgentNode.builder("streamNode")
                .withInput(String.class)
                .withOutput(Void.class)
                .withAction((input, ctx) -> {
    -->
    <!--- SUFFIX
                return null;
            })
            .build();
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava01.java -->


It is important to note that you can parse the output by working directly with a raw string stream.
This approach gives you more flexibility and control over the parsing process.

Here is a raw string stream with the Markdown definition of the output structure:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
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

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.streaming.StreamFrame;
    import ai.koog.prompt.structure.StructureDefinition;
    import java.util.concurrent.Flow;
    class exampleStreamingApiJava02 {
        static StructureDefinition markdownBookDefinition() { return null; }
        public static void main(String[] args) {
            var node = AIAgentNode.builder("streamNode")
                .withInput(String.class)
                .withOutput(Void.class)
                .withAction((input, ctx) -> {
    -->
    <!--- SUFFIX
                return null;
            })
            .build();
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava02.java -->

### Working with reasoning frames

Models that support reasoning (such as Claude Sonnet 4.5 or GPT-o1) emit reasoning frames during streaming. You can access both the reasoning process and its summary:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
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
    <!--- KNIT example-streaming-api-reasoning-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.streaming.StreamFrame;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.concurrent.Flow;
    import java.util.stream.Collectors;
    class exampleStreamingApiReasoningJava01 {
        public static void main(String[] args) {
            var node = AIAgentNode.builder("reasoningNode")
                .withInput(String.class)
                .withOutput(Void.class)
                .withAction((input, ctx) -> {
    -->
    <!--- SUFFIX
                return null;
            })
            .build();
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiReasoningJava01.java -->

### Working with a raw text stream (derived)

If you have existing streaming parsers that expect `Flow<String>`,
derive text chunks via `filterTextOnly()` or collect them with `collectText()`.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
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
    <!--- KNIT example-streaming-api-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.streaming.StreamFrame;
    import java.util.concurrent.Flow;
    class exampleStreamingApiJava03 {
        public static void main(String[] args) {
            var node = AIAgentNode.builder("streamNode")
                .withInput(String.class)
                .withOutput(Void.class)
                .withAction((input, ctx) -> {
    -->
    <!--- SUFFIX
                return null;
            })
            .build();
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava03.java -->

### Listening to stream events in event handlers

You can listen to stream events in [agent event handlers](features/agent-event-handlers.md).

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
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
    <!--- KNIT example-streaming-api-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.eventHandler.feature.EventHandler;
    import ai.koog.prompt.streaming.StreamFrame;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    class exampleStreamingApiJava04 {
        public static void main(String[] args) {
            AIAgent.builder()
                .promptExecutor(PromptExecutor.builder().ollama().build())
                .llmModel(OllamaModels.Meta.LLAMA_3_2)
    -->
    <!--- SUFFIX
            .build();
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava04.java -->

### Converting frames to `Message.Response`

You can transform a collected list of frames to standard message objects:

- `toAssistantMessageOrNull()` — extracts `Message.Assistant` from text frames
- `toReasoningMessageOrNull()` — extracts `MessagePart.Reasoning` from reasoning frames
- `toToolCallMessages()` — extracts `MessagePart.Tool.Call` from tool call frames
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
    <!--- KNIT example-streaming-api-05.kt -->

=== "Java"

    <!--- INCLUDE
    class exampleStreamingApiJava05 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // TODO not yet supported in Java
    ```
    <!--- KNIT exampleStreamingApiJava05.java -->

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
    <!--- KNIT example-streaming-api-06.kt -->

=== "Java"

    <!--- INCLUDE
    class exampleStreamingApiJava06 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // TODO not yet supported in Java
    ```
    <!--- KNIT exampleStreamingApiJava06.java -->

#### 3. Create a parser for your data structure

The `markdownStreamingParser` provides several handlers for different Markdown elements:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.exampleStreamingApi05.Book
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
    <!--- KNIT example-streaming-api-07.kt -->

=== "Java"

    <!--- INCLUDE
    class exampleStreamingApiJava07 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // TODO not yet supported in Java
    ```
    <!--- KNIT exampleStreamingApiJava07.java -->

Using the defined handlers, you can implement a function that parses the Markdown stream and emits your data objects 
with the `markdownStreamingParser` function.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.example.exampleStreamingApi05.Book
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
    <!--- KNIT example-streaming-api-08.kt -->

=== "Java"

    <!--- INCLUDE
    class exampleStreamingApiJava08 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // TODO not yet supported in Java
    ```
    <!--- KNIT exampleStreamingApiJava08.java -->

#### 4. Use the parser in your agent strategy

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.example.exampleStreamingApi05.Book
    import ai.koog.agents.example.exampleStreamingApi06.markdownBookDefinition
    import ai.koog.agents.example.exampleStreamingApi08.parseMarkdownStreamToBooks
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
    <!--- KNIT example-streaming-api-09.kt -->

=== "Java"

    <!--- INCLUDE
    class exampleStreamingApiJava09 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // TODO not yet supported in Java
    ```
    <!--- KNIT exampleStreamingApiJava09.java -->

### Advanced usage: Streaming with tools

You can also use the Streaming API with tools to process data as it arrives. 
The following sections provide a brief step-by-step guide on how to define a tool and use it with streaming data.

### 1. Define a tool for your data structure

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.SimpleTool
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.example.exampleStreamingApi05.Book
    import ai.koog.serialization.typeToken
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
    <!--- KNIT example-streaming-api-10.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.reflect.ToolSet;
    import ai.koog.agents.core.tools.annotations.Tool;
    import ai.koog.agents.core.tools.annotations.LLMDescription;
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava10.java -->

### 2. Use the tool with streaming data

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.agents.example.exampleStreamingApi06.markdownBookDefinition
    import ai.koog.agents.example.exampleStreamingApi08.parseMarkdownStreamToBooks
    import ai.koog.agents.example.exampleStreamingApi10.BookTool
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
    <!--- KNIT example-streaming-api-11.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.streaming.StreamFrame;
    import ai.koog.prompt.structure.StructureDefinition;
    import java.util.concurrent.Flow;
    class exampleStreamingApiJava11 {
        static StructureDefinition markdownBookDefinition() { return null; }
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleStreamingApiJava11.java -->

### 3. Register the tool in your agent configuration

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.tools.ToolRegistry
    import ai.koog.agents.example.exampleStreamingApi10.BookTool
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
    <!--- KNIT example-streaming-api-12.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.tools.ToolRegistry;
    import ai.koog.agents.core.tools.reflect.ToolSet;
    import ai.koog.agents.core.tools.annotations.Tool;
    import ai.koog.agents.core.tools.annotations.LLMDescription;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    class exampleStreamingApiJava12 {
        static class BookTool implements ToolSet {
            @Tool
            @LLMDescription("A tool to parse book information")
            public String book(String title, String author, String description) { return "Done"; }
        }
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    ToolRegistry toolRegistry = ToolRegistry.builder()
        .tools(new BookTool())
        .build();

    AIAgent<String, String> runner = AIAgent.<String, String>builder()
        .promptExecutor(PromptExecutor.builder().openAI("OPENAI_API_KEY").build())
        .llmModel(OpenAIModels.Chat.GPT4o)
        .toolRegistry(toolRegistry)
        .build();
    ```
    <!--- KNIT exampleStreamingApiJava12.java -->

## Best practices

1. **Define clear structures**: create clear and unambiguous markdown structures for your data.

2. **Provide good examples**: include comprehensive examples in your `MarkdownStructureDefinition` to guide the LLM.

3. **Handle incomplete data**: always check for null or empty values when parsing data from the stream.

4. **Clean up resources**: use the `onFinishStream` handler to clean up resources and process any remaining data.

5. **Handle errors**: implement proper error handling for malformed Markdown or unexpected data.

6. **Testing**: test your parser with various input scenarios, including partial chunks and malformed input.

7. **Parallel processing**: for independent data items, consider using parallel tool calls for better performance.
