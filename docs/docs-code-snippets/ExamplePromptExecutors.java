import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.google.GoogleLLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import ai.koog.prompt.executor.model.JavaPromptExecutorKt;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.message.Message;

import java.util.List;
import java.util.Map;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExamplePromptExecutors {
    public static void main(String[] args) throws Exception {
        OpenAILLMClient openAIClient = new OpenAILLMClient(System.getenv("OPENAI_KEY"));
        OllamaClient ollamaClient = new OllamaClient("http://localhost:11434");

        MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(openAIClient);
        MultiLLMPromptExecutor promptExecutor1 = new MultiLLMPromptExecutor(openAIClient, ollamaClient);

        // Use SimplePromptExecutorsKt to access the static helper method
        PromptExecutor openAIExecutor = simpleOpenAIExecutor("OPENAI_API_KEY");

        // Create a prompt
        Prompt prompt = Prompt.builder("demo")
            .user("Summarize this.")
            .build();

        // Execute a prompt
        // Note: execute() is a suspend function in Kotlin.
        // In a Java-first environment, use a blocking wrapper or an asynchronous approach.
        /*
        List<Message.Response> response = openAIExecutor.execute(
            prompt,
            OpenAIModels.Chat.GPT4o,
            Collections.emptyList() // tools
        );
        */

        /* */
        // Create LLM clients for Anthropic, and Google providers
        AnthropicLLMClient anthropicClient = new AnthropicLLMClient("ANTHROPIC_API_KEY");
        GoogleLLMClient googleClient = new GoogleLLMClient("GOOGLE_API_KEY");

        // Create a MultiLLMPromptExecutor that maps LLM providers to LLM clients
        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(
            Map.of(
                LLMProvider.OpenAI.INSTANCE, openAIClient,
                LLMProvider.Anthropic.INSTANCE, anthropicClient,
                LLMProvider.Google.INSTANCE, googleClient
            )
        );

        // Create a prompt
        Prompt p = Prompt.builder("demo")
            .user("Summarize this.")
            .build();

        // Run the prompt with an OpenAI model
        // Note: execute() is a suspend function in Kotlin.
        // In a Java-first environment, use a blocking wrapper or an asynchronous approach.
        /*
        List<Message.Response> openAIResult = executor.execute(p, OpenAIModels.Chat.GPT4o, Collections.emptyList());

        // Run the prompt with an Anthropic model
        List<Message.Response> anthropicResult = executor.execute(p, AnthropicModels.Sonnet_3_5, Collections.emptyList());
        */

        /* Configuring fallbacks */
        MultiLLMPromptExecutor multiExecutor = new MultiLLMPromptExecutor(
            Map.of(
                LLMProvider.OpenAI, openAIClient,
                LLMProvider.Ollama, ollamaClient
            ),
            new MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
                LLMProvider.Ollama,
                OllamaModels.Meta.LLAMA_3_2
            )
        );

        // Create a prompt
        Prompt p1 = Prompt.builder("demo")
            .user("Summarize this")
            .build();

        // If you pass a Google model, the prompt executor will use the fallback model, as the Google client is not included
        // Note: execute() is a suspend function in Kotlin.
        // In a Java-first environment, use a blocking wrapper or an asynchronous approach.
        /*
        List<Message.Response> response = multiExecutor.execute(p1, GoogleModels.Gemini2_5Pro, Collections.emptyList());
        */
    }
}
