# LLM clients

LLM clients are designed for direct interaction with LLM providers.
Each client implements the [`LLMClient`](api:prompt-executor-clients::ai.koog.prompt.executor.clients.LLMClient) interface, which provides methods for executing prompts and streaming responses.

You can use an LLM client when you work with a single LLM provider and don't need advanced lifecycle management.
If you need to manage multiple LLM providers, use a [prompt executor](prompt-executors.md).

The table below shows the available LLM clients and their capabilities.

| LLM provider                                        | LLMClient                                                                                                                                                                                                   | Tool<br/>calling | Streaming | Multiple<br/>choices | Embeddings | Moderation | <div style="width:50px">Model<br/>listing</div> | <div style="width:200px">Notes</div>                                                                                        |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|-----------|----------------------|------------|------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| [OpenAI](https://platform.openai.com/docs/overview) | [OpenAILLMClient](api:prompt-executor-openai-client::ai.koog.prompt.executor.clients.openai.OpenAILLMClient)                | ✓                | ✓         | ✓                    | ✓          | ✓[^1]      | ✓                                               |                                                                                                                             |
| [Anthropic](https://www.anthropic.com/)             | [AnthropicLLMClient](api:prompt-executor-anthropic-client::ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient)      | ✓                | ✓         | -                    | -          | -          | -                                               | -                                                                                                                           |
| [Google](https://ai.google.dev/) β                  | [GoogleLLMClient](api:prompt-executor-google-client::ai.koog.prompt.executor.clients.google.GoogleLLMClient)                  | ✓                | ✓         | ✓                    | ✓          | -          | ✓                                               | -                                                                                                                           |
| [DeepSeek](https://www.deepseek.com/) β             | [DeepSeekLLMClient](api:prompt-executor-deepseek-client::ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient)         | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible chat client.                                                                                              |
| [OpenRouter](https://openrouter.ai/)                | [OpenRouterLLMClient](api:prompt-executor-openrouter-client::ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient) | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible router client.                                                                                            |
| [Amazon Bedrock](https://aws.amazon.com/bedrock/)   | [BedrockLLMClient](api:prompt-executor-bedrock-client::ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient)              | ✓                | ✓         | -                    | ✓          | ✓[^2]      | -                                               | JVM-only AWS SDK client that supports multiple model families.                                                              |
| [Mistral](https://mistral.ai/) β                    | [MistralAILLMClient](api:prompt-executor-mistralai-client::ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient)    | ✓                | ✓         | ✓                    | ✓          | ✓[^3]      | ✓                                               | OpenAI-compatible client.                                                                                                   |
| [Alibaba](https://www.alibabacloud.com/en?_p_lc=1) β | [DashScopeLLMClient](api:prompt-executor-dashscope-client::ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient)    | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible client that exposes provider-specific parameters (`enableSearch`, `parallelToolCalls`, `enableThinking`). |
| [Ollama](https://ollama.com/)                       | [OllamaClient](api:prompt-executor-ollama-client::ai.koog.prompt.executor.ollama.client.OllamaClient)                            | ✓                | ✓         | -                    | ✓          | ✓          | -                                               | Local server client with model management APIs.                                                                             |

## Running a prompt

To run a prompt using an LLM client, perform the following:

1. Create an LLM client that handles the connection between your application and LLM providers.
2. Call the `execute()` method with the prompt and LLM as arguments.

Here is an example that uses `OpenAILLMClient` to run prompts:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.params.LLMParams
    import kotlinx.coroutines.runBlocking
    -->

    ```kotlin
    fun main() = runBlocking {
        // Create an OpenAI client
        val apiKey = System.getenv("OPENAI_API_KEY")
        val client = OpenAILLMClient(apiKey)

        // Create a prompt
        val prompt = prompt("prompt_name", LLMParams()) {
            // Add a system message to set the context
            system("You are a helpful assistant.")

            // Add a user message
            user("Tell me about Kotlin")

            // You can also add assistant messages for few-shot examples
            assistant("Kotlin is a modern programming language...")

            // Add another user message
            user("What are its key features?")
        }

        // Run the prompt
        val response = client.execute(prompt, OpenAIModels.Chat.GPT4o)
        // Print the response
        println(response)
    }
    ```
    <!--- KNIT example-llm-clients-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Create an OpenAI client
    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = openAIClient(apiKey);

    // Create a prompt
    Prompt prompt = Prompt.builder("prompt_name")
        // Add a system message to set the context
        .system("You are a helpful assistant.")
        
        // Add a user message
        .user("Tell me about Kotlin")

        // You can also add assistant messages for few-shot examples
        .assistant("Kotlin is a modern programming language...")

        // Add another user message
        .user("What are its key features?")
        .build();

    // Run the prompt
    List<Message.Response> response = client.execute(prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList());
    // Print the response
    System.out.println(response);

    client.close();
    ```
    <!--- KNIT example-llm-clients-java-01.java -->

## Streaming responses

!!! note
    Available for all LLM clients.

When you need to process responses as they are generated, you can use the `executeStreaming()` method in Kotlin or 
`executeStreamingWithPublisher()` in Java to stream the model output.

The streaming API provides different frame types:

- **Delta frames** (`TextDelta`, `ReasoningDelta`, `ToolCallDelta`) — incremental content that arrives in chunks
- **Complete frames** (`TextComplete`, `ReasoningComplete`, `ToolCallComplete`) — full content after all deltas are received
- **End frame** (`End`) — signals stream completion with finish reason

For models that support reasoning (such as Claude Sonnet 4.5 or GPT-o1), reasoning frames will be emitted during 
streaming.
See the [Streaming API documentation](../streaming-api.md) for more details on working with frames.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.streaming.StreamFrame
    import kotlinx.coroutines.runBlocking
    fun main() = runBlocking {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    // Set up the OpenAI client with your API key
    val token = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(token)

    val response = client.executeStreaming(
        prompt = prompt("stream_demo") { user("Stream this response in short chunks.") },
        model = OpenAIModels.Chat.GPT4_1
    )

    response.collect { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> print(frame.text)
            is StreamFrame.ReasoningDelta -> print("[Reasoning] ${frame.text}")
            is StreamFrame.ToolCallComplete -> println("\nTool call: ${frame.name}")
            is StreamFrame.End -> println("\n[done] Reason: ${frame.finishReason}")
            else -> {} // Handle other frame types if needed
        }
    }
    ```
    <!--- KNIT example-llm-clients-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Set up the OpenAI client with your API key
    String token = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = openAIClient(token);

    Prompt prompt = Prompt.builder("stream_demo")
                .user("Stream this response in short chunks.")
                .build();
    
    Publisher<StreamFrame> response = client.executeStreamingWithPublisher(prompt, OpenAIModels.Chat.GPT4_1);

    // Subscribe to the Publisher to consume frames
    response.subscribe(new Subscriber<StreamFrame>() {
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamFrame frame) {
            switch (frame) {
                case StreamFrame.TextDelta delta ->
                        System.out.print(delta.getText());
                case StreamFrame.ReasoningDelta reasoning ->
                        System.out.print("[Reasoning] " + reasoning.getText());
                case StreamFrame.ToolCallComplete toolCall ->
                        System.out.println("\nTool call: " + toolCall.getName());
                case StreamFrame.End end ->
                        System.out.println("\n[done] Reason: " + end.getFinishReason());
                default -> {} // Handle other frame types
            }
        }

        @Override
        public void onError(Throwable t) {
            t.printStackTrace();
        }

        @Override
        public void onComplete() { }
    });
    ```
    <!--- KNIT example-llm-clients-java-02.java -->

## Multiple choices

!!! note
    Available for all LLM clients except `GoogleLLMClient`, `BedrockLLMClient`, and `OllamaClient`

You can request multiple alternative responses from the model in a single call by using the `executeMultipleChoices()` method.
It requires additionally specifying the [`numberOfChoices`](prompt-creation/index.md#prompt-parameters) LLM parameter in the prompt
being executed.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.message.MessagePart
    import ai.koog.prompt.params.LLMParams
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val client = OpenAILLMClient(apiKey)

        val choices = client.executeMultipleChoices(
            prompt = prompt("n_best", params = LLMParams(numberOfChoices = 3)) {
                system("You are a creative assistant.")
                user("Give me three different opening lines for a story.")
            },
            model = OpenAIModels.Chat.GPT4o
        )

        choices.forEachIndexed { i, choice ->
            val text = choice.parts.filterIsInstance<MessagePart.Text>().joinToString(" ") { it.text }
            println("Line #${i + 1}: $text")
        }
    }
    ```
    <!--- KNIT example-llm-clients-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = openAIClient(apiKey);

    // Configure parameters (LLMParams constructor requires all 8 arguments in Java)
    LLMParams params = new LLMParams(
        null, // temperature
        null, // maxTokens
        3,    // numberOfChoices
        null, // speculation
        null, // schema
        null, // toolChoice
        null, // user
        null  // additionalProperties
    );

    Prompt prompt = Prompt.builder("n_best")
        .system("You are a creative assistant.")
        .user("Give me three different opening lines for a story.")
        .build()
        .withParams(params);

    // LLMChoice is a type alias for List<Message.Response>
    List<List<Message.Response>> choices = client.executeMultipleChoices(
        prompt, 
        OpenAIModels.Chat.GPT4o
    );

    for (int i = 0; i < choices.size(); i++) {
        List<Message.Response> choice = choices.get(i);
        StringBuilder text = new StringBuilder();
        for (Message.Response msg : choice) {
            text.append(msg.getContent()).append(" ");
        }
        System.out.println("Line #" + (i + 1) + ": " + text.toString().trim());
    }
    ```
    <!--- KNIT example-llm-clients-java-03.java -->

## Listing available models

!!! note
    Available for all LLM clients except `AnthropicLLMClient`, `BedrockLLMClient`, and `OllamaClient`.

To get a list of available model IDs supported by the LLM client, use the `models()` method:    

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.llm.LLModel
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val client = OpenAILLMClient(apiKey)

        val models: List<LLModel> = client.models()
        models.forEach { println(it.id) }
    }
    ```
    <!--- KNIT example-llm-clients-04.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = openAIClient(apiKey);

    List<LLModel> models = client.models();
    for (LLModel model : models) {
        System.out.println(model.getId());
    }
    ```
    <!--- KNIT example-llm-clients-java-04.java -->

## Embeddings

!!! note
    Available for `OpenAILLMClient`, `GoogleLLMClient`, `BedrockLLMClient`, `MistralAILLMClient`, and `OllamaClient`.

You convert text into embedding vectors using the `embed()` method.
Choose an embedding model and pass your text to this method:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(apiKey)

    val embedding = client.embed(
        text = "This is a sample text for embedding",
        model = OpenAIModels.Embeddings.TextEmbedding3Large
    )

    println("Embedding size: ${embedding.size}")
}
```
<!--- KNIT example-llm-clients-05.kt -->

## Moderation

!!! note
    Available for the following LLM clients: `OpenAILLMClient`, `BedrockLLMClient`, `MistralAILLMClient`, `OllamaClient`.

You can use the `moderate()` method with a moderation model to check whether a prompt contains inappropriate content:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val client = OpenAILLMClient(apiKey)

        val result = client.moderate(
            prompt = prompt("moderation") {
                user("This is a test message that may contain offensive content.")
            },
            model = OpenAIModels.Moderation.Omni
        )

        println(result)
    }
    ```
    <!--- KNIT example-llm-clients-06.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = openAIClient(apiKey);

    Prompt prompt = Prompt.builder("moderation")
        .user("This is a test message that may contain offensive content.")
        .build();

    ModerationResult result = client.moderate(prompt, OpenAIModels.Moderation.Omni);
    System.out.println(result);
    ```
    <!--- KNIT example-llm-clients-java-05.java -->

## Integration with prompt executors

[Prompt executors](prompt-executors.md) wrap LLM clients and provide additional functionality, such as routing, fallbacks, and unified usage across providers.
They are recommended for production use, as they offer flexibility when working with multiple providers.

[^1]: Supports moderation via the OpenAI Moderation API.
[^2]: Moderation requires Guardrails configuration.
[^3]: Supports moderation via the Mistral `v1/moderations` endpoint.
