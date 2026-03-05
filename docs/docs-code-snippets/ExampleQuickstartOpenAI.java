import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleQuickstartOpenAI {
    public static void main(String[] args) {
        // Get the OpenAI API key from the OPENAI_API_KEY environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOpenAIExecutor(apiKey))
            .llmModel(OpenAIModels.Chat.GPT4o)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
