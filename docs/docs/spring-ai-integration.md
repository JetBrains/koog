---
status: beta
---

# Spring AI Integration

--8<-- "versioning-snippets.md:beta"

Koog provides Spring AI integration starters that bridge Spring AI's abstractions with the Koog agent framework.
If you already use Spring AI for model access, memory, or vector storage, these starters let you plug Koog on top
without replacing your existing Spring AI configuration.

## How it differs from `koog-spring-boot-starter`

| | `koog-spring-boot-starter` | `koog-spring-ai` starters |
|---|---|---|
| **LLM transport** | Koog's own HTTP clients | Delegates to Spring AI beans such as `ChatModel` and `EmbeddingModel` |
| **Configuration** | `ai.koog.*` properties per provider | Standard `spring.ai.*` properties managed by Spring AI starters, plus `koog.spring.ai.*` adapter properties |
| **When to use** | You want Koog to manage model connectivity directly | You already use Spring AI and want Koog agents, memory, or RAG on top |

Both approaches are independent.
For the direct Koog starter approach, see [Spring Boot Integration](spring-boot.md).

## Available Starters

| Module | Purpose |
|---|---|
| `koog-spring-ai-starter-model-chat` | Adapts a Spring AI `ChatModel` with optional `ModerationModel` into a Koog `LLMClient` and `PromptExecutor` |
| `koog-spring-ai-starter-model-embedding` | Adapts a Spring AI `EmbeddingModel` into a Koog `LLMEmbeddingProvider` |
| `koog-spring-ai-starter-chat-memory` | Adapts a Spring AI `ChatMemoryRepository` into a Koog `ChatHistoryProvider` |
| `koog-spring-ai-starter-vector-store` | Adapts a Spring AI `VectorStore` into Koog `KoogVectorStore` for ingestion, search, and deletion |

Each starter is an independent Spring Boot starter with its own auto-configuration and configuration properties.
You can use one starter or combine several in the same application.

## Dispatcher Types

All four starters support the same dispatcher configuration pattern:

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available, otherwise falls back to `Dispatchers.IO`.
- **`IO`**: Always uses `Dispatchers.IO`.
- **`dispatcher.parallelism`**: When greater than `0` and `type=IO`, uses `Dispatchers.IO.limitedParallelism(parallelism)`.

`AUTO` is usually the simplest choice, especially when you use Spring Boot virtual threads.

## Chat Model Starter

### Overview

The `koog-spring-ai-starter-model-chat` starter bridges Spring AI's chat model abstraction with the Koog agent framework.
It auto-configures:

- A Koog `LLMClient` (`SpringAiLLMClient`) that delegates to a Spring AI `ChatModel`
- A `PromptExecutor` (`MultiLLMPromptExecutor`) assembled from all available `LLMClient` beans

Tools are always executed by the Koog agent framework.
Spring AI receives only tool definitions and schemas, and `internalToolExecutionEnabled` is set to `false`.

### Add Dependency

Add the dependency alongside any Spring AI chat model starter:

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("ai.koog:koog-agents-jvm:$koogVersion")
        implementation("ai.koog:koog-spring-ai-starter-model-chat:$koogVersion")
        implementation("org.springframework.ai:spring-ai-starter-model-openai")
    }
    ```

=== "Maven"

    ```xml
    <dependencies>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-spring-ai-starter-model-chat</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
    </dependencies>
    ```

### Available providers

The starter works with any provider for which Spring AI creates a `ChatModel`, including:
Anthropic, Azure OpenAI, Bedrock Converse, DeepSeek, Google GenAI, HuggingFace, MiniMax,
Mistral AI, OCI GenAI, Ollama, OpenAI, Vertex AI, and ZhiPu AI.

### Configure

Configure your provider through the matching Spring AI starter, then add Koog properties if needed:

```properties
# example Spring AI provider configuration
spring.ai.openai.api-key=${OPENAI_API_KEY}

# Koog chat starter defaults
koog.spring.ai.chat.enabled=true
koog.spring.ai.chat.dispatcher.type=AUTO
```

If you have a single `ChatModel` bean, everything works automatically.
The adapter wraps it into a Koog `LLMClient` and creates a ready-to-use `PromptExecutor`.

### Usage Example

Inject the `PromptExecutor` and use it to run a Koog agent:

=== "Kotlin"

    ```kotlin
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import org.springframework.stereotype.Service

    @Service
    class MyAgentService(private val promptExecutor: PromptExecutor) {

        suspend fun askAgent(userMessage: String): String {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                llmModel = OpenAIModels.Chat.GPT5Nano,
                systemPrompt = "You are a helpful assistant."
            )

            return agent.run(userMessage)
        }
    }
    ```

=== "Java"

    ```java
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import org.springframework.stereotype.Service;

    @Service
    public class MyAgentService {
        private final PromptExecutor promptExecutor;

        public MyAgentService(PromptExecutor promptExecutor) {
            this.promptExecutor = promptExecutor;
        }

        public String askAgent(String userMessage) {
            var agent = AIAgent.builder()
                    .promptExecutor(promptExecutor)
                    .llmModel(OpenAIModels.Chat.GPT5Nano)
                    .systemPrompt("You are a helpful assistant.")
                    .build();

            return agent.run(userMessage);
        }
    }
    ```

Or provide your own `PromptExecutor` bean to override the auto-configured one entirely.

### Configuration Properties (`koog.spring.ai.chat`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable or disable the chat auto-configuration |
| `chat-model-bean-name` | `String?` | `null` | Bean name of the `ChatModel` to use when multiple models are present |
| `moderation-model-bean-name` | `String?` | `null` | Bean name of the `ModerationModel` to use |
| `provider` | `String?` | `null` | Koog provider id to expose instead of auto-detecting from the `ChatModel` class |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking model calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher |

### Multi-model Contexts

When multiple `ChatModel` or `ModerationModel` beans are registered, specify which one to use:

```properties
koog.spring.ai.chat.chat-model-bean-name=openAiChatModel
koog.spring.ai.chat.moderation-model-bean-name=openAiModerationModel
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Extension Points

- **`ChatOptionsCustomizer`**: Register a Spring bean implementing this interface to customize `ChatOptions`

=== "Kotlin"

    ```kotlin
    @Bean
    fun chatOptionsCustomizer() = ChatOptionsCustomizer { options, params, model ->
        options
    }
    ```

=== "Java"

    ```java
    @Bean
    public ChatOptionsCustomizer chatOptionsCustomizer() {
        return (options, params, model) -> options;
    }
    ```

- **Custom `LLMClient`**: Register your own `LLMClient` bean. It will be composed together with the auto-configured adapter unless you replace the bean named `springAiChatModelLLMClient`.
- **Custom `PromptExecutor`**: Register your own `PromptExecutor` bean to override the auto-configured `MultiLLMPromptExecutor`.

## Embedding Model Starter

### Overview

The `koog-spring-ai-starter-model-embedding` starter bridges Spring AI's embedding model abstraction with the Koog agent framework.
It auto-configures:

- A Koog `LLMEmbeddingProvider` (`SpringAiLLMEmbeddingProvider`) that delegates to a Spring AI `EmbeddingModel`

The adapter forwards the Koog model id into Spring AI `EmbeddingOptions`, so backends that support runtime model selection can honor it.

### Add Dependency

Add the dependency alongside any Spring AI embedding model starter:

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("ai.koog:koog-agents-jvm:$koogVersion")
        implementation("ai.koog:koog-spring-ai-starter-model-embedding:$koogVersion")
        implementation("org.springframework.ai:spring-ai-starter-model-openai")
    }
    ```

=== "Maven"

    ```xml
    <dependencies>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-spring-ai-starter-model-embedding</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
    </dependencies>
    ```

### Available providers

The starter works with any provider for which Spring AI creates an `EmbeddingModel`, including:
Anthropic, Azure OpenAI, Bedrock, Google GenAI, HuggingFace, Mistral AI, OCI GenAI,
Ollama, OpenAI, Transformers, Vertex AI, and ZhiPu AI.

### Configure

Configure your embedding provider through Spring AI, then add Koog properties if needed:

```properties
# example Spring AI provider configuration
spring.ai.openai.api-key=${OPENAI_API_KEY}

# Koog embedding starter defaults
koog.spring.ai.embedding.enabled=true
koog.spring.ai.embedding.dispatcher.type=AUTO
```

If you have a single `EmbeddingModel` bean, everything works automatically.
The adapter wraps it into a Koog `LLMEmbeddingProvider`.

### Usage Example

Inject `LLMEmbeddingProvider` and use it for embedding operations:

=== "Kotlin"

    ```kotlin
    import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import org.springframework.stereotype.Service

    @Service
    class MyEmbeddingService(private val embeddingProvider: LLMEmbeddingProvider) {

        suspend fun getEmbedding(text: String): List<Double> {
            return embeddingProvider.embed(
                text,
                OpenAIModels.Embeddings.TextEmbedding3Small
            )
        }
    }
    ```

=== "Java"

    ```java
    import ai.koog.prompt.executor.clients.LLMEmbeddingProvider;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import org.springframework.stereotype.Service;
    import java.util.List;

    @Service
    public class MyEmbeddingService {
        private final LLMEmbeddingProvider embeddingProvider;

        public MyEmbeddingService(LLMEmbeddingProvider embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
        }

        public List<Double> getEmbedding(String text) {
            return embeddingProvider.embed(
                    text,
                    OpenAIModels.Embeddings.TextEmbedding3Small
            );
        }
    }
    ```

Or provide your own `LLMEmbeddingProvider` bean to override the auto-configured adapter entirely.

### Configuration Properties (`koog.spring.ai.embedding`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable or disable the embedding auto-configuration |
| `embedding-model-bean-name` | `String?` | `null` | Bean name of the `EmbeddingModel` to use when multiple models are present |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking embedding calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher |

### Multi-model Contexts

When multiple `EmbeddingModel` beans are registered, specify which one to use:

```properties
koog.spring.ai.embedding.embedding-model-bean-name=openAiEmbeddingModel
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Extension Points

- **Custom `LLMEmbeddingProvider`**: Register your own bean to override the auto-configured adapter entirely.

## Chat Memory Starter

### Overview

The `koog-spring-ai-starter-chat-memory` starter bridges Spring AI's chat memory abstraction with the Koog agent framework.
It auto-configures:

- A Koog `ChatHistoryProvider` (`SpringAiChatHistoryProvider`) that delegates to a Spring AI `ChatMemoryRepository`

This starter provides text-only conversation persistence, not full Koog execution-state persistence.

### Text-only contract

Only plain-text `System`, `User`, and `Assistant` messages are persisted.
The following are silently dropped on store:

- `MessagePart.Tool.Call`
- `MessagePart.Tool.Result`
- `MessagePart.Reasoning`
- any message carrying attachments

On load, Spring AI `TOOL` rows are silently skipped.
Metadata such as timestamps, token counts, finish reasons, and custom metadata is not preserved.

### Add Dependency

Add the dependency alongside a Spring AI chat memory repository implementation:

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("ai.koog:koog-agents-jvm:$koogVersion")
        implementation("ai.koog:koog-spring-ai-starter-chat-memory:$koogVersion")
        implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    }
    ```

=== "Maven"

    ```xml
    <dependencies>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-spring-ai-starter-chat-memory</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
        </dependency>
    </dependencies>
    ```

### Available providers

This starter works with any Spring AI chat memory repository implementation that exposes `ChatMemoryRepository`,
including JDBC, Redis, Cassandra, Cosmos DB, MongoDB, and Neo4j based repositories.

### Configure

Usually no extra configuration is required beyond your Spring AI repository setup:

```properties
# Koog chat-memory starter defaults
koog.spring.ai.chat-memory.enabled=true
koog.spring.ai.chat-memory.dispatcher.type=AUTO
```

If you have a single `ChatMemoryRepository` bean, everything works automatically.
The adapter wraps it into a Koog `ChatHistoryProvider`.

### Usage Example

Install the `ChatMemory` feature on your agent using the auto-configured `ChatHistoryProvider`:

=== "Kotlin"

    ```kotlin
    import ai.koog.agents.chatMemory.feature.ChatMemory
    import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import org.springframework.stereotype.Service

    @Service
    class MyAgentService(
        private val promptExecutor: PromptExecutor,
        private val chatStorage: ChatHistoryProvider,
    ) {

        suspend fun askAgent(userMessage: String, sessionId: String): String {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                llmModel = OpenAIModels.Chat.GPT5Nano,
                systemPrompt = "You are a helpful assistant.",
            ) {
                install(ChatMemory) {
                    chatHistoryProvider = chatStorage
                }
            }

            return agent.run(userMessage, sessionId)
        }
    }
    ```

### Configuration Properties (`koog.spring.ai.chat-memory`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable or disable the chat-memory auto-configuration |
| `chat-memory-repository-bean-name` | `String?` | `null` | Bean name of the `ChatMemoryRepository` to use when multiple repositories are present |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking repository calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher |

### Multi-repository Contexts

When multiple `ChatMemoryRepository` beans are registered, specify which one to use:

```properties
koog.spring.ai.chat-memory.chat-memory-repository-bean-name=jdbcChatMemoryRepository
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Current limitations

- Only text conversation history is persisted
- Tool calls, tool results, reasoning messages, and attachments are not persisted
- Spring AI `TOOL` messages are skipped on load
- Message metadata is not preserved through the round-trip

## Vector Store Starter

### Overview

The `koog-spring-ai-starter-vector-store` starter bridges Spring AI vector-store abstractions with Koog's RAG storage interfaces.
It auto-configures:

- A `SpringAiKoogVectorStore` adapter exposed as Koog `KoogVectorStore`

`KoogVectorStore` combines:

- `WriteStorage<TextDocument>`
- `SearchStorage<TextDocument, SimilaritySearchRequest>`
- `FilteringDeletionStorage`

Examples typically use `DocumentWithMetadata` as the concrete document type.

### Add Dependency

Add the dependency alongside a Spring AI vector-store starter:

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("ai.koog:koog-spring-ai-starter-vector-store:$koogVersion")
        implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    }
    ```

=== "Maven"

    ```xml
    <dependencies>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-spring-ai-starter-vector-store</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
        </dependency>
    </dependencies>
    ```

### Available providers

The starter works with any Spring AI implementation that exposes `VectorStore`, including PgVector,
Azure AI Search, Cassandra, Chroma, Elasticsearch, Milvus, MongoDB Atlas, Neo4j, OpenSearch,
Oracle, Pinecone, Qdrant, Redis, Typesense, and Weaviate.

### Configure

Usually no extra Koog configuration is required beyond your Spring AI vector-store setup:

```properties
# Koog vector-store starter defaults
koog.spring.ai.vectorstore.enabled=true
koog.spring.ai.vectorstore.dispatcher.type=AUTO
```

If you have a single `VectorStore` bean, everything works automatically.
The adapter wraps it into a Koog `KoogVectorStore`.

### Usage Example

Inject `KoogVectorStore` directly into your Spring components:

=== "Kotlin"

    ```kotlin
    import ai.koog.rag.base.TextDocument
    import ai.koog.rag.base.storage.search.SearchResult
    import ai.koog.rag.base.storage.search.SimilaritySearchRequest
    import ai.koog.spring.ai.vectorstore.DocumentWithMetadata
    import ai.koog.spring.ai.vectorstore.KoogVectorStore
    import org.springframework.stereotype.Service

    @Service
    class MyKnowledgeBase(
        private val vectorStore: KoogVectorStore,
    ) {

        suspend fun ingest(text: String): List<String> {
            return vectorStore.add(
                listOf(
                    DocumentWithMetadata(
                        content = text,
                        metadata = mapOf("source" to "user")
                    )
                )
            )
        }

        suspend fun search(query: String): List<SearchResult<TextDocument>> {
            return vectorStore.search(
                SimilaritySearchRequest(queryText = query, limit = 5)
            )
        }

        suspend fun remove(ids: List<String>) {
            vectorStore.delete(ids)
        }
    }
    ```

### Configuration Properties (`koog.spring.ai.vectorstore`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable or disable the vector-store auto-configuration |
| `vector-store-bean-name` | `String?` | `null` | Bean name of the `VectorStore` to use when multiple stores are present |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking vector-store calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher |

### Multi-store Contexts

When multiple `VectorStore` beans are registered, specify which one to use:

```properties
koog.spring.ai.vectorstore.vector-store-bean-name=pgVectorStore
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Current limitations

- Spring AI's `VectorStore` contract exposes similarity search only
- Update is implemented as `delete(ids)` followed by `add(documents)`, so it is not transactional
- `LookupStorage` is not implemented because Spring AI has no portable read-by-id API
- `delete(ids)` returns the input ids unchanged; Spring AI does not confirm which documents were actually deleted
- `delete(filterExpression)` returns an empty list; Spring AI does not return the ids of matched documents
- Namespace scoping is not implemented
- Metadata values must be primitive values such as `String`, `Number`, or `Boolean`

## Next Steps

- Learn about [basic agents](agents/basic-agents.md) to build minimal AI workflows
- Explore [graph-based agents](agents/graph-based-agents.md) for advanced use cases
- See the [tools overview](tools/index.md) to extend your agents' capabilities
- Read [retrieval-augmented generation](retrieval-augmented-generation.md) for RAG concepts
- Check [examples](examples.md) for real-world implementations
- Read the [Spring Boot Integration](spring-boot.md) guide for the direct Koog starter approach
