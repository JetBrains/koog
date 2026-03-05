import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleMistralAIExecutor;

public class ExampleQuickstartMistral {
    public static void main(String[] args) {
        // Get the Mistral AI API key from the MISTRAL_API_KEY environment variable
        String apiKey = System.getenv("MISTRAL_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleMistralAIExecutor(apiKey))
            .llmModel(MistralAIModels.Chat.MistralMedium31)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
