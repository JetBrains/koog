import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.google.GoogleModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleGoogleAIExecutor;

public class ExampleQuickstartGoogle {
    public static void main(String[] args) {
        // Get the Gemini API key from the GOOGLE_API_KEY environment variable
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleGoogleAIExecutor(apiKey))
            .llmModel(GoogleModels.Gemini2_5Pro)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
