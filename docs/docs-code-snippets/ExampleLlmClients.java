import ai.koog.prompt.dsl.ModerationResult;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.params.LLMParams;
import ai.koog.prompt.streaming.StreamFrame;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;

public class ExampleLlmClients {
    public static void main(String[] args) throws Exception {

        /* Running a prompt */
        // Create an OpenAI client
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client = new OpenAILLMClient(apiKey);

        // Create a prompt
        Prompt prompt = Prompt.builder("prompt_name")
            .system("You are a helpful assistant.")
            .user("Tell me about Kotlin")
            .assistant("Kotlin is a modern programming language...")
            .user("What are its key features?")
            .build();

        // Run the prompt
        List<Message.Response> response = client.execute(prompt, OpenAIModels.Chat.GPT4o);
        // Print the response
        System.out.println(response);


        /* Streaming responses */
        // Set up the OpenAI client with your API key
        String apiKey1 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client1 = new OpenAILLMClient(apiKey1);

        Prompt prompt1 = Prompt.builder("stream_demo")
                .user("Stream this response in short chunks.")
                .build();

        Publisher<StreamFrame> response1 = client1.executeStreamingWithPublisher(prompt1, OpenAIModels.Chat.GPT4_1);

        // Subscribe to the Publisher to consume frames
        response1.subscribe(new Subscriber<StreamFrame>() {
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

        // LLMChoice is a type alias for List<Message.Response>
        List<List<Message.Response>> choices = client2.executeMultipleChoices(
                prompt2,
                OpenAIModels.Chat.GPT4o
        );

        // Display the choices
        for (int i = 0; i < choices.size(); i++) {
            List<Message.Response> choice = choices.get(i);
            StringBuilder text = new StringBuilder();
            for (Message.Response msg : choice) {
                text.append(msg.getContent()).append(" ");
            }
            System.out.println("Line #" + (i + 1) + ": " + text.toString().trim());
        }


        /* Listing available models */
        String apiKey3 = System.getenv("OPENAI_API_KEY");
        OpenAILLMClient client3 = new OpenAILLMClient(apiKey3);

        List<LLModel> models = client3.models();
        for (LLModel model : models) {
            System.out.println(model.getId());
        }

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

        ModerationResult result = client5.moderate(prompt5, OllamaModels.Meta.LLAMA_GUARD_3);
        System.out.println(result);

    }
}
