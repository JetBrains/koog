import ai.koog.agents.core.agent.AIAgent;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleAnthropicExecutor;

public class ExampleQuickstartAnthropic {
    public static void main(String[] args) {
        // Get the Anthropic API key from the ANTHROPIC_API_KEY environment variable
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("The API key is not set.");
        }

        // Create an agent
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleAnthropicExecutor(apiKey))
            .llmModel(AnthropicModels.Opus_4_1)
            .build();

        // Run the agent
        String result = agent.run("Hello! How can you help me?");
        System.out.println(result);
    }
}
