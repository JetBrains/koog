package ai.koog.agents.example.websearch;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import me.kpavlov.finchly.TestEnvironment;

/**
 * A simple web search agent that can access the web to help with research tasks.
 * Requires OPENAI_API_KEY and BRIGHT_DATA_KEY environment variables.
 */
public class WebSearchAgent {

    public static void main(String[] args) {
//        String openAIApiKey = System.getenv("OPENAI_API_KEY");
        String openAIApiKey = TestEnvironment.INSTANCE.get("OPENAI_API_KEY");

        if (openAIApiKey == null || openAIApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

//        String brightDataKey = System.getenv("BRIGHT_DATA_KEY");
        String brightDataKey = TestEnvironment.INSTANCE.get("OPENAI_API_KEY");

        if (brightDataKey == null || brightDataKey.isBlank()) {
            throw new IllegalStateException("BRIGHT_DATA_KEY environment variable is not set");
        }

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(new MultiLLMPromptExecutor(new OpenAILLMClient(openAIApiKey)))
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant that helps users research information on the web.")
                .maxIterations(50)
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(new WebSearchTools(brightDataKey))
                                .build()
                )
                .build();

        String result = agent.run("Tell me in details about the Koog framework.");
        System.out.println(result);
    }
}
