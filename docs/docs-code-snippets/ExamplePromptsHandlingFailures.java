import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.clients.retry.RetryConfig;
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExamplePromptsHandlingFailures {
    public static void main(String[] args) {
        /* Basic usage */
        // Wrap any client with the retry capability
        String apiKey = System.getenv("OPENAI_API_KEY");
        Prompt prompt = Prompt.builder("test")
                .user("Hello")
                .build();
        OpenAILLMClient client = new OpenAILLMClient(apiKey);
        RetryingLLMClient resilientClient = new RetryingLLMClient(client);

        // Now all operations will automatically retry on transient errors
        List<Message.Response> response = resilientClient.execute(prompt, OpenAIModels.Chat.GPT4o);

        /* Configuring retry behavior */
        RetryingLLMClient conservativeClient = new RetryingLLMClient(
            client,
            RetryConfig.Companion.getCONSERVATIVE() // closest Java-accessible alternative
        );
        RetryingLLMClient customClient = new RetryingLLMClient(
            client,
            RetryConfig.Companion.getPRODUCTION() // closest Java-accessible alternative
        );

        /* Custom patterns */
        /*
        // Prepare custom patterns
        List<RetryablePattern> patterns = List.of(
                new RetryablePattern.Status(429),
                new RetryablePattern.Keyword("quota"),
                new RetryablePattern.Regex(new kotlin.text.Regex("ERR_\\\
d+")),
                new RetryablePattern.Custom(new Function1<String, Boolean>() {
                    @Override public Boolean invoke(String error) {
                        return error.contains("temporary") && error.length() > 20;
                    }
                })
        );
        // FAILED: Constructing RetryConfig with custom patterns from Java is not supported
        // because RetryConfig does not expose Java-friendly constructors and relies on
        // Kotlin default parameters. There is no builder or @JvmOverloads constructor.
        // Use predefined RetryConfig via RetryConfig.Companion (e.g., getPRODUCTION()) instead.
        // RetryConfig config = new RetryConfig();
         */
        /*
        // Start from defaults and append your own
        List<RetryablePattern> defaults = RetryConfig.Companion.getDEFAULT_PATTERNS();
        List<RetryablePattern> augmented = new ArrayList<>(defaults);
        augmented.add(new RetryablePattern.Keyword("custom_error"));

        // FAILED: As above, Java cannot construct a new RetryConfig instance with a custom
        // list due to Kotlin-only constructor defaults and lack of a Java builder.
        // RetryConfig config = new RetryConfig();
        */

        /* Streaming with retry */
        // FAILED: Streaming with retry from Java is not directly supported:
        // 1) Creating a custom RetryConfig in Java is not available (no Java builder/overloads).
        // 2) executeStreaming(...) returns a Kotlin Flow<StreamFrame>, and Koog does not
        //    currently expose a Java-friendly streaming wrapper.
        // As a workaround, prefer non-streaming execution via JavaPromptExecutor.
        // OpenAILLMClient baseClient = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"));
        // Prompt prompt = Prompt.builder("test").user("Generate a story").build();
        // RetryingLLMClient client = new RetryingLLMClient(baseClient, RetryConfig.Companion.getPRODUCTION());
        // Flow<StreamFrame> stream = client.executeStreaming(prompt, OpenAIModels.Chat.GPT4o); // not Java-friendly

        /* Retry with prompt executors */
        // Single provider executor with retry (Java)
        RetryingLLMClient resilientClient2 = new RetryingLLMClient(
            new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
            RetryConfig.Companion.getPRODUCTION()
        );
        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(resilientClient2);

        // Multi-provider executor with flexible client configuration (Java)
        LLMClient openai = new RetryingLLMClient(
            new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
            RetryConfig.Companion.getCONSERVATIVE()
        );
        LLMClient anthropic = new RetryingLLMClient(
            new AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
            RetryConfig.Companion.getAGGRESSIVE()
        );
        // FAILED: Creating BedrockLLMClient from Java with StaticCredentialsProvider (AWS Kotlin SDK)
        // is not Java-first and requires Kotlin-specific identity providers; no simple Java overloads.
        // LLMClient bedrock = new BedrockLLMClient();

        Map<LLMProvider, LLMClient> clients = Map.of(
                LLMProvider.OpenAI, openai,
                LLMProvider.Anthropic, anthropic
                // , LLMProvider.Bedrock, bedrock
        );
        MultiLLMPromptExecutor multiExecutor = new MultiLLMPromptExecutor(clients);

        /* Timeout configuration */
        String apiKey1 = System.getenv("OPENAI_API_KEY");
        ConnectionTimeoutConfig timeouts = new ConnectionTimeoutConfig(
                5000L,   // connectTimeoutMillis
                60000L,  // requestTimeoutMillis
                120000L  // socketTimeoutMillis
        );
        OpenAIClientSettings settings = new OpenAIClientSettings(
                "https://api.openai.com", // baseUrl
                timeouts,
                "v1/chat/completions",    // chatCompletionsPath
                "v1/responses",           // responsesAPIPath
                "v1/embeddings",          // embeddingsPath
                "v1/moderations",         // moderationsPath
                "v1/models"               // modelsPath
        );
        OpenAILLMClient client1 = new OpenAILLMClient(apiKey, settings);

        /* Error handling */
        Logger logger = LoggerFactory.getLogger("Example");
        RetryingLLMClient resilientClient3 = new RetryingLLMClient(
            new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
            RetryConfig.Companion.getPRODUCTION()
        );
        Prompt prompt2 = Prompt.builder("test")
            .user("Hello")
            .build();
        MultiLLMPromptExecutor exec = new MultiLLMPromptExecutor(resilientClient3);

        // Example helpers (stubs)
        java.util.function.Consumer<List<Message.Response>> processResponse = (resp) -> { /* implementation */ };
        Runnable scheduleRetryLater = () -> { /* implementation */ };
        Runnable notifyAdministrator = () -> { /* implementation */ };
        Runnable useDefaultResponse = () -> { /* implementation */ };

        try {
            List<Message.Response> response2 = exec.execute(prompt2, OpenAIModels.Chat.GPT4o);
            processResponse.accept(response2);
        } catch (Exception e) {
            logger.error("LLM operation failed", e);
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("rate limit")) {
                scheduleRetryLater.run();
            } else if (msg.contains("invalid api key")) {
                notifyAdministrator.run();
            } else {
                useDefaultResponse.run();
            }
        }

    }
}
