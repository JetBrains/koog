import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.bedrock.BedrockModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleBedrockExecutorWithBearerToken;

public class ExampleQuickstartBedrock {
    public static void main(String[] args) {
        // Get the Bedrock API key from the BEDROCK_API_KEY environment variable
        String apiKey = System.getenv("BEDROCK_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleBedrockExecutorWithBearerToken(apiKey))
            .llmModel(BedrockModels.AnthropicClaude4_5Sonnet)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
