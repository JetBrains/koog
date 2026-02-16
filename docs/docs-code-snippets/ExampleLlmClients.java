import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.Executors;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.params.LLMParams;
import ai.koog.prompt.streaming.StreamFrame;
import kotlinx.coroutines.flow.Flow;

import java.util.Collections;
import java.util.List;

public class ExampleLlmClients {
    public static void main(String[] args) throws Exception {

        /* Running a prompt */
        // Create an OpenAI client
        String token = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client = new OpenAILLMClient(token);

        // Create a prompt
        Prompt prompt = Prompt.builder("prompt_name")
            .system("You are a helpful assistant.")
            .user("Tell me about Kotlin")
            .assistant("Kotlin is a modern programming language...")
            .user("What are its key features?")
            .build();

        // FAILED: Run the prompt (suspend function call from Java requires a Continuation)
        // List<Message.Response> response = client.execute(prompt, OpenAIModels.Chat.GPT4o, Collections.emptyList(), continuation);
        // System.out.println(response);

        /* Streaming responses */
        // Set up the OpenAI client with your API key
        String apiKey1 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client1 = new OpenAILLMClient(apiKey1);

        Prompt prompt1 = Prompt.builder("stream_demo")
            .user("Stream this response in short chunks.")
            .build();

        // FAILED: executeStreaming returns a Kotlin Flow
        // Flow<StreamFrame> response = client.executeStreaming(
        //    prompt1,
        //    OpenAIModels.Chat.GPT4_1,
        //    Collections.emptyList()
        // );
        // FAILED: Consuming Kotlin Flow in Java usually requires FlowAdapters or runBlocking

        /* Multiple choises */
        String apiKey2 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client2 = new OpenAILLMClient(apiKey2);

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

        Prompt prompt2 = Prompt.builder("n_best")
            .system("You are a creative assistant.")
            .user("Give me three different opening lines for a story.")
            .build()
            .withParams(params);

        // FAILED: executeMultipleChoices is a suspend function; call with Continuation from Java
        // List<LLMChoice> choices = client.executeMultipleChoices(prompt2, OpenAIModels.Chat.GPT4o, Collections.emptyList(), continuation);

        /* Listing available models */
        String apiKey3 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client3 = new OpenAILLMClient(apiKey3);

        // FAILED: models() is a suspend function; call with Continuation from Java
        // List<LLModel> models = client3.models(continuation);

        /* Embeddings */
        String apiKey4 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client4 = new OpenAILLMClient(apiKey4);

        // FAILED: embed() is a suspend function; call with Continuation from Java
        // List<Double> embedding = client4.embed(
        //     "This is a sample text for embedding",
        //     OpenAIModels.Embeddings.TextEmbedding3Large,
        //     continuation
        // );

        /* Moderation */
        String apiKey5 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client5 = new OpenAILLMClient(apiKey5);

        Prompt prompt5 = Prompt.builder("moderation")
            .user("This is a test message that may contain offensive content.")
            .build();

        // FAILED: moderate() is a suspend function; call with Continuation from Java
        // ModerationResult result = client5.moderate(prompt5, OpenAIModels.Moderation.Omni, continuation);
    }
}
