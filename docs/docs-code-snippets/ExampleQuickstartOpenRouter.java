import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenRouterExecutor;

public class ExampleQuickstartOpenRouter {
    public static void main(String[] args) {
        // Get the OpenRouter API key from the OPENROUTER_API_KEY environment variable
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOpenRouterExecutor(apiKey))
            .llmModel(OpenRouterModels.GPT4o)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
