# Build a chat agent with memory

This guide shows you how to create a conversational chat agent that remembers previous messages
across multiple interactions using the [ChatMemory](../chat-memory.md) feature.

## Prerequisites

--8<-- "quickstart-snippets.md:prerequisites"

## Install Koog and Memory feature

=== "Gradle (Kotlin)"

    ```kotlin title="build.gradle.kts"
    dependencies {
        implementation("ai.koog:koog-agents:0.7.0")
        implementation("ai.koog:agents-features-memory:0.7.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy title="build.gradle"
    dependencies {
        implementation 'ai.koog:koog-agents:0.7.0'
        implementation 'ai.koog:agents-features-memory:0.7.0'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-jvm</artifactId>
        <version>0.7.0</version>
    </dependency>
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>agents-features-memory-jvm</artifactId>
        <version>0.7.0</version>
    </dependency>
    ```

## Set up an API key

--8<-- "quickstart-snippets.md:api-key"

## What you will build

A command-line chat agent that:

- Accepts user input in a loop
- Sends each message to an LLM
- Remembers the full conversation history across `agent.run()` calls
- Uses a sliding window to limit context size

Without ChatMemory, each call to `agent.run()` starts a fresh conversation — the agent has no
knowledge of what was said before. ChatMemory solves this by automatically loading previous
messages before each run and storing the updated history afterward.

## Create a chat agent

=== "OpenAI"

    === "Kotlin"

        <!--- INCLUDE
        import ai.koog.agents.chatMemory.feature.ChatMemory
        import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
        import ai.koog.agents.core.agent.AIAgent
        import ai.koog.agents.core.tools.ToolRegistry
        import ai.koog.prompt.executor.clients.openai.OpenAIModels
        import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
        -->
        ```kotlin
        suspend fun main() {
            val sessionId = "my-conversation"

            val toolRegistry = ToolRegistry {
                // register your tools here
            }

            simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")).use { executor ->
                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = OpenAIModels.Chat.GPT5_2,
                    systemPrompt = "You are a helpful assistant.",
                    toolRegistry = toolRegistry,
                ) {
                    install(ChatMemory) {
                        windowSize(20) // keep only the last 20 messages
                    }
                }

                while (true) {
                    print("You: ")
                    val input = readln().trim()
                    if (input == "/bye") break
                    if (input.isEmpty()) continue

                    val reply = agent.run(input, sessionId)
                    println("Assistant: $reply\n")
                }
            }
        }
        ```

    === "Java"

        ```java
        public class ExampleChatAgentOpenAI {
            public static void main(String[] args) {
                String sessionId = "my-conversation";
        
                ToolRegistry toolRegistry = ToolRegistry.builder()
                        // register your tools here
                        .build();
        
                try (var executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))) {
                    AIAgent<String, String> agent = AIAgent.builder()
                            .promptExecutor(executor)
                            .llmModel(OpenAIModels.Chat.GPT5_2)
                            .systemPrompt("You are a helpful assistant.")
                            .toolRegistry(toolRegistry)
                            .install(ChatMemory.Feature, config -> {
                                config.windowSize(20); // keep only the last 20 messages
                            })
                            .build();
        
                    Scanner scanner = new Scanner(System.in);
                    while (true) {
                        System.out.print("You: ");
                        String input = scanner.nextLine().trim();
                        if (input.equals("/bye")) break;
                        if (input.isEmpty()) continue;
        
                        String reply = agent.run(input, sessionId);
                        System.out.println("Assistant: " + reply + "\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        ```

=== "Anthropic"

    === "Kotlin"

        <!--- INCLUDE
        import ai.koog.agents.chatMemory.feature.ChatMemory
        import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
        import ai.koog.agents.core.agent.AIAgent
        import ai.koog.agents.core.tools.ToolRegistry
        import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
        import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
        -->
        ```kotlin
        suspend fun main() {
            val sessionId = "my-conversation"

            val toolRegistry = ToolRegistry {
                // register your tools here
            }

            simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY")).use { executor ->
                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = AnthropicModels.Sonnet_4_5,
                    systemPrompt = "You are a helpful assistant.",
                    toolRegistry = toolRegistry,
                ) {
                    install(ChatMemory) {
                        windowSize(20)
                    }
                }

                while (true) {
                    print("You: ")
                    val input = readln().trim()
                    if (input == "/bye") break
                    if (input.isEmpty()) continue

                    val reply = agent.run(input, sessionId)
                    println("Assistant: $reply\n")
                }
            }
        }
        ```

    === "Java"

        ```java
        public class ExampleChatAgentAnthropic {
            public static void main(String[] args) {
                String sessionId = "my-conversation";
        
                ToolRegistry toolRegistry = ToolRegistry.builder()
                        // register your tools here
                        .build();
        
                try (var executor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))) {
                    AIAgent<String, String> agent = AIAgent.builder()
                            .promptExecutor(executor)
                            .llmModel(AnthropicModels.Sonnet_4_5)
                            .systemPrompt("You are a helpful assistant.")
                            .toolRegistry(toolRegistry)
                            .install(ChatMemory.Feature, config -> {
                                config.windowSize(20);
                            })
                            .build();
        
                    Scanner scanner = new Scanner(System.in);
                    while (true) {
                        System.out.print("You: ");
                        String input = scanner.nextLine().trim();
                        if (input.equals("/bye")) break;
                        if (input.isEmpty()) continue;
        
                        String reply = agent.run(input, sessionId);
                        System.out.println("Assistant: " + reply + "\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        ```

=== "Google"

    === "Kotlin"

        <!--- INCLUDE
        import ai.koog.agents.chatMemory.feature.ChatMemory
        import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
        import ai.koog.agents.core.agent.AIAgent
        import ai.koog.agents.core.tools.ToolRegistry
        import ai.koog.prompt.executor.clients.google.GoogleModels
        import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
        -->
        ```kotlin
        suspend fun main() {
            val sessionId = "my-conversation"

            val toolRegistry = ToolRegistry {
                // register your tools here
            }

            simpleGoogleAIExecutor(System.getenv("GOOGLE_API_KEY")).use { executor ->
                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = GoogleModels.Gemini2_5Pro,
                    systemPrompt = "You are a helpful assistant.",
                    toolRegistry = toolRegistry,
                ) {
                    install(ChatMemory) {
                        windowSize(20)
                    }
                }

                while (true) {
                    print("You: ")
                    val input = readln().trim()
                    if (input == "/bye") break
                    if (input.isEmpty()) continue

                    val reply = agent.run(input, sessionId)
                    println("Assistant: $reply\n")
                }
            }
        }
        ```

    === "Java"

        ```java
        public class ExampleChatAgentGoogle {
            public static void main(String[] args) {
                String sessionId = "my-conversation";
        
                ToolRegistry toolRegistry = ToolRegistry.builder()
                        // register your tools here
                        .build();
        
                try (var executor = simpleGoogleAIExecutor(System.getenv("GOOGLE_API_KEY"))) {
                    AIAgent<String, String> agent = AIAgent.builder()
                            .promptExecutor(executor)
                            .llmModel(GoogleModels.Gemini2_5Pro)
                            .systemPrompt("You are a helpful assistant.")
                            .toolRegistry(toolRegistry)
                            .install(ChatMemory.Feature, config -> {
                                config.windowSize(20);
                            })
                            .build();
        
                    Scanner scanner = new Scanner(System.in);
                    while (true) {
                        System.out.print("You: ");
                        String input = scanner.nextLine().trim();
                        if (input.equals("/bye")) break;
                        if (input.isEmpty()) continue;
        
                        String reply = agent.run(input, sessionId);
                        System.out.println("Assistant: " + reply + "\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        ```

=== "Ollama"

    === "Kotlin"

        <!--- INCLUDE
        import ai.koog.agents.chatMemory.feature.ChatMemory
        import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
        import ai.koog.agents.core.agent.AIAgent
        import ai.koog.agents.core.tools.ToolRegistry
        import ai.koog.prompt.executor.ollama.client.OllamaModels
        import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
        -->
        ```kotlin
        suspend fun main() {
            val sessionId = "my-conversation"

            val toolRegistry = ToolRegistry {
                // register your tools here
            }

            simpleOllamaAIExecutor().use { executor ->
                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = OllamaModels.Meta.LLAMA_3_2,
                    systemPrompt = "You are a helpful assistant.",
                    toolRegistry = toolRegistry,
                ) {
                    install(ChatMemory) {
                        windowSize(20)
                    }
                }

                while (true) {
                    print("You: ")
                    val input = readln().trim()
                    if (input == "/bye") break
                    if (input.isEmpty()) continue

                    val reply = agent.run(input, sessionId)
                    println("Assistant: $reply\n")
                }
            }
        }
        ```

    === "Java"

        ```java
        public class ExampleChatAgentOllama {
            public static void main(String[] args) {
                String sessionId = "my-conversation";

                ToolRegistry toolRegistry = ToolRegistry.builder()
                        // register your tools here
                        .build();

                try (var executor = simpleOllamaAIExecutor("http://localhost:11434")) {
                    AIAgent<String, String> agent = AIAgent.builder()
                            .promptExecutor(executor)
                            .llmModel(OllamaModels.Meta.LLAMA_3_2)
                            .systemPrompt("You are a helpful assistant.")
                            .toolRegistry(toolRegistry)
                            .install(ChatMemory.Feature, config -> {
                                config.windowSize(20);
                            })
                            .build();

                    Scanner scanner = new Scanner(System.in);
                    while (true) {
                        System.out.print("You: ");
                        String input = scanner.nextLine().trim();
                        if (input.equals("/bye")) break;
                        if (input.isEmpty()) continue;

                        String reply = agent.run(input, sessionId);
                        System.out.println("Assistant: " + reply + "\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        ```

## How it works

The example above has three key parts:

### 1. Install ChatMemory

ChatMemory is installed as a [feature](../features-overview.md) inside the agent builder block:

=== "Kotlin"

    ```kotlin
    AIAgent(
        promptExecutor = executor,
        llmModel = OpenAIModels.Chat.GPT5_2,
        systemPrompt = "You are a helpful assistant.",
        toolRegistry = toolRegistry,
    ) {
        install(ChatMemory) {
            windowSize(20) // keep only the last 20 messages
        }
    }
    ```

=== "Java"

    ```java
    AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(OpenAIModels.Chat.GPT5_2)
            .systemPrompt("You are a helpful assistant.")
            .toolRegistry(toolRegistry)
            .install(ChatMemory.Feature, config -> {
                config.windowSize(20); // keep only the last 20 messages
            })
            .build();
    ```

The `windowSize(20)` [preprocessor](../chat-memory.md#preprocessors) ensures that the conversation context
stays bounded — only the 20 most recent messages are kept. Without this, prompt size
grows unboundedly as the conversation gets longer.

### 2. Use a consistent session ID

The second argument to `agent.run()` is the session ID:

=== "Kotlin"

    ```kotlin
    val reply = agent.run(input, sessionId)
    ```

=== "Java"

    ```java
    String reply = agent.run(input, sessionId);
    ```

ChatMemory uses this ID to load and store the conversation. All calls with the same session ID
share the same history. Different session IDs produce fully isolated conversations.

### 3. The chat loop

Each iteration of the `while` loop:

1. Reads user input
2. Calls `agent.run(input, sessionId)` — ChatMemory automatically loads the previous history
   before the LLM sees the prompt
3. Prints the response
4. ChatMemory automatically stores the updated history (including the new user message and
   assistant response)

## Example session

```
You: My name is Alice.
Assistant: Nice to meet you, Alice! How can I help you today?

You: What's my favorite color? It's blue.
Assistant: Got it — your favorite color is blue!

You: What's my name?
Assistant: Your name is Alice!
```

The agent correctly answers "Your name is Alice!" because ChatMemory loaded the earlier
exchanges before processing the third message.

## Next steps

- Learn about [preprocessors](../chat-memory.md#preprocessors) to filter and transform conversation history
- Implement a [custom history provider](../chat-memory.md#custom-history-providers) for persistent storage
- See a [backend use case](../chat-memory.md#typical-use-case-backend-applications) with Spring Boot for managing chat sessions over HTTP
- Understand the [difference between ChatMemory and Persistence](../chat-memory.md#chatmemory-vs-persistence) for crash recovery scenarios
- Explore [Chat Memory](../chat-memory.md) for the full feature reference
